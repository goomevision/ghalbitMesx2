PHASE 302B - Virtual HP B SOS Server Proof

Goal:
- Prove HP A can be discovered on the operator server.
- Prove a virtual HP B can send an SOS through the operator server.
- Prove HP A can pull that SOS from relay inbox and route it into SosAlertManager.

Runtime proof path:
1. HP A runs RUN_SERVER_PRESENCE_CHECK.
2. Virtual HP B posts /relay/send with contentType=SOS and targetGlobalId=HP A globalId.
3. HP A runs RUN_RELAY_INBOX_SYNC.
4. Expected logs on HP A:
   - GHALBIT-SOS-INBOX received
   - GHALBIT-SOS-RX
   - GHALBIT-SOS-UI
   - GHALBIT-SOS-NOTIFY

Notes:
- Relay inbox SOS is now routed through ChatDeliveryManager into SosAlertManager.
- The same relay message can still be stored as chat history, but SOS alert proof uses the SOS manager as the primary signal path.

Runtime proof captured on 2026-06-03:
- Server accepted virtual SOS:
  - messageId=SOS-VIRTUAL-B-002
  - targetGlobalId=GX-3762AC7DFBC7
- HP A logs proved delivery:
  - GHALBIT-ANDROID-RELAY pull inbox count=1
  - GHALBIT-SOS-INBOX received packetId=SOS-VIRTUAL-B-002
  - GHALBIT-SOS-RX
  - GHALBIT-SOS-UI
  - GHALBIT-SOS-NOTIFY

Follow-up gaps discovered:
- Presence proof used globalId GX-D215FB9592BB while relay runtime polling used GX-3762AC7DFBC7.
- Delivered ACK back to virtual sender still triggered a JSON parse error, which suggests an operator endpoint/response mismatch for that return path.
