package com.ghalbitnet.meshx2.economy

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ghalbitnet.meshx2.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MeshEconomyLedgerActivity : AppCompatActivity() {

    private lateinit var txtLedgerDetail: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mesh_economy_ledger)

        txtLedgerDetail = findViewById(R.id.txtLedgerDetail)
        renderLedger()
    }

    private fun renderLedger() {
        val entries =
            MeshServiceLedger.recentEntries(this, 25)

        txtLedgerDetail.text =
            if (entries.isEmpty()) {
                getString(R.string.service_economy_ledger_empty)
            } else {
                entries.joinToString("\n\n") { entry ->
                    formatEntry(entry)
                }
            }
    }

    private fun formatEntry(
        entry: ServiceLedgerEntry
    ): String {
        val time =
            SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
                .format(Date(entry.session.endedAt))

        val durationSeconds =
            entry.session.durationMs / 1000L

        val relayPath =
            if (entry.session.relayPath.isEmpty()) {
                getString(R.string.service_economy_path_direct)
            } else {
                entry.session.relayPath.joinToString(" -> ") { relay ->
                    relay.nodeName
                }
            }
        val routeSegments =
            if (entry.session.routeSegments.isEmpty()) {
                ""
            } else {
                entry.session.routeSegments.joinToString(" | ") { segment ->
                    val gatewayName =
                        if (segment.localGateway) {
                            getString(R.string.gateway_this_device)
                        } else {
                            segment.gatewayNodeName.ifBlank {
                                getString(R.string.gateway_active_none)
                            }
                        }
                    "$gatewayName ${segment.durationMs / 1000L}d"
                }
            }
        val gatewaySplit =
            if (entry.settlement.gatewayRewards.isEmpty()) {
                ""
            } else {
                entry.settlement.gatewayRewards.joinToString(" | ") { reward ->
                    "${reward.nodeName}: ${String.format(Locale.US, "%.2f", reward.amount)}"
                }
            }

        return buildString {
            append(time)
            append('\n')
            append("Session: ")
            append(entry.session.sessionId)
            append(" | Mode: ")
            append(entry.session.usageMode.name)
            append(" | Family: ")
            append(entry.session.serviceFamily.name)
            append(" x")
            append(String.format(Locale.US, "%.2f", entry.settlement.familyMultiplier))
            append('\n')
            append("Harga: ")
            append(entry.settlement.pricingLabel)
            append(" | User charge: ")
            append(if (entry.settlement.userCharged) "YA" else "TIDAK")
            append('\n')
            append("User: ")
            append(entry.session.userGlobalId)
            append('\n')
            append("Traffic: ")
            append(String.format(Locale.US, "%.2f MB", entry.session.totalMegaBytes))
            append(" | Durasi: ")
            append(durationSeconds)
            append(" detik")
            append('\n')
            append("Gateway: ")
            append(
                if (entry.session.localInternetProvider) {
                    getString(R.string.gateway_this_device)
                } else {
                    entry.session.gatewayNodeName.ifBlank {
                        getString(R.string.gateway_active_none)
                    }
                }
            )
            append('\n')
            append("Berhenti: ")
            append(entry.session.stopReason)
            append('\n')
            append("Relay path: ")
            append(relayPath)
            append('\n')
            if (routeSegments.isNotBlank()) {
                append("Segmen jalur: ")
                append(routeSegments)
                append('\n')
            }
            append(
                getString(
                    R.string.service_economy_ledger_rewards,
                    entry.settlement.burnAmount,
                    entry.settlement.gatewayReward,
                    entry.settlement.totalRelayReward,
                    entry.settlement.builderReward,
                    entry.settlement.validatorReward,
                    entry.settlement.treasuryReserve
                )
            )
            append('\n')
            append("Validasi: ")
            append(entry.settlement.validationScore)
            append(" | Proof total: ")
            append(entry.settlement.proofScore.overallProof)
            append(" | Latency: ")
            append(entry.session.averageLatencyMs)
            append(" ms")
            append('\n')
            if (gatewaySplit.isNotBlank()) {
                append("Bagi gateway: ")
                append(gatewaySplit)
                append('\n')
            }
            append(
                getString(
                    R.string.service_economy_ledger_proof,
                    entry.settlement.proofScore.gatewayProof,
                    entry.settlement.proofScore.relayProof,
                    entry.settlement.proofScore.validatorProof,
                    entry.settlement.proofScore.meshLocalProof
                )
            )
            append('\n')
            append(entry.settlement.notes)
        }
    }
}
