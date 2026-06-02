# PHASE 302A — Virtual Peer Operator Server Lab Foundation

## Tujuan
Membuat laboratorium virtual yang berperilaku seperti:

`HP A nyata -> server operator -> peer virtual B`

agar fungsi dasar GhalbitNet dapat diuji tanpa terus bergantung pada tebakan atau hanya satu jenis logcat.

## Fondasi yang dibangun

### 1. FakeOperatorServer diperluas
- presence authoritative (`registerDevice`, `heartbeat`, `getPresence`)
- pending queue pesan dengan `MessageEnvelope`
- dedup `messageId`
- receipt `delivered` dan `read` dengan urutan logis
- sesi panggilan dengan state:
  - `RINGING`
  - `ACCEPTED`
  - `REJECTED`
  - `ENDED`
  - `CONNECTED` saat tone/audio virtual mulai berjalan
- tone inbox untuk simulasi audio ping-pong

### 2. VirtualPeerB
- sync inbox
- ack delivered
- ack read
- auto reply pesan
- auto accept/reject call
- auto balas tone saat sesi call aktif

### 3. FakeGhalbitWorld
- register peer default
- online/offline presence
- jalankan sync peer virtual terhadap message/call session

## Anomali dasar yang sekarang bisa dideteksi
- duplicate `messageId` membuat queue dobel
- read receipt datang sebelum delivered
- peer offline tapi server tidak menjaga pending queue
- call lompat state tanpa `RINGING -> ACCEPTED`
- call accept tanpa peer responder
- tone/audio test tidak punya lawan virtual

## Arti praktis
Ini belum menggantikan dua HP nyata, tetapi sudah memberi kita:
- operator server yang lebih realistis
- peer virtual yang bisa benar-benar merespons
- skenario dasar yang bisa diulang dan diuji otomatis

## Lanjutan yang cocok
- sambungkan hasil lab ini ke laporan evidence yang lebih kaya
- tambah simulasi media/file
- tambah kontrak session/call yang lebih dekat ke runtime app
