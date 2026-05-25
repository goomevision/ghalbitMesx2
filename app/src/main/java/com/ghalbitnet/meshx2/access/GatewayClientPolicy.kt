package com.ghalbitnet.meshx2.access

object GatewayClientPolicy {

    enum class ClientStatus {
        UNKNOWN,
        UNAUTHORIZED,
        AUTHORIZED,
        TOKEN_EXPIRED
    }
}
