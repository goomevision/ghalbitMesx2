PHASE 301D — RX Jitter Buffer & Playback Stability Fix

Tujuan:
- menurunkan drop/conceal yang terlalu agresif pada panggilan nyata 2 HP
- memberi frame yang datang sedikit terlambat kesempatan untuk tetap diputar
- menambah bukti runtime yang lebih jujur untuk stabilitas playback

Perubahan utama:
1. AudioPacketJitterBuffer
   - target buffer awal dinaikkan dari 2 frame menjadi 4 frame
   - jika frame yang diharapkan belum datang tetapi buffer masa depan masih tipis, buffer menahan beberapa poll terlebih dahulu
   - jika buffer masa depan sudah cukup sehat, buffer melakukan resync ke frame nyata berikutnya daripada terus conceal/drop
   - log baru:
     - GHALBIT-CALL-AUDIO-JITTER hold
     - GHALBIT-CALL-AUDIO-JITTER resync
   - metrics baru:
     - hold
     - resync

2. AudioPlaybackWorker
   - menambah log stabilitas playback burst
   - log baru:
     - GHALBIT-CALL-AUDIO-PLAYBACK-STABLE playBurst=...

Harapan runtime:
- rx tetap naik
- play ikut naik lebih signifikan
- drop/conceal tidak melonjak secepat sebelumnya
- audio lawan lebih terdengar dan tidak cepat hilang di sisi playback
