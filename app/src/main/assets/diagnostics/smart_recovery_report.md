# PHASE 300P — Smart Recovery & Auto Fix Engine

Mode: SAFE SELF-HEALING ONLY

## Implemented Components
- `SmartRecoveryEngine`
- `ErrorClassifier`
- `RecoveryAction` / `RecoveryActionName`
- `RecoveryPolicy`
- `RecoveryReportGenerator`
- `AutoFixSuggestion`

## Coverage
### Server
- baseUrl missing
- timeout
- 401/403
- 404 endpoint missing
- 500 server error
- ECONNREFUSED

### Network
- route not found
- route lock spam
- peer stale
- reconnect failed
- pending queue stuck

### Chat/Media
- duplicate messageId
- failed before TTL
- pending too long
- receipt mismatch
- media upload/download failed

### Call
- stuck ringing
- accept not connected
- rx > 0 but played = 0
- ringtone stuck

### Audio
- mic silence
- clipping
- speaker underrun
- loopback failed

## Safe Recovery Actions
- retry with backoff
- mark server partial
- release route lock
- cooldown failed host
- limited rediscovery
- fallback pending queue
- convert failed->pending before TTL
- safe retry
- dedup message id
- resend ack safe
- stop ringtone
- force call cleanup
- enable safe playback
- fallback to PTT
- increase audio buffer
- mark audio needs user check

## Logs
- `GHALBIT-RECOVERY DETECT error= source=`
- `GHALBIT-RECOVERY CLASSIFY type= severity=`
- `GHALBIT-RECOVERY ACTION name= result=`
- `GHALBIT-RECOVERY SKIP reason=`
- `GHALBIT-RECOVERY SUGGEST file= action=`
- `GHALBIT-RECOVERY RESULT recovered= pending= failed=`

## Integration
- Auto Diagnostic Center step added: `smart_recovery`
- Score output now includes `recovery=...`

## Notes
- No destructive behavior.
- No auto-delete.
- No large refactor.
- Patch suggestions are advisory and include risk + human approval flag.

