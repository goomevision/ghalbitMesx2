package com.ghalbitnet.meshx2.simulation

data class FakeNetworkCondition(
    var internetAvailable: Boolean = true,
    var relayAvailable: Boolean = true,
    var serverSlow: Boolean = false,
    var serverErrorCode: Int? = null
)

