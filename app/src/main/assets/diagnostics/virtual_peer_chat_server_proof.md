PHASE 302C - Virtual HP B Chat Server Proof

Goal:
- Prove a normal virtual text message can travel from virtual HP B to HP A through the operator server.
- Prove HP A can send delivered and read receipts back through the operator server.

Proven runtime result:
- Runtime target globalId on HP A: `GX-3762AC7DFBC7`
- Message `CHAT-VIRTUAL-B-003` posted to `/relay/send`: `ok=true status=ACCEPTED`
- HP A pulled relay inbox and stored the message locally.
- HP A sent delivered receipt to `/receipt/delivered`.
- HP A then sent read receipt to `/receipt/read`.

Observed logs:
1. Delivery proof
   - `GHALBIT-ANDROID-RELAY: pull inbox count=1`
   - `GHALBIT-NOTIFY: message shown id=CHAT-VIRTUAL-B-003`
   - `GHALBIT-BG: message received id=CHAT-VIRTUAL-B-003`
   - `GHALBIT-INTERNET-TX: url=.../receipt/delivered ok=true status=DELIVERED code=200`
   - `GHALBIT-ANDROID-RELAY: ack sent messageId=CHAT-VIRTUAL-B-003`
   - `GHALBIT-CHAT-ACK: id=CHAT-VIRTUAL-B-003 delivered=true remote=true`

2. Read proof
   - `GHALBIT-DEBUG-TRIGGER: RECEIVED action=com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CHAT_READ`
   - `GHALBIT-DEBUG-TRIGGER: DISPATCH target=virtual_chat_read`
   - `GHALBIT-INTERNET-TX: url=.../receipt/read ok=true status=READ code=200`
   - `GHALBIT-ANDROID-RELAY: read sent messageId=CHAT-VIRTUAL-B-003`
   - `GHALBIT-DELIVERY-SEMANTIC: packetId=CHAT-VIRTUAL-B-003 stage=READ_BY_USER state=READ_REMOTE`
   - `GHALBIT-READ: remote receipt sent id=CHAT-VIRTUAL-B-003`

Implementation note:
- The read trigger now checks multiple virtual chat aliases: `Virtual HP B` and `GX-VIRTUAL-HP-B`.
- This avoids brittle behavior when incoming relay chat is stored under `senderGlobalId` because `senderDisplayName` is absent or different.
