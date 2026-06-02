PHASE 301C addendum - Realtime recovery and end sync

Goals:
- Return from PTT fallback to live call mode when conditions improve.
- Make local hang-up more likely to reach the remote side even if the activity closes quickly.

Changes:
1. When the watchdog upgrades back to `LIVE_VOICE`, the UI now restores
   `VOICE_STREAM_ACTIVE` instead of leaving the call visually stuck in `PTT_FALLBACK`.
2. Local `CALL_END` dispatch now uses a detached IO scope so the end signal
   is not tied to the activity lifecycle shutting down immediately.

Expected runtime:
- If conditions improve after a fallback, the call can present itself again as
  a live voice call.
- When one side closes the call, the end signal has a better chance to reach
  the other side before the local activity is destroyed.
