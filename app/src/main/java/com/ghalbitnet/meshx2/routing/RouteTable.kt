package com.ghalbitnet.meshx2.routing

import java.util.concurrent.ConcurrentHashMap

object RouteTable {

    private val routes =
        ConcurrentHashMap<String, MeshRoute>()

    fun updateRoute(
        destination: String,
        nextHop: String,
        hopCount: Int
    ) {

        val current =
            routes[destination]

        if (
            current == null ||
            hopCount < current.hopCount
        ) {
            routes[destination] =
                MeshRoute(
                    destination,
                    nextHop,
                    hopCount
                )
        }
    }

    fun getRoute(
        destination: String
    ): MeshRoute? {
        return routes[destination]
    }

    fun allRoutes(): List<MeshRoute> {
        return routes.values.toList()
    }

    fun clearExpired(
        maxAgeMs: Long = 120000L
    ) {
        val now =
            System.currentTimeMillis()

        routes.entries.removeIf {
            now - it.value.updatedAt > maxAgeMs
        }
    }

    fun report(): String {

        return buildString {

            appendLine("ROUTE TABLE")
            appendLine("===================")

            routes.values.forEach {

                appendLine(
                    "${it.destination} -> ${it.nextHop} (${it.hopCount} hop)"
                )
            }
        }
    }
}
