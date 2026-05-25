package com.ghalbitnet.meshx2.packet

data class MeshPacket(

    val id: String,

    val fromNode: String,

    val toNode: String,

    val payload: String,

    val timestamp: Long,

    val ttl: Int = 5
)