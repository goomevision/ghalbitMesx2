# Ghalbit Mesh System Blueprint V1

Dokumen ini merangkum fungsi utama aplikasi, fungsi server control-plane, dan posisi implementasi saat ini.

## Prinsip utama

- App bukan hanya VPN.
- App adalah node, router, wallet, gateway, validator, miner jasa, dan alat komunikasi mandiri.
- Firebase / server dipakai sebagai control-plane.
- Trafik utama tetap berjalan antar node dan gateway.
- Jika server mati, mesh lokal tetap hidup.

## Fungsi utama aplikasi

### 1. VPN Engine

Target:

- mengambil trafik internet dari HP
- menerapkan aturan akses
- memilih gateway / rute
- memutus trafik bila syarat gagal

Status saat ini:

- dasar monitor bridge dan keputusan policy sudah ada
- tunnel packet engine penuh masih tahap lanjut

Kelas terkait:

- `MeshVpnService`
- `InternetBridgePolicyManager`
- `InternetBridgeUsageMonitor`

### 2. Deteksi peran otomatis

Peran:

- Client
- Relay Node
- Gateway Internet
- Validator
- Miner

Status saat ini:

- sudah mulai aktif berbasis bukti kontribusi

Kelas terkait:

- `AutoNodeRoleManager`
- `InternetProviderReadinessManager`

### 3. Gateway internet

Target:

- mendeteksi internet aktif
- menguji kesiapan jalur keluar
- mengiklankan diri sebagai gateway bila lolos

Kelas terkait:

- `InternetGatewayRegistry`
- `InternetProviderReadinessManager`
- `InternetGatewayLoadManager`

### 4. Relay node

Target:

- meneruskan paket antar node
- mencatat bukti kontribusi relay

Kelas terkait:

- `PeerManager`
- `MultiHopRouter`
- `InternetRoutePlanner`

### 5. Client mode

Target:

- pengguna memakai internet dari jaringan Ghalbit
- data usage dan biaya tercatat

Kelas terkait:

- `InternetBridgePolicyManager`
- `InternetBridgeUsageMonitor`
- `MeshServiceLedger`

### 6. Wallet GBHT

Target:

- saldo
- riwayat transaksi
- voucher
- reward
- biaya internet

Kelas terkait:

- `TokenManager`
- `WalletActivity`
- `FirebaseEconomySyncManager`

### 7. Reward engine

Target:

- bayar kontribusi nyata
- gateway
- relay
- validator
- builder
- reserve / burn

Kelas terkait:

- `RewardEngine`
- `MeshEconomySettlementEngine`
- `MeshServiceFormula`

### 8. Trust score

Target:

- skala 0 - 100
- naik karena stabil, jujur, aktif
- turun karena spam, fake node, manipulasi

Kelas terkait:

- `PeerReputationManager`
- `FirebaseRemoteSyncManager`
- `firebase-admin-tools/report-abuse.js`

### 9. Mesh lokal

Target:

- chat lokal
- file lokal
- voice lokal
- discovery lokal

Kelas terkait:

- `DiscoveryManager`
- `SecureChatManager`
- `FileTransferManager`

### 10. Server sync

Target:

- discovery
- policy
- trust
- blockchain sync
- registry peserta

Kelas terkait:

- `FirebaseRemoteSyncManager`
- `FirebaseEconomySyncManager`

## Tugas server control-plane

Server bertugas sebagai:

- Bootstrap Server
- Registry Node
- Policy Controller
- Gateway Directory
- Trust & Reputation Engine
- Blockchain Sync Helper
- Emergency Recovery Helper

Server bukan:

- jalur utama internet
- pusat VPN trafik
- pusat chat

## Koleksi Firebase yang dipakai

- `presence/{globalId}`
- `providerProfiles/{globalId}`
- `wallets/{globalId}`
- `walletTransactions/{autoId}`
- `peerPolicies/{globalId}`
- `bridgePolicies/default`
- `economyPolicies/default`
- `nodeRegistry/{globalId}`
- `gatewayDirectory/{globalId}`
- `bootstrapState/default`
- `networkState/default`
- `blockchainSync/default`
- `recoverySnapshots/{autoId}`
- `trustReports/{autoId}`
- `abuseReports/{autoId}`

## Urutan kerja aplikasi saat dibuka

1. buat Node ID
2. siapkan public/private key
3. cek onboarding dan persetujuan
4. cek hotspot / jalur lokal
5. cek internet
6. sinkron ke Firebase
7. tarik policy dan wallet
8. tentukan peran otomatis
9. mulai mesh lokal

## Urutan kerja saat VPN/bridge aktif

1. semua keputusan akses memakai `GhalbitMesh X2`
2. app cek identitas peserta
3. app cek persetujuan kontribusi
4. app cek online / presence
5. app cek wallet / saldo
6. app cek policy
7. app pilih gateway / rute
8. app catat usage dan reward

## Status implementasi ringkas

### Sudah cukup kuat

- wallet GBHT
- voucher
- policy sync
- provider readiness
- gateway selection
- route cooperation
- failover dasar
- settlement dasar
- local mesh features
- server control-plane tools

### Masih parsial

- VPN packet engine penuh
- packet drop / forward level tunnel
- handshake akses antar aplikasi
- trust proof anti-manipulasi penuh
- battery optimization flow lengkap

### Masih tahap lanjut

- tun2socks / packet forwarder nyata
- STUN/TURN traversal
- packet enforcement untuk client tethered luar aplikasi

## Arah kerja berikutnya

1. handshake akses antar aplikasi
2. packet enforcement penuh di VPN engine
3. bootstrap peer dari `bootstrapState/default`
4. trust score server masuk ke routing
5. gateway directory server masuk ke pemilihan jalur
