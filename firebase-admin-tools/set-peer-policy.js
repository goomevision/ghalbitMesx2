const {
  getFirestore,
  getServiceAccountPath,
  parseArgs,
  normalizeGlobalId,
  requireArg,
  classifyWalletOwner,
  serverTimestamp,
} = require("./common");

const ALLOWED_TIERS = new Set(["STANDARD", "PRIORITY", "BLOCKED"]);

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const positional = [...(args._ || [])];
  const globalId = normalizeGlobalId(args.globalId || positional.shift() || requireArg(args, "globalId"));
  const rawTier = String(args.tier || positional.shift() || "STANDARD").trim().toUpperCase();
  if (!ALLOWED_TIERS.has(rawTier)) {
    throw new Error("Tier harus STANDARD, PRIORITY, atau BLOCKED.");
  }

  let quotaRaw = args.quotaMb;
  if (quotaRaw === undefined && positional.length > 0) {
    const next = String(positional[0]).trim();
    if (/^\d+(\.\d+)?$/.test(next)) {
      quotaRaw = positional.shift();
    }
  }

  const customDailyQuotaMb =
    quotaRaw !== undefined && String(quotaRaw).trim() !== ""
      ? Math.max(0, Number(quotaRaw))
      : null;

  if (customDailyQuotaMb !== null && !Number.isFinite(customDailyQuotaMb)) {
    throw new Error("Nilai --quotaMb harus angka yang valid.");
  }

  const note = String(args.note || positional.join(" ") || "").trim();
  const db = getFirestore();
  const subjectClass = classifyWalletOwner(globalId);

  await db
    .collection("peerPolicies")
    .doc(globalId)
    .set(
      {
        globalId,
        tier: rawTier,
        customDailyQuotaMb,
        note,
        updatedAt: serverTimestamp(),
        controlledBy: "SYSTEM",
        subjectClass,
        source: "firebase-admin-tools",
      },
      { merge: true }
    );

  console.log(`Service account: ${getServiceAccountPath()}`);
  console.log("Peer policy berhasil disimpan.");
  console.log(`Peer: ${globalId}`);
  console.log(`Tier: ${rawTier}`);
  console.log(
    `Custom quota: ${
      customDailyQuotaMb === null ? "server default" : `${customDailyQuotaMb} MB`
    }`
  );
  console.log(`Subjek: ${subjectClass}`);
  console.log(`Pengendali: SYSTEM`);
  if (note) {
    console.log(`Catatan: ${note}`);
  }
}

main().catch((error) => {
  console.error("Gagal menyimpan peer policy:", error.message || error);
  process.exitCode = 1;
});
