package com.ghalbitnet.meshx2.access

import android.content.Context
import com.ghalbitnet.meshx2.settings.HotspotVerificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object HotspotGuardManager {

    interface HotspotGuardListener {
        fun onWarning(message: String)
        fun onStatus(message: String)
    }

    private var guardJob: Job? = null
    private var lastHotspotSecurityWarningAt: Long = 0L
    private var lastHotspotScanAt: Long = 0L

    fun start(
        context: Context,
        scope: CoroutineScope,
        listener: HotspotGuardListener,
        onVerificationRequired: (HotspotVerificationManager.Requirement) -> Unit
    ) {
        stop(context)
        guardJob =
            scope.launch(Dispatchers.Main) {
                while (true) {
                    delay(2_000L)
                    if (CaptivePortalPolicy.shouldRun(context)) {
                        CaptivePortalServer.start(context)
                        LocalProxyServer.start(context)
                        val now = System.currentTimeMillis()
                        if (now - lastHotspotScanAt >= 20_000L) {
                            lastHotspotScanAt = now
                            launch(Dispatchers.IO) {
                                HotspotNetworkScanner.scan(context)
                            }
                        }
                    } else {
                        CaptivePortalServer.stop()
                        LocalProxyServer.stop()
                    }

                    val warning = UnauthorizedHotspotWarning.evaluate(context)
                    if (warning.shouldWarn) {
                        val now = System.currentTimeMillis()
                        if (now - lastHotspotSecurityWarningAt >= 15_000L) {
                            lastHotspotSecurityWarningAt = now
                            listener.onWarning(warning.message)
                        }
                    }

                    val requirement = HotspotVerificationManager.currentRequirement(context) ?: continue
                    if (requirement == HotspotVerificationManager.Requirement.HOTSPOT_OFF) {
                        HotspotVerificationManager.invalidate(context)
                    }
                    listener.onStatus(context.getString(com.ghalbitnet.meshx2.R.string.hotspot_guard_redirect_message))
                    onVerificationRequired(requirement)
                    break
                }
            }
    }

    fun stop(context: Context) {
        guardJob?.cancel()
        guardJob = null
        CaptivePortalServer.stop()
        LocalProxyServer.stop()
    }
}

