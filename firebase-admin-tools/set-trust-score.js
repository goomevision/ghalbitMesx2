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
  const score = Number(args.score || args._[1] || requireArg(args, "score"));
  const note = String(args.note || args._.slice(2).join(" ") || "").trim();

  if (!Number.isFinite(score) || score < 0 || score > 100) {
    throw new Error("Nilai trust harus angka 0 sampai 100.");
  }

  const db = getFirestore();
  await db.collection("nodeRegistry").doc(globalId).set(
    {
      globalId,
      trustScore: Math.round(score),
      trustNote: note,
      trustUpdatedAt: serverTimestamp(),
      trustControlledBy: "SYSTEM",
      source: "firebase-admin-tools",
    },
    { merge: true }
  );

  await db.collection("trustReports").add({
    globalId,
    score: Math.round(score),
    note,
    type: "MANUAL_TRUST_UPDATE",
    createdAt: serverTimestamp(),
    controlledBy: "SYSTEM",
    source: "firebase-admin-tools",
  });

  console.log(`Service account: ${getServiceAccountPath()}`);
  console.log(`Trust score diperbarui untuk ${globalId}: ${Math.round(score)}`);
  if (note) console.log(`Catatan: ${note}`);
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error("Gagal memperbarui trust score:", error.message || error);
    process.exit(1);
  });
