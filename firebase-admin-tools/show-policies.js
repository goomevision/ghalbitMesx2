const { getFirestore } = require("./common");

async function main() {
  const firestore = getFirestore();

  const [bridgeDoc, economyDoc] = await Promise.all([
    firestore.collection("bridgePolicies").doc("default").get(),
    firestore.collection("economyPolicies").doc("default").get(),
  ]);

  console.log("=== bridgePolicies/default ===");
  console.log(
    JSON.stringify(bridgeDoc.exists ? bridgeDoc.data() : { missing: true }, null, 2)
  );

  console.log("\n=== economyPolicies/default ===");
  console.log(
    JSON.stringify(economyDoc.exists ? economyDoc.data() : { missing: true }, null, 2)
  );
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error("Gagal menampilkan policy Firebase:", error);
    process.exit(1);
  });
