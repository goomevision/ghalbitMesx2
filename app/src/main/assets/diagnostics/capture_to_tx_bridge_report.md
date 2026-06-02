# PHASE 301B — TX Success Path & Virtual Audio Sink

## Tujuan
- Membuktikan jalur `capture -> tx attempt -> tx success/fail`
- Menyediakan `VirtualAudioSink` untuk route `virtual://incoming`
- Membuat virtual diagnostic mampu menghasilkan bukti `TX`, `RX`, dan `PLAY`

## Perubahan
- Menambahkan bridge log:
  - `GHALBIT-CALL-AUDIO-BRIDGE CAPTURE_READY`
  - `GHALBIT-CALL-AUDIO-BRIDGE ENCODE_START`
  - `GHALBIT-CALL-AUDIO-BRIDGE ENCODE_OK`
  - `GHALBIT-CALL-AUDIO-BRIDGE PACKET_READY`
  - `GHALBIT-CALL-AUDIO-BRIDGE TX_ATTEMPT`
  - `GHALBIT-CALL-AUDIO-BRIDGE TX_SUCCESS`
  - `GHALBIT-CALL-AUDIO-BRIDGE TX_FAIL`
  - `GHALBIT-CALL-AUDIO-BRIDGE CAPTURE_STOP`
- Menambahkan runtime evidence:
  - `CALL_AUDIO_CAPTURE_READY`
  - `CALL_AUDIO_ENCODE_OK`
  - `CALL_AUDIO_TX_ATTEMPT`
  - `CALL_AUDIO_TX`
  - `CALL_AUDIO_TX_FAIL`
  - `CALL_AUDIO_CAPTURE_STOP`
- Menambahkan `VirtualAudioSink` di `FullDuplexCallEngine`:
  - jika endpoint memakai `virtual://incoming` atau peer `VIRTUAL_CALLER_PC`
  - frame dianggap TX sukses ke route `virtual_diagnostic`
  - frame langsung di-loopback ke RX lokal untuk pembuktian playback

## Ekspektasi Runtime
- `CALL_AUDIO_CAPTURE_READY` muncul
- `CALL_AUDIO_TX_ATTEMPT` muncul
- `CALL_AUDIO_TX` muncul untuk virtual diagnostic
- `CALL_AUDIO_RX` muncul karena sink virtual memantulkan frame ke jitter buffer
- `CALL_AUDIO_PLAY` muncul jika playback worker benar-benar memutar frame

## Catatan
- Patch ini tidak mengubah jalur peer nyata
- Peer nyata tetap memakai `CallManager.sendVoicePacket()`
