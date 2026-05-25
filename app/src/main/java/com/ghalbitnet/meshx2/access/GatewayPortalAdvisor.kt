package com.ghalbitnet.meshx2.access

object GatewayPortalAdvisor {

    fun unauthorizedInstruction(gatewayIp: String): String {
        return "Untuk memakai internet komunitas, install GhalbitMesh X2 atau gunakan konfigurasi proxy Ghalbit. Buka http://$gatewayIp:${CaptivePortalServer.PORT} dan gunakan proxy $gatewayIp:${LocalProxyServer.PORT}."
    }
}
