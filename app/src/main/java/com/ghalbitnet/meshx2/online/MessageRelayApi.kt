package com.ghalbitnet.meshx2.online

interface MessageRelayApi {
    suspend fun sendMessage(route: InternetRoute, payload: String): RelaySendResult

    suspend fun sendSignal(route: InternetRoute, payload: String): RelaySendResult

    suspend fun sendSos(route: InternetRoute, payload: String): RelaySendResult
}
