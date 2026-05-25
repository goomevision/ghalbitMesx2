const {
  getFirestore,
  getServiceAccountPath,
  parseArgs,
  normalizeGlobalId,
  requireArg,
} = require("./common");

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const globalId = normalizeGlobalId(args.globalId || args._[0] || requireArg(args, "globalId"));
  const db = getFirestore();

  const [walletSnap, txSnap] = await Promise.all([
    db.collection("wallets").doc(globalId).get(),
    db.collection("walletTransactions").where("walletGlobalId", "==", globalId).get(),
  ]);

  console.log(`Service account: ${getServiceAccountPath()}`);
  console.log(`Wallet: ${globalId}`);

  if (!walletSnap.exists) {
    console.log("Status: belum ada data wallet server.");
    return;
  }

  const wallet = walletSnap.data() || {};
  console.log(`Balance: ${Number(wallet.balance || 0).toFixed(2)} GHBT`);
  console.log(`Owner class: ${wallet.ownerClass || "USER"}`);
  console.log(`Last reason: ${wallet.lastReason || "-"}`);
  console.log(`Last direction: ${wallet.lastDirection || "-"}`);
  console.log(`Last amount: ${Number(wallet.lastAmount || 0).toFixed(2)} GHBT`);
  console.log(`Last controller: ${wallet.lastController || wallet.lastOperator || "-"}`);
  console.log("Recent transactions:");

  if (txSnap.empty) {
    console.log("- belum ada transaksi.");
    return;
  }

  const recent = txSnap.docs
    .map((doc) => ({ id: doc.id, ...doc.data() }))
    .sort((a, b) => {
      const aMs = a.createdAt && typeof a.createdAt.toMillis === "function" ? a.createdAt.toMillis() : 0;
      const bMs = b.createdAt && typeof b.createdAt.toMillis === "function" ? b.createdAt.toMillis() : 0;
      return bMs - aMs;
    })
    .slice(0, 10);

  recent.forEach((item) => {
    const amount = Number(item.amount || 0).toFixed(2);
    console.log(
      `- ${item.direction || "?"} ${amount} GHBT | ${item.reason || "-"} | ${item.controller || item.operator || "-"}`
    );
  });
}

main().catch((error) => {
  console.error("Gagal membaca wallet:", error.message || error);
  process.exitCode = 1;
});
