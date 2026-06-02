## Popup and Virtual Peer Inbox Proof

### Scope
- Incoming chat popup should open the correct conversation when tapped, even if `ChatActivity` is already running.
- Incoming call popup continues using the existing full-screen call notification path.
- Virtual peer inbox proof allows checking whether the server is delivering back receipts and call signals to the simulated peer.

### Changes
1. `ChatActivity`
   - Added `applyConversationIntent(intent)` so both `onCreate()` and `onNewIntent()` resolve the active conversation consistently.
   - Notification taps now resolve conversation identity from:
     - `peerName`
     - deep-link `EXTRA_CONVERSATION_ID`
     - fallback `globalId -> chatId` lookup through `ConversationIdentityStore.findChatIdByGlobalId(...)`
   - When a message popup is tapped while chat is already open, `renderHistory()` is triggered again for the newly selected conversation.

2. `ConversationIdentityStore`
   - Added `findChatIdByGlobalId(context, globalId)` to bridge runtime global identity with stored chat ids.

3. `DiagnosticDebugReceiver`
   - Added `RUN_VIRTUAL_PEER_INBOX_CHECK` debug action.
   - Supports optional `targetGlobalId` extra for probing a specific virtual peer inbox.

4. `VirtualPeerInboxProbe`
   - Fetches the virtual peer inbox through `OnlineFallbackTransport.fetchInbox(...)`
   - Summarizes:
     - messages
     - receipts
     - call signals

### Expected Runtime Logs
- `GHALBIT-NOTIFY open conversation peer=... globalId=...`
- `GHALBIT-NOTIFY message clicked id=...`
- `GHALBIT-DEBUG-TRIGGER DISPATCH target=virtual_peer_inbox`
- `GHALBIT-VIRTUAL-PEER INBOX_CHECK globalId=... messages=... receipts=... callSignals=...`

### Practical Meaning
- Message popups behave more like a real messenger: tap opens the right chat instead of staying on the previous conversation.
- Two-way simulation becomes easier to prove because we can inspect whether the virtual peer is receiving:
  - delivered receipts
  - read receipts
  - call signaling events
