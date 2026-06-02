PHASE 301A.2 - Nearby Voice Probe Failure Audit and Bypass Strategy

Finding:
- one-device virtual call uses `virtual://incoming`
- this route is not a real mesh peer with a live ACK path
- forcing `probeNearbyVoice()` on this route causes activation to fail before the realtime engine starts

Observed failure path:
- `ACTIVATION_START`
- nearby probe attempted
- no ACK returned
- `CALL_AUDIO_ENGINE_FAIL stage=probe reason=nearby_probe_failed`

Why this happens:
- `VOICE_PROBE` is designed for a real peer transport
- a diagnostic virtual caller does not behave like a full remote node
- therefore the probe gate is stricter than the diagnostic scenario can satisfy

Safe bypass strategy applied:
- only for routes starting with `virtual://`
- skip nearby voice probe
- skip voice transport handshake
- continue into realtime engine activation

Why this is safe:
- applies only to one-device virtual diagnostic
- does not change real mesh, LAN, or relay behavior
- keeps real peers on the normal probe and handshake path

Expected next proof after this patch:
- `GHALBIT-CALL-AUDIO-ENGINE START`
- `GHALBIT-CALL-AUDIO-ENGINE READY`
- then either:
  - `CALL_AUDIO_TX` appears, proving engine is alive but route is virtual only
  - or engine fails later, revealing the next real blocker
