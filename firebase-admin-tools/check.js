const { getFirestore, getServiceAccountPath } = require("./common");

async function main() {
  console.log("Service account:", getServiceAccountPath());
  const firestore = getFirestore();

  const [bridgeDoc, economyDoc] = await Promise.all([
    firestore.collection("bridgePolicies").doc("default").get(),
    firestore.collection("economyPolicies").doc("default").get(),
  ]);

  console.log("Firestore admin access: OK");
  console.log("bridgePolicies/default:", bridgeDoc.exists ? "ADA" : "BELUM ADA");
  console.log("economyPolicies/default:", economyDoc.exists ? "ADA" : "BELUM ADA");

  if (bridgeDoc.exists) {
    console.log("Bridge version:", bridgeDoc.data().versionLabel || "-");
  }
  if (economyDoc.exists) {
    console.log("Economy version:", economyDoc.data().versionLabel || "-");
  }
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error("Check Firebase gagal:", error);
    process.exit(1);
  });
