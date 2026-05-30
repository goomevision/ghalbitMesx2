package com.ghalbitnet.meshx2.identity

object IdentityDisplayFormatter {

    fun primaryLabel(
        canonicalDisplayName: String? = null,
        walletAddress: String? = null,
        globalId: String? = null,
        publicKey: String? = null,
        legacyName: String? = null,
        ipAddress: String? = null
    ): String {
        canonicalDisplayName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        walletAddress?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return "Wallet ${shortValue(it, 6, 4)}"
        }
        globalId?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return "Node ${shortValue(it, 8, 4)}"
        }
        publicKey?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return "Key ${shortValue(it, 8, 4)}"
        }
        legacyName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        ipAddress?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return "Unknown peer"
    }

    fun legacyDebugLabel(
        primaryLabel: String,
        legacyName: String? = null,
        ipAddress: String? = null
    ): String? {
        val legacy = legacyName?.trim().orEmpty()
        val ip = ipAddress?.trim().orEmpty()
        return when {
            legacy.isNotEmpty() && legacy != primaryLabel && ip.isNotEmpty() -> "$legacy | $ip"
            legacy.isNotEmpty() && legacy != primaryLabel -> legacy
            ip.isNotEmpty() && ip != primaryLabel -> ip
            else -> null
        }
    }

    fun secondaryLabel(
        primaryLabel: String,
        legacyName: String? = null,
        walletAddress: String? = null,
        globalId: String? = null,
        publicKey: String? = null,
        ipAddress: String? = null
    ): String? {
        val parts = linkedSetOf<String>()

        legacyName?.trim()?.takeIf { it.isNotEmpty() && it != primaryLabel }?.let {
            parts.add("Chat ID: $it")
        }
        walletAddress?.trim()?.takeIf { it.isNotEmpty() }?.let {
            parts.add("Wallet ${shortValue(it, 6, 4)}")
        }
        globalId?.trim()?.takeIf { it.isNotEmpty() }?.let {
            parts.add("Node ${shortValue(it, 8, 4)}")
        }
        publicKey?.trim()?.takeIf { it.isNotEmpty() }?.let {
            parts.add("Key ${shortValue(it, 8, 4)}")
        }
        ipAddress?.trim()?.takeIf { it.isNotEmpty() && it != primaryLabel }?.let {
            parts.add("IP $it")
        }

        return parts.takeIf { it.isNotEmpty() }?.joinToString(" | ")
    }

    private fun shortValue(
        value: String,
        prefix: Int,
        suffix: Int
    ): String {
        if (value.length <= prefix + suffix + 1) {
            return value
        }
        return "${value.take(prefix)}...${value.takeLast(suffix)}"
    }
}
