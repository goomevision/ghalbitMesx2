# Ghalbit Mesh X2 Stabilization Plan

## Tujuan Dokumen

Dokumen ini menjadi peta kerja untuk mematangkan Android core `Ghalbit Mesh X2` tanpa melompat ke ESP32 atau menambah fitur besar baru. Fokus utama:

1. build stabil
2. arsitektur inti lebih modular
3. identitas node konsisten
4. alur chat / routing / VPN / access tidak saling tumpang tindih
5. flow ekonomi dan voucher QR benar-benar tersambung

---

## Status Audit Awal

### 1. Build system

Temuan awal:

- project memakai **Groovy Gradle**, bukan Kotlin DSL:
  - `settings.gradle`
  - `build.gradle`
  - `app/build.gradle`
- versi utama saat ini:
  - Android Gradle Plugin `8.1.0`
  - Kotlin `1.9.22`
  - compileSdk `34`
  - targetSdk `34`
  - minSdk `26`
  - Java/Kotlin target `17`
- dependensi utama terlihat konsisten dan build modern Android masih cocok.

Kondisi saat audit:

- `.\gradlew.bat assembleDebug --no-daemon` sudah pernah berhasil pada state repo saat ini.
- `.\gradlew.bat installDebug` juga sudah pernah berhasil pada state repo saat ini.

Kesimpulan:

- fokus build saat ini bukan "project gagal compile total", tetapi **stabilitas jangka pendek dan kebersihan struktur**.

### 2. MainActivity terlalu berat

Temuan:

- `MainActivity.kt` memiliki sekitar **1112 line**.
- `MainActivity` masih memegang logika terlalu banyak:
  - identity bootstrap
  - startup mesh runtime
  - socket server
  - UDP discovery
  - heartbeat
  - packet receive/process
  - hotspot guard
  - captive portal/proxy start-stop
  - beberapa bagian UI refresh dan balance refresh

Kesimpulan:

- `MainActivity` belum berperan sebagai dashboard/controller tipis.
- refactor prioritas tinggi adalah **memindahkan orchestration dan worker logic keluar dari activity**, tetapi dilakukan bertahap.

### 3. Manager inti sudah ada, tetapi belum jadi pusat kendali

Temuan:

- sudah ada `core/manager/GhalbitCoreManager.kt`
- namun isi saat ini masih lebih mirip helper/future switchboard, belum menjadi coordinator utama Android core.
- sudah ada `DiscoveryManager.kt`, tetapi `MainActivity` masih mengendalikan banyak alur discovery/socket secara langsung.

Kesimpulan:

- kita tidak perlu mengganti arsitektur total dari nol.
- strategi yang lebih aman adalah:
  - **memperkuat manager yang sudah ada**
  - lalu secara bertahap memindahkan pemanggilan dari `MainActivity`

### 4. Identity belum benar-benar unified

Temuan:

- `MainActivity` masih menghasilkan `myPeerId` pendek dari hash public key.
- modul lain juga memakai `GlobalMeshIdentityManager.buildGlobalId(...)`.
- beberapa area masih memakai campuran:
  - `peerId`
  - `globalId`
  - `peerName`
  - IP lokal
- wallet sudah relatif dekat ke `globalId`, tetapi belum ada model tunggal lintas modul.

Kesimpulan:

- perlu satu model identitas bersama untuk seluruh app:
  - chat
  - routing
  - discovery
  - wallet
  - reward
  - access
  - server sync

### 5. VPN / gateway layer overlap

Temuan:

- ada dua service terkait VPN:
  - `service/MeshVpnService.kt`
  - `service/GhalbitVpnService.kt`
- `AndroidManifest.xml` mendaftarkan `MeshVpnService`.
- banyak komponen VPN/gateway sudah berkembang:
  - monitoring passive/light
  - packet router
  - gateway/proxy/captive portal
  - usage meter

Kesimpulan:

- perlu memperjelas batas:
  - mana service VPN utama
  - mana service monitoring
  - mana layer gateway/provider
- jangan sampai ada dua jalur service yang mengerjakan hal mirip tanpa kontrak yang jelas.

### 6. Flow voucher QR terlihat sudah ada, tetapi perlu audit sambungan saldo

Temuan:

- `VoucherQrManager.extractVoucherCode()` sudah membersihkan prefix `GHBTV:`
- `WalletActivity` saat scan QR:
  - mencoba voucher flow lebih dulu
  - jika kode voucher valid, memanggil `redeemVoucher(voucherCode)`
- `VoucherQrManager.redeemVoucher(...)`:
  - validasi voucher
  - memanggil `TokenManager.recordWalletCredit(...)`
  - menandai voucher redeemed

Kesimpulan:

- flow dasar scan -> redeem -> credit saldo **secara kode tampak sudah tersambung**
- tetapi tetap perlu audit lanjutan untuk memastikan:
  - saldo UI benar-benar refresh konsisten
  - remote sync tidak mengganggu saldo lokal
  - prefix / payload QR lain tidak bentrok

### 7. Reward/Economy masih campuran core + future

Temuan:

- ada `token/RewardEngine.kt`
- ada juga `future/reward/RewardEngine.kt`
- usage local DB dan cost preview GBHT sudah ada
- tetapi model reward proof/session formal belum terlihat sebagai kontrak tunggal

Kesimpulan:

- jangan menambah logic reward besar dulu
- rapikan dulu kontrak data:
  - session proof
  - pending / verified / rejected / settled

### 8. Repo hygiene

Temuan:

- ada folder backup / artefak yang bisa membingungkan maintainability, misalnya:
  - `codex_backup_20260515_203713`
- ada cukup banyak script utilitas di root.

Kesimpulan:

- belum perlu menghapus agresif
- tapi perlu didata mana yang:
  - aktif
  - cadangan
  - eksperimental

---

## Masalah Prioritas

### Prioritas A — Build Stability

Tujuan:

- project tetap bisa dibuka dan di-build di Android Studio tanpa error
- tidak ada konflik service, import, resource, atau class duplikat yang mengganggu compile

Daftar cek:

- cek service VPN mana yang canonical
- cek activity/service yang terdaftar di manifest tetapi tidak benar-benar dipakai
- cek dependency overlap yang rawan drift
- cek class duplikat:
  - reward engine
  - VPN service
  - helper network yang mirip fungsi

### Prioritas B — MainActivity Decomposition

Tujuan:

- `MainActivity` hanya jadi UI controller/dashboard

Langkah:

1. petakan blok logika besar di `MainActivity`
2. pindahkan startup runtime ke manager/core coordinator
3. pindahkan discovery/socket orchestration ke manager
4. pindahkan hotspot/provider protection scheduler keluar dari activity
5. sisakan activity untuk:
   - binding tombol
   - observasi state
   - render UI
   - navigation

### Prioritas C — Unified Identity

Tujuan:

- satu identitas utama lintas modul

Model target:

- `GhalbitIdentityRecord`
  - `globalId`
  - `publicKey`
  - `walletAddress`
  - `displayName`
  - `localIp`
  - `localPort`
  - `lastSeenLocal`
  - `lastSeenRemote`
  - `relayCapable`
  - `gatewayCapable`
  - `trustScore`

Aturan:

- `globalId/nodeId` adalah identitas utama
- IP hanya alamat sementara
- `peerName` hanya label tampilan
- `callId` hanya sesi
- wallet harus terikat ke identity utama

### Prioritas D — Network Contract

Tujuan:

- semua packet memakai header standar

Header target:

- `packetId`
- `type`
- `sourceId`
- `destinationId`
- `timestamp`
- `ttl`
- `priority`
- `routeHint`
- `payload`
- `signature` bila tersedia

Tambahan yang harus dipastikan:

- ACK
- retry
- timeout
- reconnect
- route cache
- offline queue
- duplicate detection

### Prioritas E — Chat Routing

Tujuan:

- chat tidak menunggu terlalu lama dan tidak bergantung langsung pada IP

Konsep target:

- `ConnectionResolver`
  - last working route
  - local route
  - mesh route
  - relay route
  - server route
  - offline queue

### Prioritas F — VPN / Gateway Separation

Tujuan:

- VPN stabil, usage tercatat, UI tidak tersumbat

Pemisahan yang diinginkan:

- internal device VPN
- gateway/provider control
- usage meter
- internet bridge

Catatan:

- saat ini mode monitoring pasif/light sudah banyak berkembang, jadi prioritasnya adalah **memperjelas kontrak layer**, bukan membangun VPN besar baru.

### Prioritas G — Voucher / QR / Wallet Integrity

Tujuan:

- scan QR benar-benar memicu redeem dan update saldo

Audit lanjutan wajib:

- `VoucherQrManager`
- `WalletActivity`
- `TokenManager`
- `RewardEngine`

Poin khusus:

- prefix voucher harus dibersihkan konsisten
- scan QR tidak boleh berhenti di parsing payload
- saldo UI harus update setelah redeem valid

### Prioritas H — Reward Proof Data Model

Tujuan:

- data kontribusi/reward tidak mudah dimanipulasi

Konsep target:

- `sessionId`
- `userId`
- `providerId`
- `relayIds`
- `bytesIn`
- `bytesOut`
- `startedAt`
- `endedAt`
- `userSignature`
- `providerSignature`
- `relayProofs`

Status reward:

- `pending`
- `verified`
- `rejected`
- `settled`

### Prioritas I — Offline State Discipline

Tujuan:

- local pending tidak dianggap final global

Status minimum:

- `localPending`
- `synced`
- `conflict`
- `settled`

---

## Strategi Refactor Bertahap

### Fase 1 — Audit compile-safe

Output:

- pastikan build tetap hijau
- petakan class overlap
- petakan manager yang sudah ada dan yang masih placeholder

### Fase 2 — Kurangi beban MainActivity

Output:

- core startup dipindah ke orchestrator/manager
- activity lebih tipis

### Fase 3 — Satukan identity

Output:

- semua modul memakai `globalId/nodeId` sebagai anchor

### Fase 4 — Audit flow voucher / wallet

Output:

- scan QR -> redeem -> update saldo tervalidasi

### Fase 5 — Rapikan network contract

Output:

- header packet lebih seragam
- chat/routing tidak overlap liar

### Fase 6 — Rapikan VPN/gateway boundary

Output:

- mode monitoring tetap stabil
- service dan controller tidak tumpang tindih

---

## TODO Teknis yang Perlu Diverifikasi

1. Apakah `MeshVpnService` dan `GhalbitVpnService` memang dua service berbeda yang masih dibutuhkan, atau salah satunya sudah menjadi legacy path.
2. Apakah `GhalbitCoreManager` akan diperluas menjadi coordinator nyata, atau cukup menjadi façade yang memanggil manager lain.
3. Apakah `RewardEngine` di `token/` dan `future/reward/` punya domain berbeda atau duplikasi arah.
4. Apakah semua data chat/contact saat ini bisa dipetakan ke `globalId` tanpa migrasi besar.
5. Apakah registry peer/address/auth saat ini sudah cukup untuk menjadi sumber tunggal identity record.

---

## Deliverable Tahap Berikutnya

Setelah `PLAN.md` ini:

1. buat `ARCHITECTURE.md`
2. audit terarah:
   - `MainActivity`
   - `GhalbitCoreManager`
   - `MeshVpnService` vs `GhalbitVpnService`
   - `VoucherQrManager`
   - `WalletActivity`
   - `TokenManager`
   - `RewardEngine`
3. lakukan refactor kecil yang aman compile
4. catat semua perubahan ke `CHANGELOG.md`
5. jalankan build ulang

