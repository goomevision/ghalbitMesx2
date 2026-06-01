# Audio Truth Laboratory (PHASE 300L)

Mode: SAFE DIAGNOSTIC ONLY

## Tujuan
- Validasi mikrofon lokal
- Validasi speaker tone lokal (440Hz, 1kHz)
- Validasi noise floor / RMS / peak
- Deteksi speech / clipping sederhana
- Loopback internal tanpa jaringan

## Cara Menjalankan
1. Buka Runtime Dashboard.
2. Tekan tombol **AUDIO TRUTH LAB**.
3. Tunggu proses selesai (sekitar 3–5 detik).
4. Ringkasan laporan otomatis disalin ke clipboard.
5. Periksa logcat dengan filter:
   - `GHALBIT-AUDIO-IN`
   - `GHALBIT-AUDIO-OUT`
   - `GHALBIT-TONE-TEST`
   - `GHALBIT-LOOPBACK`
   - `GHALBIT-AUDIO-TRUTH`

## Log Wajib
- `GHALBIT-AUDIO-IN START/RMS/PEAK/NOISE/SPEECH_DETECTED/CLIPPING/STOP`
- `GHALBIT-AUDIO-OUT START/WRITE/UNDERRUN/STALL/STOP`
- `GHALBIT-TONE-TEST START/PLAYED/STOP`
- `GHALBIT-LOOPBACK START/INPUT_FRAMES/OUTPUT_FRAMES/LATENCY_MS/RESULT`

## Skor Kesehatan Audio
`healthScore` dihitung 0–100 dari:
- kelengkapan frame mic
- kualitas RMS/peak
- indikasi clipping
- keberhasilan tone playback
- indikasi underrun/stall output
- hasil loopback internal

## Catatan
- Ini bukan pengujian voice call end-to-end antar perangkat.
- Ini hanya proof dasar audio lokal 1 HP.

