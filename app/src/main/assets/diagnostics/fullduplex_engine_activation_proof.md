PHASE 301A.1 - FullDuplex Engine Activation Proof

Purpose:
- prove whether the realtime audio engine is actually activated after call connect
- distinguish diagnostic-connected state from real duplex audio activation

Added logs:
- `GHALBIT-CALL-AUDIO-ENGINE ACTIVATION_START`
- `GHALBIT-CALL-AUDIO-ENGINE START`
- `GHALBIT-CALL-AUDIO-ENGINE READY`
- `GHALBIT-CALL-AUDIO-ENGINE FAIL`
- `GHALBIT-CALL-AUDIO-ENGINE STOP`

Added evidence:
- `CALL_AUDIO_ENGINE_START`
- `CALL_AUDIO_ENGINE_READY`
- `CALL_AUDIO_ENGINE_FAIL`
- `CALL_AUDIO_ENGINE_STOP`

Failure stages now made explicit:
- permission
- nearby probe
- relay unavailable
- relay not implemented
- voice handshake
- engine prepare/start

How to interpret:
- if `ACTIVATION_START` exists but no `START`, activation gate is blocked before engine start
- if `START` exists but no `READY`, engine start failed
- if `READY` exists but still no `CALL_AUDIO_TX`, capture callback is not entering transport
- if `CALL_AUDIO_TX` exists but no `CALL_AUDIO_RX`, transport/route is broken
- if `CALL_AUDIO_RX` exists but no `CALL_AUDIO_PLAY`, playback path is broken
