PHASE 301C - Realtime Call State Softening & Real Transport Proof Prep

Purpose:
- Stop virtual diagnostic calls from looking broken after TX/RX/PLAY is already proven.
- Keep the virtual route in `VOICE_STREAM_ACTIVE` with a softer status message.
- Add explicit prep logs for the next real transport proof phase.

Changes:
1. `onRealtimeFailure` is softened only when the active route is virtual diagnostic.
2. The audio watchdog no longer forces `PTT_FALLBACK` for virtual diagnostic routes.
3. Non-virtual peers now emit `GHALBIT-CALL-REAL-TRANSPORT-PREP` with route and relay readiness context.

Expected runtime:
- Virtual diagnostic:
  - stays in voice-active state
  - shows a status like "Mode diagnostik virtual aktif, audio balik virtual sedang diverifikasi."
- Real transport:
  - existing fallback logic stays intact
  - prep logs reveal route/relay context before activation

Next proof target:
- Observe `GHALBIT-CALL-REAL-TRANSPORT-PREP` on a non-virtual peer.
- Verify whether realtime TX/RX/PLAY works on a genuine transport path after the virtual loopback proof.
