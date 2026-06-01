# Internet Server Operator Readiness (PHASE 300Q)

## Scope

Server-first runtime readiness for:

- Presence: register, heartbeat, lookup/presence status
- Chat relay: send, inbox, delivered/read ack
- Call signaling: start, ringing, accept, reject, end

## Runtime Step

Auto Diagnostic step:

- `SERVER_OPERATOR_FULL_CHECK`

Source:

- `/app/src/main/java/com/ghalbitnet/meshx2/diagnostics/InternetServerOperatorReadinessProbe.kt`
- `/app/src/main/java/com/ghalbitnet/meshx2/diagnostics/autodiag/AutoDiagnosticOrchestrator.kt`

## Status Model

- `READY`: all operator checks pass
- `PARTIAL`: configured, but only subset passes
- `FAILED`: configured, but checks fail
- `SERVER_NOT_CONFIGURED`: relay/presence base URL not ready
- `FAKE_SERVER_PASS_ONLY`: offline/fake simulation can pass while real server remains unproven

## Required Logs

Presence:

- `GHALBIT-SERVER-PRESENCE REGISTER_OK/REGISTER_FAIL`
- `GHALBIT-SERVER-PRESENCE HEARTBEAT_OK/HEARTBEAT_FAIL`
- `GHALBIT-SERVER-PRESENCE ONLINE/OFFLINE/LAST_SEEN`

Chat:

- `GHALBIT-SERVER-CHAT SEND_OK/SEND_FAIL`
- `GHALBIT-SERVER-CHAT INBOX_OK/INBOX_FAIL`
- `GHALBIT-SERVER-CHAT DELIVERED_OK/DELIVERED_FAIL`
- `GHALBIT-SERVER-CHAT READ_OK/READ_FAIL`

Call signaling:

- `GHALBIT-SERVER-CALL START_OK/START_FAIL`
- `GHALBIT-SERVER-CALL RINGING`
- `GHALBIT-SERVER-CALL ACCEPT_OK/ACCEPT_FAIL`
- `GHALBIT-SERVER-CALL REJECT_OK/REJECT_FAIL`
- `GHALBIT-SERVER-CALL END_OK/END_FAIL`

## Smart Recovery Mapping

Integrated in `SmartRecoveryEngine`:

- missing base URL -> `SERVER_NOT_CONFIGURED`
- timeout -> `SERVER_TIMEOUT`
- 401/403 -> `AUTH_REQUIRED`
- 404 -> `ENDPOINT_MISSING`
- 500 -> `SERVER_ERROR`
- connection refused -> `SERVER_DOWN`

## Notes

- This phase does not add advanced call media features.
- This phase keeps mesh/local flow intact.
- This phase is diagnostic-first for internet operator readiness.
