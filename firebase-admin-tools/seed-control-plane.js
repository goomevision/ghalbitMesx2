const { getFirestore, defaultPolicies, serverTimestamp } = require("./common");

const db = getFirestore();

async function main() {
  const { bridgePolicy, economyPolicy } = defaultPolicies();

  console.log("Menulis bridgePolicies/default ...");
  await db.collection("bridgePolicies").doc("default").set(bridgePolicy, { merge: true });

  console.log("Menulis economyPolicies/default ...");
  await db.collection("economyPolicies").doc("default").set(economyPolicy, { merge: true });

  console.log("Menulis bootstrapConfig/default ...");
  await db.collection("bootstrapConfig").doc("default").set(
    {
      status: "ACTIVE",
      recommendedPeerLimit: 12,
      recommendedGatewayLimit: 6,
      trustThreshold: 40,
      allowLocalMeshWithoutServer: true,
      allowInternetBridgeByPolicy: true,
      notes: "Server koordinator, bukan jalur utama internet.",
      updatedAt: serverTimestamp(),
      controlledBy: "SYSTEM",
      source: "firebase-admin-tools",
    },
    { merge: true }
  );

  console.log("Menulis trustConfig/default ...");
  await db.collection("trustConfig").doc("default").set(
    {
      baseScore: 50,
      onlineBonus: 5,
      providerReadyBonus: 10,
      providerActiveBonus: 15,
      blockedPenalty: 40,
      abusePenalty: 25,
      spamPenalty: 20,
      minimumHealthyScore: 40,
      updatedAt: serverTimestamp(),
      controlledBy: "SYSTEM",
      source: "firebase-admin-tools",
    },
    { merge: true }
  );

  console.log("Selesai. Fondasi control plane berhasil ditulis.");
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error("Gagal menulis fondasi control plane:", error.message || error);
    process.exit(1);
  });
