package com.ghalbitnet.meshx2.identity

import android.util.Log
import com.ghalbitnet.meshx2.model.MeshPacket
import java.security.MessageDigest

object SelfIdentityProtector {
    private const val TAG = "GHALBIT-ROUTE"

    data class IdentityContext(
        val selfNodeId: String = "",
        val selfPublicKeyHash: String? = null,
        val selfDeviceInstanceId: String? = null
    )

    data class Decision(
        val selfLoop: Boolean,
        val reason: String
    )

    fun hashPublicKey(publicKey: String?): String? {
        if (publicKey.isNullOrBlank()) return null
        val bytes = MessageDigest.getInstance("SHA-256").digest(publicKey.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun evaluate(
        packet: MeshPacket,
        packetOrigin: String?,
        packetPublicKeyHash: String?,
        packetDeviceInstanceId: String?,
        identityContext: IdentityContext
    ): Decision {
        if (identityContext.selfNodeId.isNotBlank() && packet.source == identityContext.selfNodeId) {
            return Decision(true, "selfNodeId")
        }
        if (!identityContext.selfPublicKeyHash.isNullOrBlank() &&
            !packetPublicKeyHash.isNullOrBlank() &&
            identityContext.selfPublicKeyHash == packetPublicKeyHash
        ) {
            return Decision(true, "publicKeyHash")
        }
        if (!identityContext.selfDeviceInstanceId.isNullOrBlank() &&
            !packetDeviceInstanceId.isNullOrBlank() &&
            identityContext.selfDeviceInstanceId == packetDeviceInstanceId
        ) {
            return Decision(true, "deviceInstanceId")
        }
        if (identityContext.selfNodeId.isNotBlank() && packetOrigin == identityContext.selfNodeId) {
            return Decision(true, "packetOrigin")
        }
        return Decision(false, "foreign")
    }

    fun logSelfLoop(packet: MeshPacket, reason: String) {
        Log.d(TAG, "Ignored self-loop packet ${packet.packetId} source=${packet.source} reason=$reason")
    }
}
