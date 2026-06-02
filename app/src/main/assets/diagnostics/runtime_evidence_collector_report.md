# PHASE 300V — Runtime Evidence Collector

## Tujuan
Menyimpan bukti runtime penting ke file lokal agar tidak hilang saat logcat terfilter, app restart, atau event terjadi di waktu yang sulit ditangkap.

## Lokasi file

Disimpan pada internal app files:

`files/diagnostics/runtime_events.jsonl`

## Format

JSON Lines:

```json
{"ts":1717300000000,"event":"CALL_ACCEPTED","source":"OneDeviceIncomingCallDiagnostic","messageId":"","callId":"virt-ab12cd34","peerId":"VIRTUAL_CALLER_PC","status":"ACCEPTED","details":""}
```

## Event yang sudah ditanam

### Call / virtual call
- `VIRTUAL_CALL_TRIGGER_RECEIVED`
- `VIRTUAL_CALL_TOOL_START`
- `INCOMING_CALL_SCREEN_OPENED`
- `RINGING_STARTED`
- `CALL_ACCEPTED`
- `CALL_CONNECTED`
- `AUDIO_CAPTURE_STARTED`
- `AUDIO_SPEECH_DETECTED`
- `RINGTONE_STOPPED`
- `CALL_ENDED`
- `FAIL_STAGE`
- `RECOVERY_ACTION_APPLIED`

### Server
- `SERVER_BASE_URL_CONFIGURED`
- `SERVER_HEALTH_OK`
- `SERVER_HEALTH_FAIL`
- `SERVER_REGISTER_OK`
- `SERVER_HEARTBEAT_OK`
- `SERVER_RELAY_OK`
- `SERVER_SESSION_OK`
- `SERVER_NOT_CONFIGURED`

### Chat / media
- `MESSAGE_CREATED`
- `MESSAGE_PENDING`
- `MESSAGE_DELIVERED`
- `MESSAGE_READ`
- `MEDIA_PENDING`
- `FAILED_BEFORE_TTL_BLOCKED`

## Integrasi minimal

- `VirtualCallerTool`
- `OneDeviceIncomingCallDiagnostic`
- `AudioTruthProbe`
- `ServerTruthProbe`
- `InternetServerOperatorReadinessProbe`
- `ChatDeliveryManager`
- `SmartRecoveryEngine`
- `AutoDiagnosticActivity` export/clear controls

## Cara pakai

1. Buka **Auto Diagnostic Center**
2. Tekan **CLEAR RUNTIME EVIDENCE**
3. Jalankan fitur yang ingin dibuktikan
4. Tekan **EXPORT RUNTIME EVIDENCE**
5. Ringkasan akan:
   - ditampilkan di layar
   - disalin ke clipboard

## Tujuan pembuktian

Collector ini dibuat khusus supaya event seperti:
- `ACCEPT_PRESSED`
- `AUDIO_SPEECH_DETECTED`
- `RINGTONE_STOPPED`

tetap bisa dibuktikan meski logcat tidak lengkap.
