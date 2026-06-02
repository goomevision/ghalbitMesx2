const express = require("express");
const cors = require("cors");
const admin = require("firebase-admin");
const { onRequest } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();
const app = express();

app.use(cors({ origin: true }));
app.use(express.json({ limit: "1mb" }));

const PRESENCE = "operator_presence";
const INBOX = "operator_inbox";
const RECEIPTS = "operator_receipts";
const SESSIONS = "operator_sessions";

function nowMs() {
  return Date.now();
}

function normalizeId(value) {
  return String(value || "").trim().toUpperCase();
}

function normalizeEventType(payload) {
  return String(
    payload?.signalType ||
      payload?.type ||
      payload?.contentType ||
      payload?.messageType ||
      "UNKNOWN"
  ).trim();
}

function ensureEventId(payload) {
  return String(
    payload?.eventId ||
      payload?.messageId ||
      payload?.packetId ||
      `${normalizeEventType(payload)}-${nowMs()}`
  ).trim();
}

function splitInbox(items) {
  const messages = [];
  const receipts = [];
  const callSignals = [];
  items.forEach((item) => {
    const type = normalizeEventType(item);
    if (type.startsWith("CALL_")) {
      callSignals.push(item);
    } else if (type.includes("ACK") || type.includes("RECEIPT") || type.includes("READ")) {
      receipts.push(item);
    } else {
      messages.push(item);
    }
  });
  return { messages, receipts, callSignals };
}

async function appendInbox(globalId, event) {
  const id = normalizeId(globalId);
  const ref = db.collection(INBOX).doc(id);
  const snapshot = await ref.get();
  const current = snapshot.exists ? snapshot.data().items || [] : [];
  const deduped = current.filter((item) => item.eventId !== event.eventId);
  deduped.push(event);
  await ref.set(
    {
      globalId: id,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      items: deduped
    },
    { merge: true }
  );
}

app.get("/health", async (_req, res) => {
  res.json({
    ok: true,
    service: "ghalbit-firebase-operator-server",
    status: "READY",
    timestamp: nowMs()
  });
});

app.post("/presence/heartbeat", async (req, res) => {
  const payload = req.body || {};
  const globalId = normalizeId(payload.globalId);
  if (!globalId) {
    logger.warn("GHALBIT-SERVER-PRESENCE HEARTBEAT_FAIL missing_globalId");
    return res.status(400).json({ ok: false, status: "INVALID", error: "missing_globalId" });
  }
  const presence = {
    nodeId: String(payload.nodeId || "").trim(),
    globalId,
    publicKeyHash: String(payload.publicKeyHash || "").trim(),
    relayUrl: String(payload.relayUrl || req.get("origin") || "").trim(),
    networkType: String(payload.networkType || "").trim(),
    online: payload.online !== false,
    lastSeen: nowMs(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  };
  await db.collection(PRESENCE).doc(globalId).set(presence, { merge: true });
  logger.info("GHALBIT-SERVER-PRESENCE HEARTBEAT_OK", { globalId });
  res.json({
    ok: true,
    status: "ONLINE",
    online: true,
    lastSeen: presence.lastSeen,
    presence
  });
});

app.get("/presence/:globalId", async (req, res) => {
  const globalId = normalizeId(req.params.globalId);
  if (!globalId) {
    return res.status(400).json({ ok: false, error: "missing_globalId" });
  }
  const snapshot = await db.collection(PRESENCE).doc(globalId).get();
  if (!snapshot.exists) {
    logger.warn("GHALBIT-SERVER-PRESENCE LOOKUP_FAIL", { globalId });
    return res.status(404).json({ ok: false, online: false, error: "not_found" });
  }
  const presence = snapshot.data();
  logger.info("GHALBIT-SERVER-PRESENCE LOOKUP_OK", { globalId, lastSeen: presence.lastSeen || 0 });
  res.json({ ok: true, online: presence.online === true, lastSeen: presence.lastSeen || 0, presence });
});

app.post("/relay/send", async (req, res) => {
  const payload = req.body || {};
  const targetGlobalId = normalizeId(payload.targetGlobalId);
  const sourceGlobalId = normalizeId(payload.sourceGlobalId || payload.senderGlobalId);
  const eventId = ensureEventId(payload);
  const type = normalizeEventType(payload);
  if (!targetGlobalId) {
    logger.warn("GHALBIT-CALL-SIGNAL relayRejected", { type, eventId, reason: "missing_targetGlobalId" });
    return res.status(400).json({ ok: false, status: "REJECTED", error: "missing_targetGlobalId" });
  }
  const event = {
    ...payload,
    eventId,
    targetGlobalId,
    sourceGlobalId,
    type,
    createdAt: Number(payload.createdAt || nowMs()),
    expiresAt: Number(payload.expiresAt || nowMs() + 24 * 60 * 60 * 1000)
  };
  await appendInbox(targetGlobalId, event);
  logger.info("GHALBIT-CALL-SIGNAL relayAccepted", {
    type,
    eventId,
    callId: payload.callId || "",
    source: sourceGlobalId,
    target: targetGlobalId
  });
  res.json({
    ok: true,
    status: "ACCEPTED",
    messageId: payload.messageId || eventId,
    eventId
  });
});

app.get("/relay/inbox/:globalId", async (req, res) => {
  const globalId = normalizeId(req.params.globalId);
  const ref = db.collection(INBOX).doc(globalId);
  const snapshot = await ref.get();
  const items = snapshot.exists ? snapshot.data().items || [] : [];
  const alive = items.filter((item) => Number(item.expiresAt || nowMs() + 1) > nowMs());
  const split = splitInbox(alive);
  if (snapshot.exists) {
    // Consume fetched inbox items so the client does not keep replaying
    // the same relay message on every polling cycle.
    await ref.set({ items: [], updatedAt: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });
  }
  logger.info("GHALBIT-CALL-INBOX fetch", { globalId, count: alive.length });
  res.json({
    ok: true,
    globalId,
    messages: split.messages,
    receipts: split.receipts,
    callSignals: split.callSignals
  });
});

async function storeReceipt(kind, payload) {
  const messageId = String(payload.messageId || "").trim();
  const targetGlobalId = normalizeId(payload.targetGlobalId || payload.globalId);
  if (!messageId || !targetGlobalId) {
    return { ok: false, code: 400, body: { ok: false, error: "missing_messageId_or_target" } };
  }
  const event = {
    eventId: `${kind}-${messageId}-${nowMs()}`,
    type: kind.toUpperCase(),
    messageId,
    targetGlobalId,
    sourceGlobalId: normalizeId(payload.sourceGlobalId || payload.globalId),
    createdAt: nowMs(),
    expiresAt: nowMs() + 24 * 60 * 60 * 1000
  };
  await db.collection(RECEIPTS).doc(event.eventId).set(event);
  await appendInbox(targetGlobalId, event);
  return { ok: true, code: 200, body: { ok: true, status: kind.toUpperCase(), eventId: event.eventId } };
}

app.post("/receipt/delivered", async (req, res) => {
  const result = await storeReceipt("delivered", req.body || {});
  logger.info("GHALBIT-SERVER-CHAT DELIVERED_OK", { ok: result.ok });
  res.status(result.code).json(result.body);
});

app.post("/receipt/read", async (req, res) => {
  const result = await storeReceipt("read", req.body || {});
  logger.info("GHALBIT-SERVER-CHAT READ_OK", { ok: result.ok });
  res.status(result.code).json(result.body);
});

async function upsertSession(path, payload) {
  const callId = String(payload.callId || "").trim();
  if (!callId) {
    return { ok: false, code: 400, body: { ok: false, error: "missing_callId" } };
  }
  const targetGlobalId = normalizeId(payload.targetGlobalId || payload.peerGlobalId);
  const ref = db.collection(SESSIONS).doc(callId);
  await ref.set(
    {
      callId,
      status: path,
      targetGlobalId,
      sourceGlobalId: normalizeId(payload.sourceGlobalId),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      payload
    },
    { merge: true }
  );
  if (targetGlobalId) {
    await appendInbox(targetGlobalId, {
      eventId: `${path}-${callId}-${nowMs()}`,
      type: `CALL_${path.toUpperCase()}`,
      callId,
      targetGlobalId,
      sourceGlobalId: normalizeId(payload.sourceGlobalId),
      createdAt: nowMs(),
      expiresAt: nowMs() + 60 * 60 * 1000
    });
  }
  return {
    ok: true,
    code: 200,
    body: {
      ok: true,
      status: path.toUpperCase(),
      callId,
      targetGlobalId
    }
  };
}

["start", "ringing", "accept", "reject", "end"].forEach((path) => {
  app.post(`/session/${path}`, async (req, res) => {
    const result = await upsertSession(path, req.body || {});
    logger.info(`GHALBIT-SERVER-CALL ${path.toUpperCase()}_OK`, { ok: result.ok, callId: req.body?.callId || "" });
    res.status(result.code).json(result.body);
  });
});

exports.operator = onRequest(
  {
    region: "asia-southeast1",
    timeoutSeconds: 60,
    memory: "256MiB"
  },
  app
);
