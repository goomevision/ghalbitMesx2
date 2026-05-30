package com.ghalbitnet.meshx2.reliability

object RuntimeStressDiagnostics {

    fun indicators(
        signals: List<ReliabilitySignalSnapshot> = emptyList()
    ): List<RuntimeStressIndicator> =
        listOf(
            RuntimeStressIndicator(
                "retry storm risk",
                severityForRetry(signals),
                "derived from retry metadata and pending ACKs [derived]"
            ),
            RuntimeStressIndicator(
                "relay congestion risk",
                severityForRelay(signals),
                "derived from route requests and transfers [derived]"
            ),
            RuntimeStressIndicator(
                "delayed sync overload",
                severityForConnectivity(signals),
                "connectivity scope based estimate [estimated]"
            ),
            RuntimeStressIndicator(
                "custody backlog saturation",
                severityForRelay(signals),
                "relay backlog estimate [estimated]"
            ),
            RuntimeStressIndicator(
                "hotspot instability",
                RuntimeStressSeverity.MODERATE,
                "mesh hotspot environments can be unstable [placeholder]"
            ),
            RuntimeStressIndicator(
                "VPN lifecycle instability",
                RuntimeStressSeverity.MODERATE,
                "Android VPN lifecycle can interrupt long sessions [placeholder]"
            ),
            RuntimeStressIndicator(
                "WiFi Direct instability",
                RuntimeStressSeverity.MODERATE,
                "WiFi Direct links may flap under contention [placeholder]"
            ),
            RuntimeStressIndicator(
                "battery pressure",
                severityForBattery(signals),
                "battery level hint if available [observational]"
            )
        )

    fun highestSeverity(
        signals: List<ReliabilitySignalSnapshot> = emptyList()
    ): RuntimeStressSeverity =
        indicators(signals).maxByOrNull { it.severity.ordinal }?.severity
            ?: RuntimeStressSeverity.INFORMATIONAL

    fun report(
        signals: List<ReliabilitySignalSnapshot> = emptyList()
    ): String =
        buildString {
            appendLine("RUNTIME STRESS")
            appendLine("======================")
            appendLine("Highest severity: ${highestSeverity(signals).name.lowercase()}")
            indicators(signals).forEach { indicator ->
                appendLine("- ${indicator.name}: ${indicator.severity.name.lowercase()} (${indicator.detail})")
            }
        }.trimEnd()

    private fun severityForRetry(
        signals: List<ReliabilitySignalSnapshot>
    ): RuntimeStressSeverity {
        val retry = intSignal(signals, ReliabilitySignalType.RETRY_METADATA_COUNT)
        val pendingAck = intSignal(signals, ReliabilitySignalType.ACK_PENDING_COUNT)
        return when {
            retry >= 8 || pendingAck >= 20 -> RuntimeStressSeverity.HIGH
            retry >= 4 || pendingAck >= 10 -> RuntimeStressSeverity.MODERATE
            retry >= 1 || pendingAck >= 1 -> RuntimeStressSeverity.LOW
            else -> RuntimeStressSeverity.INFORMATIONAL
        }
    }

    private fun severityForRelay(
        signals: List<ReliabilitySignalSnapshot>
    ): RuntimeStressSeverity {
        val routes = intSignal(signals, ReliabilitySignalType.ROUTE_REQUEST_COUNT)
        val transfers = intSignal(signals, ReliabilitySignalType.PENDING_TRANSFER_COUNT)
        return when {
            routes >= 8 || transfers >= 6 -> RuntimeStressSeverity.HIGH
            routes >= 4 || transfers >= 3 -> RuntimeStressSeverity.MODERATE
            routes >= 1 || transfers >= 1 -> RuntimeStressSeverity.LOW
            else -> RuntimeStressSeverity.INFORMATIONAL
        }
    }

    private fun severityForConnectivity(
        signals: List<ReliabilitySignalSnapshot>
    ): RuntimeStressSeverity =
        when (stringSignal(signals, ReliabilitySignalType.CONNECTIVITY_SCOPE)) {
            "offline" -> RuntimeStressSeverity.HIGH
            "local_only", "internet_only" -> RuntimeStressSeverity.MODERATE
            "internet_and_local" -> RuntimeStressSeverity.LOW
            else -> RuntimeStressSeverity.INFORMATIONAL
        }

    private fun severityForBattery(
        signals: List<ReliabilitySignalSnapshot>
    ): RuntimeStressSeverity {
        val battery = intSignal(signals, ReliabilitySignalType.BATTERY_HINT, -1)
        return when {
            battery in 0..15 -> RuntimeStressSeverity.HIGH
            battery in 16..30 -> RuntimeStressSeverity.MODERATE
            battery > 30 -> RuntimeStressSeverity.LOW
            else -> RuntimeStressSeverity.INFORMATIONAL
        }
    }

    private fun intSignal(
        signals: List<ReliabilitySignalSnapshot>,
        type: ReliabilitySignalType,
        fallback: Int = 0
    ): Int {
        val raw = signals.firstOrNull { it.type == type }?.value ?: return fallback
        return raw.substringBefore(' ').toIntOrNull() ?: fallback
    }

    private fun stringSignal(
        signals: List<ReliabilitySignalSnapshot>,
        type: ReliabilitySignalType
    ): String? = signals.firstOrNull { it.type == type }?.value
}
