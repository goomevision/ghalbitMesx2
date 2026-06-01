# PHASE 300M — Auto Diagnostic Center

Mode: SAFE DIAGNOSTIC UI ONLY

## Cakupan
- Server readiness snapshot (catalog-based)
- Network status dasar
- Simulation readiness indicator
- Pending queue health probe
- Receipt dry-run signal
- Call signaling readiness check
- Audio Truth Lab integration
- Loop guard timing probe

## Log Wajib
- `GHALBIT-AUTO-DIAG START`
- `GHALBIT-AUTO-DIAG STEP name=... status=...`
- `GHALBIT-AUTO-DIAG SCORE server=... network=... simulation=... audio=... media=... call=... loop=...`
- `GHALBIT-AUTO-DIAG RESULT status=...`

## Cara Pakai
1. Buka Runtime Dashboard.
2. Tekan **RUN FULL DIAGNOSTIC**.
3. App membuka **Auto Diagnostic Center**.
4. Tekan tombol **RUN FULL DIAGNOSTIC**.
5. Hasil ringkas tampil di layar dan report markdown disalin ke clipboard.

## Contoh Status
- PASS: komponen sehat dan siap
- PARTIAL: komponen tersedia namun butuh bukti runtime/backend
- FAIL: komponen gagal probe dasar

