package com.ghalbitnet.meshx2.access

interface OpenWrtGatewayEnforcer {
    fun enforce(clientIp: String): Boolean
}
