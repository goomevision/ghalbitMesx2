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

        return buildString {
            append(time)
            append('\n')
            append("Session: ")
            append(entry.session.sessionId)
            append(" | Family: ")
            append(entry.session.serviceFamily.name)
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
            append("Relay path: ")
            append(relayPath)
            append('\n')
            append(
                getString(
                    R.string.service_economy_ledger_rewards,
                    entry.settlement.burnAmount,
                    entry.settlement.gatewayReward,
                    entry.settlement.totalRelayReward,
                    entry.settlement.builderReward,
                    entry.settlement.treasuryReserve
                )
            )
            append('\n')
            append("Validasi: ")
            append(entry.settlement.validationScore)
            append(" | Latency: ")
            append(entry.session.averageLatencyMs)
            append(" ms")
            append('\n')
            append(entry.settlement.notes)
        }
    }
}
