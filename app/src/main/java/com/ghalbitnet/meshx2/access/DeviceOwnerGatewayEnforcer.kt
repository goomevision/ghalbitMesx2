package com.ghalbitnet.meshx2.access

interface DeviceOwnerGatewayEnforcer {
    fun enforce(clientIp: String): Boolean
}
