# Changelog

## 2026-05-21

### Audit & planning

- menambahkan `PLAN.md` berisi:
  - peta masalah repo
  - status build saat ini
  - prioritas stabilisasi Android core
  - urutan refactor bertahap
- menambahkan `ARCHITECTURE.md` berisi:
  - arsitektur aplikasi saat ini
  - batas modul yang sudah ada
  - target arsitektur yang lebih modular
- mendokumentasikan temuan audit awal:
  - `MainActivity` terlalu berat
  - identity belum benar-benar unified
  - ada overlap pada layer VPN/gateway
  - flow voucher/wallet sudah tampak tersambung tetapi perlu audit lanjutan
  - ada beberapa modul future/core yang perlu dipetakan ulang sebelum refactor

### Temuan teknis penting

- build file utama menggunakan Groovy Gradle:
  - `settings.gradle`
  - `build.gradle`
  - `app/build.gradle`
- service VPN yang perlu dipastikan batasnya:
  - `MeshVpnService`
  - `GhalbitVpnService`
- manager inti yang sudah ada dan akan dievaluasi untuk dijadikan pusat orkestrasi:
  - `GhalbitCoreManager`
  - `DiscoveryManager`
- modul voucher/wallet yang sudah diaudit awal:
  - `VoucherQrManager`
  - `WalletActivity`
  - `TokenManager`

### VPN service audit

- mengaudit batas tanggung jawab antara:
  - `MeshVpnService`
  - `GhalbitVpnService`
- konfirmasi bahwa service yang **terdaftar di `AndroidManifest.xml`** adalah:
  - `MeshVpnService`
- konfirmasi bahwa caller utama juga menargetkan:
  - `MeshVpnService`
  - terutama dari `VpnController`
- konfirmasi bahwa `GhalbitVpnService` saat ini berfungsi sebagai:
  - implementasi runtime utama
  - basis `VpnService` untuk packet loop, monitoring mode, usage meter, dan notification control
- menambahkan dokumentasi tanggung jawab + TODO stabilisasi langsung di:
  - `service/MeshVpnService.kt`
  - `service/GhalbitVpnService.kt`
- rekomendasi audit:
  - `MeshVpnService` dipertahankan sementara sebagai façade publik/manifest entry-point
  - `GhalbitVpnService` dipertahankan sebagai basis implementasi runtime
  - refactor besar ditunda sampai semua caller, state manager, dan policy manager selesai dipetakan

### VPN status/state audit

- mengaudit semua pembaca/penulis status VPN utama:
  - `VpnController`
  - `MeshVpnService`
  - `GhalbitVpnService`
  - `VpnRuntimeState`
  - `InternetBridgeStateManager`
  - `GhalbitSystemStatusManager`
  - `MeshEconomyActivity`
- konfirmasi kontrak status saat ini:
  - `MeshVpnService.isBridgeServiceActive(context)` = flag persisted aktif/nonaktif
  - `VpnRuntimeState` = detail runtime in-memory untuk UI dan debug
  - `InternetBridgeStateManager` = read-model status bridge untuk layar ekonomi
  - `VpnController.markDesiredRunning(...)` = desired state, bukan jaminan service final
- mengidentifikasi risiko sinkronisasi:
  - UI bisa membaca snapshot runtime yang reset saat process mati
  - desired state bisa berbeda sementara dengan service state final
  - status bridge produk dan status runtime packet bukan domain yang sama
- menambahkan dokumentasi kontrak + TODO stabilisasi pada:
  - `vpn/VpnRuntimeState.kt`
  - `vpn/VpnController.kt`
  - `economy/InternetBridgeStateManager.kt`

### Unified VPN status read-model

- menambahkan helper read-only tunggal:
  - `vpn/VpnStatusSnapshot.kt`
  - `vpn/VpnStatusProvider.kt`
- kontrak baru ini menyatukan pembacaan dari:
  - desired state
  - persisted service active flag
  - runtime snapshot
  - bridge state tingkat UI
- mengarahkan pembaca utama agar tidak merakit status VPN sendiri-sendiri:
  - `MeshEconomyActivity`
  - `GhalbitSystemStatusManager`
- tidak ada perubahan lifecycle service, packet loop, TUN setup, atau controller start/stop

### VPN runtime status enrichment

- menambahkan field runtime kecil yang aman untuk status VPN:
  - `activeGatewayName: String?`
  - `packetsForwardedOut: Long`
- `packetsForwardedOut` sekarang diincrement tepat setelah `PacketRouter.forwardPacket(...)`
  dipanggil dari `GhalbitVpnService`
- `activeGatewayName` sekarang diisi dari runtime guard saat gateway aktif benar-benar
  diketahui, dan di-clear saat mode passive/light atau saat gateway hilang
- `VpnStatusProvider` sekarang membaca:
  - `gatewayName` dari `activeGatewayName`
  - `packetsOut` dari `packetsForwardedOut`

### VPN runtime freshness + cleanup

- membersihkan warning Kotlin unused variable `sessionId` di `GhalbitVpnService`
  dengan menghapus local variable yang memang tidak dipakai di packet loop
- menambahkan freshness metadata ke runtime snapshot:
  - `lastRuntimeUpdateAt`
  - `runtimeAgeMs`
- `VpnStatusProvider` sekarang bisa memberi warning ringan:
  - `VPN runtime snapshot stale`
  saat service aktif tetapi snapshot runtime sudah lebih tua dari 15 detik

### VPN runtime freshness class

- menambahkan enum read-only:
  - `RuntimeFreshness`
  - nilai: `FRESH`, `AGING`, `STALE`, `UNKNOWN`
- `VpnStatusSnapshot` sekarang membawa `runtimeFreshness`
- `VpnStatusProvider` sekarang mengklasifikasikan `runtimeAgeMs` menjadi freshness class
- warning `VPN runtime snapshot stale` tetap dipakai saat freshness = `STALE`

### MainActivity thinning - hotspot guard extraction

- memindahkan orchestration hotspot guard dari `MainActivity` ke manager baru:
  - `access/HotspotGuardManager.kt`
- logic yang dipindahkan:
  - loop guard 2 detik
  - start/stop `CaptivePortalServer`
  - start/stop `LocalProxyServer`
  - debounce scan `HotspotNetworkScanner`
  - debounce warning `UnauthorizedHotspotWarning`
  - redirect ke `HotspotVerificationActivity` saat requirement muncul
- `MainActivity` sekarang cukup:
  - memanggil `HotspotGuardManager.start(...)`
  - memanggil `HotspotGuardManager.stop(...)`
  - menerima callback warning/status sederhana

### MainActivity thinning - mesh startup orchestration

- menambahkan manager baru:
  - `core/runtime/MeshStartupManager.kt`
- orchestration start/stop mesh yang dipindahkan dari `MainActivity`:
  - `LightweightMeshSupervisor.start/stop`
  - `MeshHeartbeatTicker.start/stop`
  - start/stop `MeshForegroundService`
  - bootstrap `WireGuardMeshManager`
  - `UdpDiscovery.init/listen/stop`
  - discovery heartbeat loop
  - `MeshSocketServer.start/stop`
  - initialization `WifiDirectManager` dan `NearbyManager`
  - `MeshAutoRecovery.start/stop`
- `MainActivity` sekarang memegang callback business logic:
  - packet handling
  - secure packet handling
  - peer discovery handling
- urutan startup/stop kini dipanggil melalui `MeshStartupManager`

### MainActivity thinning - incoming packet dispatch

- menambahkan handler baru:
  - `network/IncomingPacketHandler.kt`
- dispatch packet masuk yang dipindahkan dari `MainActivity`:
  - ACK
  - CHAT
  - SOS
  - CALL_INVITE
  - decrypt payload
  - local broadcast `NEW_MESH_PACKET`
  - secure packet callback handling
- `MainActivity` sekarang hanya menerima callback UI/status melalui:
  - `IncomingPacketListener`
- `sendSos()` tetap berada di `MainActivity` karena itu masih alur outbound UI

### MainActivity thinning - peer discovery handling

- menambahkan handler baru:
  - `discovery/PeerDiscoveryHandler.kt`
- logic yang dipindahkan dari `MainActivity`:
  - update peer key/address hasil discovery
  - register peer ke `PeerAddressRegistry`
  - `DiscoveryManager.addNode(...)`
  - `NodeStatusManager.upsertNode(...)`
  - `RouteDiscovery.rememberDirectRoute(...)`
  - warning peer key changed
- `MainActivity` sekarang hanya menerima callback UI/status melalui:
  - `PeerDiscoveryListener`
- menambahkan catatan eksplisit:
  - `TODO unified identity: resolve discovered peer by globalId, not IP/display name.`

### MainActivity thinning - outbound mesh actions

- menambahkan handler baru:
  - `network/OutboundMeshActionHandler.kt`
- memindahkan outbound action yang masih tersisa di `MainActivity`:
  - `sendSos()` sekarang hanya memanggil handler
- `MainActivity` sekarang menerima callback UI/status melalui:
  - `OutboundMeshActionListener`
- menambahkan catatan eksplisit:
  - `TODO unified identity: outbound action should resolve destination by globalId.`

### Belum diubah pada tahap ini

- belum ada refactor besar kode produksi
- belum ada penghapusan modul
- belum ada perubahan arsitektur runtime
