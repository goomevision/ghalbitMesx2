const { getFirestore } = require("./common");

const db = getFirestore();

async function main() {
  const [networkStateDoc, bootstrapDoc, gatewaysSnapshot] = await Promise.all([
    db.collection("networkState").doc("default").get(),
    db.collection("bootstrapState").doc("default").get(),
    db.collection("gatewayDirectory").get(),
  ]);

  console.log("=== networkState/default ===");
  console.log(JSON.stringify(networkStateDoc.data() || {}, null, 2));
  console.log("");
  console.log("=== bootstrapState/default ===");
  console.log(JSON.stringify(bootstrapDoc.data() || {}, null, 2));
  console.log("");
  console.log("=== gatewayDirectory ===");
  const gateways = [];
  gatewaysSnapshot.forEach((doc) => gateways.push({ id: doc.id, ...doc.data() }));
  gateways
    .sort((a, b) => Number(b.providerActive) - Number(a.providerActive) || (b.trustScore || 0) - (a.trustScore || 0))
    .forEach((gateway) => {
      console.log(
        `- ${gateway.id} | health=${gateway.health} | trust=${gateway.trustScore} | online=${gateway.online} | active=${gateway.providerActive}`
      );
    });
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error("Gagal membaca network state:", error.message || error);
    process.exit(1);
  });
