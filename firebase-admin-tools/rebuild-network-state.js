const { getAdminApp, getFirestore, serverTimestamp } = require("./common");

const adminApp = getAdminApp();
const db = getFirestore();
const rtdb = adminApp.database();

function asList(value) {
  return Array.isArray(value) ? value.filter(Boolean) : [];
}

function clampTrust(value) {
  return Math.max(0, Math.min(100, Math.round(value)));
}

async function main() {
  const [
    presenceSnapshot,
    providerProfilesSnapshot,
    walletsSnapshot,
    peerPoliciesSnapshot,
    bridgePolicyDoc,
    economyPolicyDoc,
    existingRegistrySnapshot,
  ] = await Promise.all([
    rtdb.ref("presence").get(),
    db.collection("providerProfiles").get(),
    db.collection("wallets").get(),
    db.collection("peerPolicies").get(),
    db.collection("bridgePolicies").doc("default").get(),
    db.collection("economyPolicies").doc("default").get(),
    db.collection("nodeRegistry").get(),
  ]);

  const presenceMap = new Map();
  if (presenceSnapshot.exists()) {
    const raw = presenceSnapshot.val() || {};
    Object.entries(raw).forEach(([globalId, value]) => {
      presenceMap.set(globalId, value || {});
    });
  }

  const providerMap = new Map();
  providerProfilesSnapshot.forEach((doc) => providerMap.set(doc.id, doc.data() || {}));

  const walletMap = new Map();
  walletsSnapshot.forEach((doc) => walletMap.set(doc.id, doc.data() || {}));

  const peerPolicyMap = new Map();
  peerPoliciesSnapshot.forEach((doc) => peerPolicyMap.set(doc.id, doc.data() || {}));

  const existingRegistryMap = new Map();
  existingRegistrySnapshot.forEach((doc) => existingRegistryMap.set(doc.id, doc.data() || {}));

  const allIds = new Set([
    ...presenceMap.keys(),
    ...providerMap.keys(),
    ...walletMap.keys(),
    ...peerPolicyMap.keys(),
    ...existingRegistryMap.keys(),
  ]);

  const trustConfig = {
    baseScore: 50,
    onlineBonus: 5,
    providerReadyBonus: 10,
    providerActiveBonus: 15,
    blockedPenalty: 40,
  };

  const batch = db.batch();
  const registryEntries = [];
  const gatewayEntries = [];

  for (const globalId of allIds) {
    const presence = presenceMap.get(globalId) || {};
    const provider = providerMap.get(globalId) || {};
    const wallet = walletMap.get(globalId) || {};
    const peerPolicy = peerPolicyMap.get(globalId) || {};
    const previous = existingRegistryMap.get(globalId) || {};

    const online = Boolean(presence.online);
    const contributionApproved = Boolean(
      provider.contributionApproved ?? presence.contributionApproved ?? false
    );
    const onboardingCompleted = Boolean(
      provider.onboardingCompleted ?? presence.onboardingCompleted ?? false
    );
    const participantRoles = asList(provider.participantRoles || presence.participantRoles || []);
    const providerReady = Boolean(provider.providerReady ?? presence.providerReady ?? false);
    const providerActive = Boolean(provider.providerActive ?? presence.providerActive ?? false);
    const hotspotActive = Boolean(provider.hotspotActive ?? presence.hotspotActive ?? false);
    const walletBalance = Number(wallet.balance || 0);
    const ownerClass = String(wallet.ownerClass || "USER").toUpperCase();
    const peerTier = String(peerPolicy.tier || "STANDARD").toUpperCase();

    let trustScore =
      typeof previous.trustScore === "number" ? previous.trustScore : trustConfig.baseScore;
    if (typeof previous.trustScore !== "number") {
      trustScore =
        trustConfig.baseScore +
        (online ? trustConfig.onlineBonus : 0) +
        (providerReady ? trustConfig.providerReadyBonus : 0) +
        (providerActive ? trustConfig.providerActiveBonus : 0) -
        (peerTier === "BLOCKED" ? trustConfig.blockedPenalty : 0);
    }
    trustScore = clampTrust(trustScore);

    const status =
      providerActive
        ? "ACTIVE_PROVIDER"
        : providerReady
          ? "READY_PROVIDER"
          : online
            ? "ONLINE_PARTICIPANT"
            : "OFFLINE_PARTICIPANT";

    const registryEntry = {
      globalId,
      online,
      contributionApproved,
      onboardingCompleted,
      participantRoles,
      providerReady,
      providerActive,
      hotspotActive,
      walletBalance,
      ownerClass,
      peerTier,
      customDailyQuotaMb:
        typeof peerPolicy.customDailyQuotaMb === "number" ? peerPolicy.customDailyQuotaMb : null,
      trustScore,
      status,
      updatedAt: serverTimestamp(),
      source: "firebase-admin-tools",
    };

    batch.set(db.collection("nodeRegistry").doc(globalId), registryEntry, { merge: true });
    registryEntries.push({ globalId, ...registryEntry });

    if (providerReady || providerActive) {
      const gatewayEntry = {
        globalId,
        online,
        providerReady,
        providerActive,
        hotspotActive,
        trustScore,
        walletBalance,
        participantRoles,
        health:
          providerActive ? "STABIL" : online ? "CADANGAN_SIAP" : "BELUM_SIAP",
        updatedAt: serverTimestamp(),
        source: "firebase-admin-tools",
      };
      batch.set(db.collection("gatewayDirectory").doc(globalId), gatewayEntry, { merge: true });
      gatewayEntries.push({ globalId, ...gatewayEntry });
    }
  }

  const sortedPeers = [...registryEntries].sort((a, b) => b.trustScore - a.trustScore);
  const sortedGateways = [...gatewayEntries].sort((a, b) => {
    if (b.providerActive !== a.providerActive) return Number(b.providerActive) - Number(a.providerActive);
    return b.trustScore - a.trustScore;
  });

  const walletCount = walletsSnapshot.size;
  const totalKnownBalance = [...walletMap.values()].reduce(
    (sum, value) => sum + Number(value.balance || 0),
    0
  );

  batch.set(
    db.collection("bootstrapState").doc("default"),
    {
      activePeerIds: sortedPeers.filter((it) => it.online).slice(0, 25).map((it) => it.globalId),
      recommendedPeerIds: sortedPeers.slice(0, 12).map((it) => it.globalId),
      activeGatewayIds: sortedGateways.filter((it) => it.online).slice(0, 10).map((it) => it.globalId),
      recommendedGatewayIds: sortedGateways.slice(0, 6).map((it) => it.globalId),
      peerCount: registryEntries.length,
      onlinePeerCount: registryEntries.filter((it) => it.online).length,
      gatewayCount: gatewayEntries.length,
      bridgePolicyVersion: bridgePolicyDoc.get("versionLabel") || "UNKNOWN",
      economyPolicyVersion: economyPolicyDoc.get("versionLabel") || "UNKNOWN",
      updatedAt: serverTimestamp(),
      source: "firebase-admin-tools",
    },
    { merge: true }
  );

  batch.set(
    db.collection("blockchainSync").doc("default"),
    {
      walletCount,
      totalKnownBalance,
      updatedAt: serverTimestamp(),
      source: "firebase-admin-tools",
    },
    { merge: true }
  );

  batch.set(
    db.collection("networkState").doc("default"),
    {
      peerCount: registryEntries.length,
      onlinePeerCount: registryEntries.filter((it) => it.online).length,
      approvedParticipantCount: registryEntries.filter((it) => it.contributionApproved).length,
      providerReadyCount: registryEntries.filter((it) => it.providerReady).length,
      providerActiveCount: registryEntries.filter((it) => it.providerActive).length,
      gatewayCount: gatewayEntries.length,
      walletCount,
      totalKnownBalance,
      updatedAt: serverTimestamp(),
      source: "firebase-admin-tools",
    },
    { merge: true }
  );

  await batch.commit();

  console.log("Selesai memperbarui network state.");
  console.log(`Node registry: ${registryEntries.length}`);
  console.log(`Gateway directory: ${gatewayEntries.length}`);
  console.log(`Wallet count: ${walletCount}`);
  console.log(`Total balance terdata: ${totalKnownBalance.toFixed(2)} GHBT`);
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error("Gagal memperbarui network state:", error.message || error);
    process.exit(1);
  });
