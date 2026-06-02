PHASE 301E — Call Tone Ping-Pong Lab

Tujuan:
- membuat mode uji panggilan 2 HP yang lebih terkontrol
- mengirim sinyal audio buatan yang konsisten melalui jalur call realtime
- membiarkan aplikasi menganalisis apakah sinyal benar-benar sampai

Cara kerja:
- pengguna melakukan panggilan biasa dan mengangkat di 2 HP
- setelah call mencapai jalur suara aktif, long-press tombol speaker pada kedua HP
- mode uji akan aktif
- sisi caller mengirim tone 440 Hz pada slot tertentu
- sisi callee mengirim tone 660 Hz pada slot tertentu
- kedua sisi menganalisis tone yang masuk dan mencatat evidence TX/RX

Log utama:
- GHALBIT-CALL-LAB MODE
- GHALBIT-CALL-LAB TX
- GHALBIT-CALL-LAB RX
- GHALBIT-CALL-LAB SUMMARY

Evidence utama:
- CALL_TONE_LAB_ENABLED
- CALL_TONE_LAB_DISABLED
- CALL_TONE_LAB_TX
- CALL_TONE_LAB_RX

Tujuan analisis:
- membuktikan jalur call realtime dengan pola yang tidak bergantung pada ucapan manusia
- memudahkan diagnosis TX sukses tetapi RX lemah
- memudahkan diagnosis RX masuk tetapi playback tidak stabil
