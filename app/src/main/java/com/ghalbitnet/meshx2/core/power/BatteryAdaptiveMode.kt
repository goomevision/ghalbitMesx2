package com.ghalbitnet.meshx2.core.power

object BatteryAdaptiveMode {

    fun getScanDelay(
        battery: Int
    ): Long {

        return when {

            battery > 70 -> 5000

            battery > 40 -> 12000

            battery > 20 -> 20000

            else -> 30000
        }
    }
}