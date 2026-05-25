const {
  getFirestore,
  getServiceAccountPath,
  parseArgs,
  normalizeGlobalId,
  requireArg,
  serverTimestamp,
} = require("./common");

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const globalId = normalizeGlobalId(args.globalId || args._[0] || requireArg(args, "globalId"));
  const category = String(args.category || args._[1] || "ABUSE").trim().toUpperCase();
  const detail = String(args.detail || args._.slice(2).join(" ") || "").trim();
  const penalty = Number(args.penalty || 15);

  if (!Number.isFinite(penalty) || penalty <= 0) {
    throw new Error("Penalty harus angka positif.");
  }

  const db = getFirestore();
  const registryRef = db.collection("nodeRegistry").doc(globalId);
  const reportRef = db.collection("abuseReports").doc();

  await db.runTransaction(async (tx) => {
    const snap = await tx.get(registryRef);
    const currentTrust = snap.exists ? Number(snap.get("trustScore") || 50) : 50;
    const nextTrust = Math.max(0, Math.round(currentTrust - penalty));

    tx.set(
      registryRef,
      {
        globalId,
        trustScore: nextTrust,
        lastAbuseCategory: category,
        lastAbuseDetail: detail,
        abuseUpdatedAt: serverTimestamp(),
        source: "firebase-admin-tools",
      },
      { merge: true }
    );

    tx.set(reportRef, {
      globalId,
      category,
      detail,
      penalty,
      trustBefore: currentTrust,
      trustAfter: nextTrust,
      createdAt: serverTimestamp(),
      controlledBy: "SYSTEM",
      source: "firebase-admin-tools",
    });
  });

  console.log(`Service account: ${getServiceAccountPath()}`);
  console.log(`Laporan abuse disimpan untuk ${globalId}`);
  console.log(`Kategori: ${category}`);
  console.log(`Penalty trust: ${penalty}`);
  if (detail) console.log(`Detail: ${detail}`);
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error("Gagal menyimpan laporan abuse:", error.message || error);
    process.exit(1);
  });
