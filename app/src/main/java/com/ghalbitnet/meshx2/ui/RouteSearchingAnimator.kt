package com.ghalbitnet.meshx2.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RouteSearchingAnimator(
    private val scope: CoroutineScope,
    private val onText: (String) -> Unit
) {
    private var job: Job? = null
    private var baseText: String = ""

    fun start(text: String) {
        baseText = text
        if (job != null) return
        job =
            scope.launch(Dispatchers.Main) {
                var frame = 0
                while (true) {
                    val dots = when (frame % 4) {
                        1 -> "."
                        2 -> ".."
                        3 -> "..."
                        else -> ""
                    }
                    onText(baseText + dots)
                    frame += 1
                    delay(550L)
                }
            }
    }

    fun update(text: String) {
        baseText = text
    }

    fun stop(finalText: String? = null) {
        job?.cancel()
        job = null
        finalText?.let(onText)
    }
}
