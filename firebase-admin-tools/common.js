const path = require("path");
const admin = require("firebase-admin");

const serviceAccountPath =
  process.env.FIREBASE_SERVICE_ACCOUNT ||
  path.resolve(__dirname, "..", "firebase-admin-key.json");

function getServiceAccountPath() {
  return serviceAccountPath;
}

function getAdminApp() {
  if (admin.apps.length > 0) {
    return admin.app();
  }

  const serviceAccount = require(serviceAccountPath);
  return admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
    databaseURL:
      "https://maritime-link-aceh-default-rtdb.asia-southeast1.firebasedatabase.app",
  });
}

function getFirestore() {
  getAdminApp();
  return admin.firestore();
}

function serverTimestamp() {
  return admin.firestore.FieldValue.serverTimestamp();
}

function parseArgs(argv) {
  const result = {};
  const positional = [];
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (!token.startsWith("--")) {
      positional.push(token);
      continue;
    }
    const key = token.slice(2);
    const next = argv[i + 1];
    if (!next || next.startsWith("--")) {
      result[key] = true;
      continue;
    }
    result[key] = next;
    i += 1;
  }
  result._ = positional;
  return result;
}

function normalizeGlobalId(raw) {
  return String(raw || "").trim().toUpperCase();
}

function requireArg(args, name) {
  const value = args[name];
  if (value === undefined || value === null || String(value).trim() === "") {
    throw new Error(`Argumen --${name} wajib diisi.`);
  }
  return String(value).trim();
}

function parsePositiveAmount(raw, label = "amount") {
  const amount = Number(raw);
  if (!Number.isFinite(amount) || amount <= 0) {
    throw new Error(`Nilai ${label} harus lebih dari 0.`);
  }
  return amount;
}

function classifyWalletOwner(globalId) {
  return normalizeGlobalId(globalId) === "BUILDER_FOUNDATION" ? "BUILDER" : "USER";
}

function defaultPolicies() {
  return {
    bridgePolicy: {
      versionLabel: "BRIDGE-FB-2026.05",
      maxSessionMinutes: 45,
      maxSessionMb: 512,
      standardDailyQuotaMb: 1024,
      priorityDailyQuotaMb: 4096,
      minimumInternetAccessBalance: 5.0,
      allowLocalGateway: true,
      allowRemoteGateway: true,
      priorityUsers: [],
      blockedUsers: [],
    },
    economyPolicy: {
      versionLabel: "GBHT-V1-2026.05",
      sourceLabel: "GBHT Policy v1 - bayar sesuai pemakaian",
      priceReferencePerGbhtIdr: 100,
      localEditingLocked: true,
      appBonusTable: {
        burnPerMb: 0.0,
        gatewayPerMb: 0.026,
        relayPerMb: 0.012,
        treasuryPerMb: 0.003,
        builderPerMb: 0.005,
        validatorPerMb: 0.005,
        builderShareRate: 0.1,
        chatMultiplier: 1.0,
        mediaMultiplier: 1.05,
        callMultiplier: 1.10,
        sosMultiplier: 1.20,
        controlMultiplier: 0.45,
        otherMultiplier: 0.8,
      },
      internetBridgeTable: {
        burnPerMb: 0.09765625,
        gatewayPerMb: 0.0537109375,
        relayPerMb: 0.01953125,
        treasuryPerMb: 0.0048828125,
        builderPerMb: 0.009765625,
        validatorPerMb: 0.009765625,
        builderShareRate: 0.1,
        chatMultiplier: 1.0,
        mediaMultiplier: 1.05,
        callMultiplier: 1.10,
        sosMultiplier: 1.20,
        controlMultiplier: 0.55,
        otherMultiplier: 1.0,
      },
    },
  };
}

module.exports = {
  getAdminApp,
  getFirestore,
  getServiceAccountPath,
  parseArgs,
  normalizeGlobalId,
  requireArg,
  parsePositiveAmount,
  classifyWalletOwner,
  serverTimestamp,
  defaultPolicies,
};
