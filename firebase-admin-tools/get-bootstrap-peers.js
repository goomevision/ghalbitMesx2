const { getFirestore, parseArgs, normalizeGlobalId } = require("./common");

const db = getFirestore();

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const requester = normalizeGlobalId(args.globalId || args._[0] || "UNKNOWN");
  const [bootstrapDoc, registrySnapshot] = await Promise.all([
    db.collection("bootstrapState").doc("default").get(),
    db.collection("nodeRegistry").get(),
  ]);

  const bootstrap = bootstrapDoc.data() || {};
  const registry = new Map();
  registrySnapshot.forEach((doc) => registry.set(doc.id, doc.data() || {}));

  console.log(`Bootstrap untuk: ${requester}`);
  console.log("");
  console.log("Peer rekomendasi:");
  (bootstrap.recommendedPeerIds || []).forEach((globalId) => {
    const peer = registry.get(globalId) || {};
    console.log(`- ${globalId} | trust=${peer.trustScore || 0} | online=${peer.online === true}`);
  });

  console.log("");
  console.log("Gateway rekomendasi:");
  (bootstrap.recommendedGatewayIds || []).forEach((globalId) => {
    const gateway = registry.get(globalId) || {};
    console.log(
      `- ${globalId} | status=${gateway.status || "UNKNOWN"} | trust=${gateway.trustScore || 0} | providerReady=${gateway.providerReady === true}`
    );
  });
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error("Gagal mengambil bootstrap peers:", error.message || error);
    process.exit(1);
  });
