package com.ghalbitnet.meshx2.access

interface RootGatewayEnforcer {
    fun enforce(clientIp: String): Boolean
}
