package com.ghalbitnet.meshx2.core.routing

object RoutingTable {

    private val routes =
        mutableMapOf<String,String>()

    fun updateRoute(
        destination: String,
        nextHop: String
    ) {

        routes[destination] = nextHop
    }

    fun getRoute(
        destination: String
    ): String? {

        return routes[destination]
    }

    fun allRoutes(): Map<String,String> {

        return routes
    }
}