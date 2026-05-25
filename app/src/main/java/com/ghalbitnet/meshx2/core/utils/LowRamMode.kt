package com.ghalbitnet.meshx2.core.utils

object LowRamMode {

    fun optimize() {

        Runtime.getRuntime().gc()

        println("LOW RAM OPTIMIZATION")
    }
}