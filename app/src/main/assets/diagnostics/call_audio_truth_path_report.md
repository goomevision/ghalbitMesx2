PHASE 301A - Call Audio Truth Path

Purpose:
- prove the live call audio path step by step
- separate call signaling success from actual audible voice success

Truth path instrumented:
1. capture
   - AudioCaptureWorker
   - logs `GHALBIT-CALL-AUDIO-CAPTURE`
   - evidence `CALL_AUDIO_CAPTURE`
2. tx
   - FullDuplexCallEngine -> CallManager.sendVoicePacket
   - logs `GHALBIT-CALL-AUDIO-TX`
   - evidence `CALL_AUDIO_TX`
3. rx
   - FullDuplexCallEngine.onIncomingAudioPacket
   - logs `GHALBIT-CALL-AUDIO-RX`
   - evidence `CALL_AUDIO_RX`
4. play
   - AudioPlaybackWorker after actual AudioTrack.write
   - logs `GHALBIT-CALL-AUDIO-PLAY`
   - evidence `CALL_AUDIO_PLAY`
5. safe mode
   - CallSessionActivity audio watchdog
   - triggers when `rx > 0` and `played = 0` for > 3s
   - logs `GHALBIT-CALL-AUDIO-SAFE-MODE`
   - evidence `CALL_AUDIO_SAFE_MODE`

How to read runtime:
- capture present, tx absent:
  sender session/route problem
- tx present, rx absent:
  transport or destination route problem
- rx present, play absent:
  jitter/playback/speaker path problem
- safe mode enabled:
  call receives audio but normal playback path is stuck

Watchdog summary log:
- `GHALBIT-CALL-AUDIO capture= tx= txFail= rx= queued= played= dropped= safeMode=`

Current scope:
- diagnostic only
- no architecture rewrite
- no advanced call refactor
