const { getFirestore, defaultPolicies } = require("./common");

const firestore = getFirestore();
const { bridgePolicy, economyPolicy } = defaultPolicies();

async function main() {
  console.log("Menulis bridgePolicies/default ...");
  await firestore.collection("bridgePolicies").doc("default").set(bridgePolicy);

  console.log("Menulis economyPolicies/default ...");
  await firestore.collection("economyPolicies").doc("default").set(economyPolicy);

  console.log("Selesai. Policy Firebase berhasil ditulis.");
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error("Gagal menulis policy Firebase:", error);
    process.exit(1);
  });
