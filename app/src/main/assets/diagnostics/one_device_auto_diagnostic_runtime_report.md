# PHASE 300O — One Device Auto Diagnostic Runtime Report

Tanggal: 2026-06-02 (Asia/Jakarta)
Branch: `restore-ui-from-codex`

## 1) Device Detection
- `adb devices`: **detected**
- Device ID: `115413747T003958`

## 2) Install Result
- Command: `.\gradlew.bat installDebug --no-daemon`
- Result: **SUCCESS** (installed on 1 device)

## 3) Auto Diagnostic Execution
- App launched on device: **yes**
- Auto Diagnostic Center executed (`RUN FULL DIAGNOSTIC` logs observed): **yes**

## 4) Runtime Scores (from `GHALBIT-AUTO-DIAG SCORE`)
- server score: **66**
- network score: **90**
- simulation score: **80**
- audio score: **100**
- media score: **85**
- call signaling score: **80**
- loop guard score: **88**
- total: **83**
- final status: **PARTIAL**

## 5) Stability / Error Signals
- `FATAL EXCEPTION`: **not found** in filtered runtime capture
- `ANR`: **not found** in filtered runtime capture
- `ECONNREFUSED`: **not found** in filtered runtime capture

## 6) Audio Runtime Evidence
Observed tags:
- `GHALBIT-AUDIO-IN START/RMS/PEAK/NOISE/SPEECH_DETECTED/STOP`
- `GHALBIT-AUDIO-OUT START/WRITE/STOP`
- `GHALBIT-TONE-TEST START/PLAYED/STOP`
- `GHALBIT-LOOPBACK ... RESULT=true`

Key evidence:
- Tone 440 Hz played (`frames=5600`)
- Tone 1 kHz played (`frames=5600`)
- Loopback internal result: `RESULT=true`, `LATENCY_MS=1`

## 7) Server/Network Evidence
Observed:
- `GHALBIT-SERVER-TRUTH BASE_URL relay= presence= configured=false`
- `GHALBIT-AUTO-DIAG STEP name=server status=PARTIAL`
- `GHALBIT-AUTO-DIAG STEP name=network status=PASS`

Interpretation:
- Network local runtime healthy.
- Server integration still partial because relay/presence base URL not configured on device runtime.

## 8) Features Still Needing 2-Device Real Test
- Direct peer chat delivery over real peer online/offline transition
- Cross-device receipt timing (delivered/read) end-to-end
- Call signaling exchange A↔B with ringing/accept/reject from second physical device
- Route fallback behavior under real handoff and peer route loss

## 9) Conclusion
One-device auto diagnostic runtime is operational and stable with **PARTIAL (83/100)** outcome.
Main remaining gap is server/operator endpoint configuration and multi-device end-to-end proof.

