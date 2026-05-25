const { getFirestore, serverTimestamp } = require("./common");

const db = getFirestore();

async function main() {
  const [networkStateDoc, bootstrapDoc, gatewaysSnapshot, registrySnapshot] = await Promise.all([
    db.collection("networkState").doc("default").get(),
    db.collection("bootstrapState").doc("default").get(),
    db.collection("gatewayDirectory").get(),
    db.collection("nodeRegistry").get(),
  ]);

  const gateways = [];
  gatewaysSnapshot.forEach((doc) => gateways.push({ id: doc.id, ...doc.data() }));

  const peers = [];
  registrySnapshot.forEach((doc) => peers.push({ id: doc.id, ...doc.data() }));

  const captureRef = db.collection("recoverySnapshots").doc();
  await captureRef.set({
    createdAt: serverTimestamp(),
    networkState: networkStateDoc.data() || {},
    bootstrapState: bootstrapDoc.data() || {},
    topGateways: gateways
      .sort((a, b) => Number(b.providerActive) - Number(a.providerActive) || (b.trustScore || 0) - (a.trustScore || 0))
      .slice(0, 10),
    topPeers: peers.sort((a, b) => (b.trustScore || 0) - (a.trustScore || 0)).slice(0, 20),
    source: "firebase-admin-tools",
  });

  console.log(`Recovery snapshot berhasil dibuat: ${captureRef.id}`);
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error("Gagal membuat recovery snapshot:", error.message || error);
    process.exit(1);
  });
