package com.ghalbitnet.meshx2.identity

object SimulationEventLogger {

    private const val MAX_EVENTS = 80
    private val events =
        ArrayDeque<SimulationEvent>()

    fun record(
        event: SimulationEvent
    ) {
        if (events.size >= MAX_EVENTS) {
            events.removeFirst()
        }
        events.addLast(event)
    }

    fun recent(
        limit: Int = 20
    ): List<SimulationEvent> =
        events.takeLast(limit)

    fun report(
        limit: Int = 20
    ): String {
        val items = recent(limit)
        if (items.isEmpty()) {
            return "No simulation events yet."
        }
        return buildString {
            appendLine("SIMULATION EVENT LOG")
            appendLine("======================")
            items.forEach { event ->
                appendLine("${event.type.name} | ${event.label} | ${event.detail}")
            }
        }.trim()
    }
}
