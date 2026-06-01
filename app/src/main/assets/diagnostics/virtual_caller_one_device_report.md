# Virtual Caller One Device Diagnostic (PHASE 300R)

## Purpose

Diagnose one-device incoming call behavior using a virtual caller trigger:

1. Trigger incoming call UI
2. Observe ringing state
3. User accepts/rejects manually
4. Detect transition to connected state
5. Run audio truth probe (10 seconds wait window before probe)
6. Verify ringtone stop behavior
7. End-state + smart recovery summary

## Runtime Step

- Auto Diagnostic step: `VIRTUAL_INCOMING_CALL_CHECK`
- Orchestrator source:
  - `app/src/main/java/com/ghalbitnet/meshx2/diagnostics/autodiag/AutoDiagnosticOrchestrator.kt`

## Main Components

- `VirtualCallerTool`
- `OneDeviceIncomingCallDiagnostic`
- `VirtualCallScenario`
- `VirtualCallReportGenerator`

Location:

- `app/src/main/java/com/ghalbitnet/meshx2/diagnostics/virtualcall/`

## Required Logs

- `GHALBIT-VIRTUAL-CALL START caller=...`
- `GHALBIT-VIRTUAL-CALL RINGING callId=...`
- `GHALBIT-VIRTUAL-CALL ACCEPTED callId=...`
- `GHALBIT-VIRTUAL-CALL CONNECTED callId=...`
- `GHALBIT-VIRTUAL-CALL AUDIO_CAPTURE rms=... peak=... speech=...`
- `GHALBIT-VIRTUAL-CALL RINGTONE_STOPPED result=...`
- `GHALBIT-VIRTUAL-CALL ENDED callId=...`
- `GHALBIT-VIRTUAL-CALL RESULT status=...`

## Notes

- This diagnostic **does not replace full two-device validation**.
- It is a one-device runtime sanity check to validate:
  - incoming-call UI pipeline
  - accept/connect state transitions
  - ringtone stop behavior
  - microphone activity capture
  - recovery visibility
