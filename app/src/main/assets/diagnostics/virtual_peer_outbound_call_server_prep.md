# Virtual Peer Outbound Call Server Prep

Purpose:
- let HP A start an outgoing call toward Virtual HP B through the server-first signaling path
- keep this diagnostic-only and low risk

What is prepared:
- `VirtualPeerOutboundCallProbe.launchServerFirstOutgoingCall(...)`
- `VirtualPeerOutboundCallProbe.launchServerFirstOutgoingCallWithAutoAccept(...)`
- marks `GX-VIRTUAL-HP-B` as online through `OnlinePresenceManager.applyRealtimePresence(...)`
- opens `CallSessionActivity` as an outgoing call targeting:
  - peer name: `Virtual HP B`
  - peer globalId: `GX-VIRTUAL-HP-B`
  - route source: operator relay
- stores the same `callId` for the virtual signaling probe
- can trigger virtual `ringing -> accept` after the outgoing call starts

Expected route behavior:
- `GhalbitCallManager.resolveRoute(...)` can choose `INTERNET_RELAY`
- signaling then uses `InternetRelaySignalingChannel`

What this does not claim yet:
- full two-way voice media through the server
- server-mediated playback proof

Why this step matters:
- HP A can now begin a server-first outgoing call attempt to the virtual peer
- this is the cleanest first step before strengthening accept/media analysis
