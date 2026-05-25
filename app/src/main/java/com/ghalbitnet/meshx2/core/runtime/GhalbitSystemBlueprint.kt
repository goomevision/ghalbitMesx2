package com.ghalbitnet.meshx2.core.runtime

object GhalbitSystemBlueprint {

    enum class Coverage {
        READY,
        PARTIAL,
        PLANNED
    }

    enum class Domain {
        VPN,
        IDENTITY,
        ROUTING,
        ECONOMY,
        TRUST,
        LOCAL_MESH,
        SERVER_SYNC,
        DEVICE_CONTROL
    }

    data class ModuleSpec(
        val id: String,
        val title: String,
        val domain: Domain,
        val coverage: Coverage,
        val summary: String,
        val keyClasses: List<String>
    )

    data class RoleSpec(
        val id: String,
        val title: String,
        val summary: String
    )

    val nodeRoles =
        listOf(
            RoleSpec("CLIENT", "Client", "Memakai internet dan layanan mesh dari jaringan Ghalbit."),
            RoleSpec("RELAY", "Relay Node", "Meneruskan trafik dan paket antar node."),
            RoleSpec("GATEWAY", "Gateway Internet", "Membawa node lain keluar ke internet nyata."),
            RoleSpec("VALIDATOR", "Validator", "Memvalidasi aktivitas, trust, dan keputusan layanan."),
            RoleSpec("MINER", "Miner", "Menerima reward dari kontribusi jaringan nyata.")
        )

    val requiredModules =
        listOf(
            ModuleSpec(
                id = "vpn_engine",
                title = "VPN Engine",
                domain = Domain.VPN,
                coverage = Coverage.PARTIAL,
                summary = "Sudah ada service pengendali bridge dan policy gate, tetapi packet tunnel penuh masih tahap lanjut.",
                keyClasses = listOf("MeshVpnService", "InternetBridgePolicyManager")
            ),
            ModuleSpec(
                id = "packet_forwarder",
                title = "Tun2Socks / Packet Forwarder",
                domain = Domain.VPN,
                coverage = Coverage.PLANNED,
                summary = "Belum ada tunnel packet forwarder penuh untuk semua trafik Android.",
                keyClasses = emptyList()
            ),
            ModuleSpec(
                id = "auto_role",
                title = "Deteksi Peran Otomatis",
                domain = Domain.IDENTITY,
                coverage = Coverage.READY,
                summary = "Peran node dibaca dari bukti kontribusi, bukan klaim manual.",
                keyClasses = listOf("AutoNodeRoleManager", "InternetProviderReadinessManager")
            ),
            ModuleSpec(
                id = "gateway_selector",
                title = "Gateway Selector",
                domain = Domain.ROUTING,
                coverage = Coverage.READY,
                summary = "Sistem sudah memilih gateway dan rute terbaik, termasuk load balancing dasar dan failover.",
                keyClasses = listOf(
                    "InternetGatewayRegistry",
                    "InternetRoutePlanner",
                    "InternetRouteCooperationManager"
                )
            ),
            ModuleSpec(
                id = "route_manager",
                title = "Route Manager",
                domain = Domain.ROUTING,
                coverage = Coverage.READY,
                summary = "Rute aktif, rute cadangan, dan pembagian segmen gateway sudah mulai dicatat.",
                keyClasses = listOf("InternetBridgeUsageMonitor", "MeshServiceLedger")
            ),
            ModuleSpec(
                id = "wallet_manager",
                title = "Wallet GBHT",
                domain = Domain.ECONOMY,
                coverage = Coverage.READY,
                summary = "Wallet lokal, sinkron Firebase, voucher, dan transaksi sudah tersedia.",
                keyClasses = listOf("TokenManager", "WalletActivity", "FirebaseEconomySyncManager")
            ),
            ModuleSpec(
                id = "reward_engine",
                title = "Reward Engine",
                domain = Domain.ECONOMY,
                coverage = Coverage.READY,
                summary = "Reward gateway, relay, validator, builder, dan reserve sudah punya dasar settlement.",
                keyClasses = listOf("RewardEngine", "MeshEconomySettlementEngine", "MeshServiceFormula")
            ),
            ModuleSpec(
                id = "trust_manager",
                title = "Trust Manager",
                domain = Domain.TRUST,
                coverage = Coverage.PARTIAL,
                summary = "Trust score dan abuse sudah mulai dikelola, tetapi proof anti-manipulasi penuh masih berkembang.",
                keyClasses = listOf("PeerReputationManager", "FirebaseRemoteSyncManager")
            ),
            ModuleSpec(
                id = "local_mesh",
                title = "Mesh Lokal",
                domain = Domain.LOCAL_MESH,
                coverage = Coverage.READY,
                summary = "Chat, file, discovery, dan layanan lokal tetap bisa hidup tanpa internet luar.",
                keyClasses = listOf("DiscoveryManager", "SecureChatManager", "FileTransferManager")
            ),
            ModuleSpec(
                id = "server_sync",
                title = "Server Sync",
                domain = Domain.SERVER_SYNC,
                coverage = Coverage.READY,
                summary = "Firebase dipakai sebagai control plane: presence, wallet, policy, provider profile, dan registry.",
                keyClasses = listOf("FirebaseRemoteSyncManager", "FirebaseEconomySyncManager")
            ),
            ModuleSpec(
                id = "peer_manager",
                title = "Peer Manager",
                domain = Domain.ROUTING,
                coverage = Coverage.PARTIAL,
                summary = "Manajemen peer sudah ada, tetapi handshake akses antar aplikasi masih perlu diperdalam.",
                keyClasses = listOf("PeerManager", "RemotePresenceRegistry")
            ),
            ModuleSpec(
                id = "battery_handler",
                title = "Battery Optimization Handler",
                domain = Domain.DEVICE_CONTROL,
                coverage = Coverage.PARTIAL,
                summary = "Adaptasi mode hemat daya sudah ada, tetapi alur izin optimasi baterai masih perlu diperluas.",
                keyClasses = listOf("BatteryAdaptiveMode", "LightweightMeshSupervisor")
            )
        )
}
