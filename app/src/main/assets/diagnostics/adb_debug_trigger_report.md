# PHASE 300U — ADB Debug Diagnostic Trigger

## Tujuan
Menyediakan trigger diagnostik dari ADB/Codex tanpa mengekspor `AutoDiagnosticActivity`.

## Implementasi aman

### 1. `AutoDiagnosticActivity` tetap tertutup
- Status manifest tetap:
  - `android:exported="false"`

### 2. Trigger debug dipindah ke source set `debug`
- Receiver ADB tidak dipasang di `main` manifest.
- Receiver hanya diregistrasikan pada:
  - `app/src/debug/AndroidManifest.xml`

Artinya:
- build debug bisa menerima broadcast ADB
- build release tidak membuka receiver ini

## Action yang diterima

Receiver: `DiagnosticDebugReceiver`

- `com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CALL_CHECK`
- `com.ghalbitnet.meshx2.debug.RUN_FULL_DIAGNOSTIC`
- `com.ghalbitnet.meshx2.debug.RUN_AUDIO_TRUTH`

## Log wajib

Jika ditolak:
- `GHALBIT-DEBUG-TRIGGER DENIED reason=...`

Jika diterima:
- `GHALBIT-DEBUG-TRIGGER RECEIVED action=...`
- `GHALBIT-DEBUG-TRIGGER DISPATCH target=...`
- `GHALBIT-DEBUG-TRIGGER RESULT status=...`

Untuk virtual call:
- `GHALBIT-VIRTUAL-CALL TRIGGER_RECEIVED source=adb_debug`
- `GHALBIT-VIRTUAL-CALL TOOL_START`
- `GHALBIT-VIRTUAL-CALL ACCEPTED`
  atau
- `GHALBIT-VIRTUAL-CALL FAIL_STAGE stage=... reason=...`

## Jalur dispatch

### Virtual call
- Receiver memanggil `OneDeviceIncomingCallDiagnostic.run(...)`
- Diagnostic tersebut memanggil `VirtualCallerTool.run(...)`
- Jika layar panggilan tidak berhasil dibuka, hasil tetap tercatat sebagai partial/fail stage

### Full diagnostic
- Receiver memanggil `AutoDiagnosticOrchestrator.run(...)`

### Audio truth
- Receiver memanggil `AudioTruthProbe.run(...)`

## Runtime command target

```powershell
adb logcat -c
adb shell am broadcast -a com.ghalbitnet.meshx2.debug.RUN_VIRTUAL_CALL_CHECK
adb logcat -d | findstr /i "GHALBIT-DEBUG-TRIGGER GHALBIT-VIRTUAL-CALL GHALBIT-AUDIO-IN GHALBIT-RECOVERY FATAL EXCEPTION ANR"
```

## Ekspektasi

- Command ADB tidak perlu membuka `AutoDiagnosticActivity`
- Trigger awal selalu masuk ke receiver debug pada build debug
- Stage terakhir selalu terlihat jelas di log
