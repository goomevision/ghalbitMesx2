# Virtual Call Trigger Hardening Report (PHASE 300T)

## Trigger Paths

Implemented trigger entry points:

1. **AutoDiagnosticActivity intent action handler**
   - Action: `com.ghalbitnet.meshx2.action.RUN_VIRTUAL_CALL_CHECK`
   - Logs:
     - `GHALBIT-VIRTUAL-CALL TRIGGER_RECEIVED source=AutoDiagnosticActivity`
2. **RuntimeDashboardActivity shortcut**
   - Long-press `RUN FULL DIAGNOSTIC` button
   - Launches `AutoDiagnosticActivity` with the above action
   - Logs:
     - `GHALBIT-VIRTUAL-CALL TRIGGER_RECEIVED source=RuntimeDashboardActivity`

## Stage Logs Hardened

Early mandatory logs now available:

- `GHALBIT-VIRTUAL-CALL TRIGGER_RECEIVED source=...`
- `GHALBIT-VIRTUAL-CALL STEP_START`
- `GHALBIT-VIRTUAL-CALL TOOL_START`

Failure before opening call screen:

- `GHALBIT-VIRTUAL-CALL FAIL_STAGE stage=... reason=...`

## Virtual Caller Tool Invocation

`VirtualCallerTool.run()` is now the single tool entry for virtual incoming call launch.
`OneDeviceIncomingCallDiagnostic` uses it directly and records fail stage when it cannot open `CallSessionActivity`.

## Fallback Behavior

When launch fails before incoming UI:

- Result status: `PARTIAL_TRIGGER_FAILED`
- Notes include fail stage and reason
- This avoids silent failure and keeps diagnostic readable.

## Intent Action Availability

- Action handler: **available in code**.
- External `adb am start` availability depends on activity export policy.
- Security-safe mode keeps diagnostic activity internal by default.

## Runtime Trigger Result (adb command in PHASE 300T)

Command:

`adb shell am start -a com.ghalbitnet.meshx2.action.RUN_VIRTUAL_CALL_CHECK -n com.ghalbitnet.meshx2/.diagnostics.autodiag.AutoDiagnosticActivity`

Observed result:

- `SecurityException: ... AutoDiagnosticActivity ... not exported`
- Meaning:
  - External shell trigger to this internal activity is blocked by Android security policy.
  - This is expected while keeping `AutoDiagnosticActivity` non-exported in safe mode.

Alternative proven trigger path:

- Internal trigger from app flow (RuntimeDashboard long-press on `RUN FULL DIAGNOSTIC`) reached virtual check path.
- Runtime log evidence captured:
  - `GHALBIT-VIRTUAL-CALL: ACCEPTED callId=...`
  - `GHALBIT-VIRTUAL-CALL: AUDIO_CAPTURE rms=... peak=... speech=true`
  - `GHALBIT-VIRTUAL-CALL: RINGTONE_STOPPED result=true`

Stage terakhir yang tercapai:

- `ACCEPTED` -> `AUDIO_CAPTURE` -> `RINGTONE_STOPPED`
