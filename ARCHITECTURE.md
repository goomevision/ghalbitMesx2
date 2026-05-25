# Ghalbit Mesh X2 Architecture

## Tujuan Dokumen

Dokumen ini merangkum arsitektur Android core `Ghalbit Mesh X2` dalam dua sudut:

1. **arsitektur saat ini**
2. **arsitektur target bertahap**

Dokumen ini sengaja menjaga kejujuran sistem:

- Android core saat ini sudah kaya fitur
- tetapi masih ada beberapa overlap antar modul
- target kita adalah merapikan koneksi antar lapisan, bukan membuang semua yang sudah ada

---

## 1. Gambaran Besar Sistem

`Ghalbit Mesh X2` saat ini dapat dipahami sebagai kumpulan lapisan berikut:

1. **UI / Dashboard Layer**
2. **Core Orchestration Layer**
3. **Identity & Access Layer**
4. **Discovery / Routing / Transport Layer**
5. **Chat / Community Interaction Layer**
6. **VPN / Gateway / Provider Layer**
7. **Usage / Economy / Wallet Layer**
8. **Persistence / Sync Layer**

---

## 2. Arsitektur Saat Ini

## 2.1 UI Layer

Komponen utama:

- `MainActivity`
- `MeshEconomyActivity`
- `WalletActivity`
- `UsageHistoryActivity`
- `InternetSharingSettingsActivity`
- `UnauthorizedClientsActivity`
- `HotspotBlocklistAssistantActivity`
- `CommunitySessionActivity`
- activity chat/call/settings lainnya

Peran saat ini:

- menampilkan dashboard
- menerima input user
- membuka layar fitur
- beberapa activity juga masih mengandung logika bisnis cukup tebal

Catatan penting:

- `MainActivity` saat ini masih terlalu berat dan belum murni controller UI.

## 2.2 Core Orchestration Layer

Komponen:

- `core/manager/GhalbitCoreManager`
- `core/runtime/MeshStartupManager`
- beberapa runtime helper di `core/runtime`

Peran saat ini:

- helper / façade untuk fitur future:
  - QoS
  - AI routing
  - reward
  - offline sync
  - VPN policy

Kelemahan saat ini:

- belum menjadi pusat koordinasi nyata untuk startup dan lifecycle Android core

Target:

- menjadi titik masuk orkestrasi bersama untuk manager lain
- startup mesh bertahap sudah mulai dipindahkan keluar dari `MainActivity`

## 2.3 Identity & Access Layer

Komponen yang sudah ada:

- `GlobalMeshIdentityManager`
- `KeyStoreManager`
- `NodeIdentityManager`
- `AccessHandshakeManager`
- `AccessTokenManager`
- `PeerAuthRegistry`
- `NetworkAccessPolicy`
- `UnauthorizedDeviceDetector`

Peran:

- membangun identity berbasis public key
- membangun/mengecek `globalId`
- handshake `HELLO_AUTH`
- penerbitan/reuse `ACCESS_TOKEN`
- penilaian authorized vs unauthorized

Masalah saat ini:

- belum semua modul memakai identity utama yang sama secara konsisten

Target:

- satu model identitas lintas modul: `GhalbitIdentityRecord`

## 2.4 Discovery / Routing / Transport Layer

Komponen:

- `UdpDiscovery`
- `DiscoveryManager`
- `PeerDiscoveryHandler`
- `MeshSocketServer`
- `MeshSocketClient`
- `IncomingPacketHandler`
- `OutboundMeshActionHandler`
- `ReliablePacketSender`
- `AckTracker`
- registry routing/mesh terkait

Peran:

- discovery peer lokal
- heartbeat
- pertukaran packet antar node
- ACK dan resend dasar

Masalah saat ini:

- sebagian orchestration masih dilakukan langsung dari `MainActivity`
- kontrak packet belum dirangkum sebagai satu envelope baku lintas modul
- dispatch packet masuk mulai dipindahkan keluar dari `MainActivity`
- handling discovered peer mulai dipindahkan keluar dari `MainActivity`
- outbound action UI mulai dipindahkan keluar dari `MainActivity`

Target:

- packet contract tunggal
- relay cukup membaca header
- routing path dan retry lebih seragam

## 2.5 Chat Layer

Komponen:

- activity chat / contact
- receiver messaging
- contact directory global

Peran:

- komunikasi antar user
- penyimpanan kontak
- route fallback dasar

Masalah saat ini:

- chat belum sepenuhnya digerakkan oleh `globalId` + `ConnectionResolver`

Target:

- route resolver yang memilih:
  - last working route
  - local
  - mesh
  - relay
  - server
  - offline queue

## 2.6 VPN / Gateway / Provider Layer

Komponen:

- `MeshVpnService`
- `GhalbitVpnService`
- `VpnStatusProvider`
- `VpnStatusSnapshot`
- `PacketRouter`
- `UsageMeter`
- `UsageDownloadMonitor`
- policy/gateway/controller terkait
- `CaptivePortalServer`
- `LocalProxyServer`
- `HotspotGuardManager`
- hotspot scanner / notifier / provider protection

Peran:

- monitoring usage
- packet routing internal
- read-model status VPN terpadu untuk UI/system status
- runtime snapshot kini juga membawa `activeGatewayName` dan `packetsForwardedOut`
- portal komunitas lokal
- proxy gateway lokal
- deteksi perangkat hotspot asing
- orchestration hotspot guard dipindahkan keluar dari `MainActivity`

Masalah saat ini:

- ada overlap konsep antara:
  - VPN internal perangkat
  - gateway/provider enforcement
  - usage monitoring
- dua service VPN perlu diperjelas hubungan dan perannya

Target:

- batas jelas:
  - VPN device
  - monitoring
  - gateway/provider assist
  - captive portal/proxy

## 2.7 Usage / Economy / Wallet Layer

Komponen:

- `UsageMeter`
- `UsageRepository`
- `UsageDao`
- `UsageSessionEntity`
- `UsageDeltaEntity`
- `UsageHistoryActivity`
- `TokenManager`
- `WalletActivity`
- `VoucherQrManager`
- `RewardEngine`
- pricing/cost preview classes

Peran:

- menghitung usage lokal
- menyimpan usage ke Room
- menampilkan riwayat
- menghitung preview biaya GBHT
- mengelola saldo wallet lokal
- issue/redeem voucher

Masalah saat ini:

- reward/session proof belum menjadi kontrak data yang kuat
- perlu audit lebih dalam untuk sinkronisasi saldo, voucher, dan reward

## 2.8 Persistence / Sync Layer

Komponen:

- Room database usage
- token database
- shared preferences untuk beberapa registry ringan
- Firebase sync managers

Peran:

- menyimpan state lokal
- menyimpan transaksi/wallet
- menyimpan usage
- enqueue sinkronisasi

Masalah saat ini:

- beberapa state masih tersebar antara DB, prefs, dan cache manager

Target:

- state inti punya sumber kebenaran lebih jelas

---

## 3. Arsitektur Target

## 3.1 Prinsip Inti

1. `MainActivity` hanya controller UI
2. identity tunggal untuk seluruh modul
3. network packet punya header standar
4. wallet / voucher / reward mengacu ke identity dan session yang sama
5. monitoring / VPN / gateway dipisahkan secara tanggung jawab

## 3.2 Manager Target

Manager target yang ingin dipakai atau diperkuat:

- `GhalbitCoreManager`
- `DiscoveryManager`
- `RoutingManager`
- `NetworkManager`
- `WalletManager`
- `AccessManager`
- `EconomyManager`
- `VpnManager`

Catatan:

- tidak semua manager ini harus dibuat dari nol sekaligus
- sebagian bisa berupa façade/adaptor di atas komponen lama

## 3.3 Identity Contract Target

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

- semua modul wajib mengacu ke `globalId/nodeId`
- IP tidak boleh dipakai sebagai identitas permanen

## 3.4 Packet Contract Target

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
- `signature`

Tambahan:

- ACK
- retry
- timeout
- reconnect
- route cache
- duplicate detection
- offline queue

## 3.5 Session/Proof Contract Target

Untuk usage + reward + governance:

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

Status:

- `pending`
- `verified`
- `rejected`
- `settled`

---

## 4. Batas Jujur Sistem Saat Ini

Sudah ada fondasi untuk:

- identity/auth
- hotspot unauthorized detection
- captive portal lokal
- local proxy
- usage monitoring
- usage database
- wallet lokal
- voucher issue/redeem
- provider governance

Belum boleh diklaim penuh:

- full hotspot blocking otomatis di stock Android
- full VPN enforcement untuk semua klien tethering
- blockchain settlement penuh
- reward trustless final

---

## 5. Arah Refactor yang Aman

Urutan refactor yang paling aman:

1. stabilkan build dan service mapping
2. tipiskan `MainActivity`
3. satukan identity
4. audit flow voucher/wallet
5. rapikan packet/routing contract
6. rapikan batas VPN/gateway/usage
7. baru matangkan reward proof dan sync
