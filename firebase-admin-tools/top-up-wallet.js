const {
  getFirestore,
  getServiceAccountPath,
  parseArgs,
  normalizeGlobalId,
  requireArg,
  parsePositiveAmount,
  classifyWalletOwner,
  serverTimestamp,
} = require("./common");

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const globalId = normalizeGlobalId(args.globalId || args._[0] || requireArg(args, "globalId"));
  const amount = parsePositiveAmount(args.amount || args._[1] || requireArg(args, "amount"));
  const reason = String(args.reason || args._[2] || "SYSTEM_TOPUP").trim();
  const ownerClass = classifyWalletOwner(globalId);
  const db = getFirestore();
  const walletRef = db.collection("wallets").doc(globalId);
  const txRef = db.collection("walletTransactions").doc();

  await db.runTransaction(async (tx) => {
    const walletSnap = await tx.get(walletRef);
    const currentBalance = walletSnap.exists ? Number(walletSnap.get("balance") || 0) : 0;
    const nextBalance = currentBalance + amount;

    tx.set(
      walletRef,
      {
        globalId,
        balance: nextBalance,
        updatedAt: serverTimestamp(),
        lastReason: reason,
        lastDirection: "CREDIT",
        lastAmount: amount,
        lastController: "SYSTEM",
        ownerClass,
      },
      { merge: true }
    );

    tx.set(txRef, {
      walletGlobalId: globalId,
      amount,
      direction: "CREDIT",
      reason,
      controller: "SYSTEM",
      ownerClass,
      createdAt: serverTimestamp(),
      balanceBefore: currentBalance,
      balanceAfter: nextBalance,
      source: "firebase-admin-tools",
    });
  });

  console.log(`Service account: ${getServiceAccountPath()}`);
  console.log(`Top-up berhasil.`);
  console.log(`Wallet: ${globalId}`);
  console.log(`Amount: ${amount.toFixed(2)} GHBT`);
  console.log(`Reason: ${reason}`);
  console.log(`Kelas dompet: ${ownerClass}`);
  console.log(`Pengendali: SYSTEM`);
}

main().catch((error) => {
  console.error("Gagal top-up wallet:", error.message || error);
  process.exitCode = 1;
});
