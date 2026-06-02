## Virtual Peer Chat Popup Flow

### Goal
Prove an end-to-end flow:

`Virtual HP B -> server operator -> popup on HP A -> tap popup -> correct conversation opens -> delivered/read visible to virtual peer`

### Debug trigger
- Action: `com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CHAT_SERVER_SEND`
- Optional extra: `message`

### Expected app logs
- `GHALBIT-VIRTUAL-PEER CHAT_SEND_OK packetId=...`
- `GHALBIT-BG message received id=...`
- `GHALBIT-NOTIFY open conversation peer=... globalId=...`
- `GHALBIT-NOTIFY message clicked id=...`

### Expected server-side behavior
- virtual peer inbox should later show:
  - delivered receipt
  - read receipt
  - optional call signals from other tests

### Supporting debug proof
- `RUN_VIRTUAL_PEER_INBOX_CHECK`
  - inspects the simulated peer inbox
  - summarizes messages / receipts / callSignals
