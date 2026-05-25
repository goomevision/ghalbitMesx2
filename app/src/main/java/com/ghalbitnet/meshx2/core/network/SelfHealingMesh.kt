package com.ghalbitnet.meshx2.core.network

import kotlinx.coroutines.*

object SelfHealingMesh {

    fun start() {

        CoroutineScope(Dispatchers.IO).launch {

            while (true) {

                delay(10000)

                checkMesh()
            }
        }
    }

    private fun checkMesh() {

        println("Checking mesh stability...")
    }
}