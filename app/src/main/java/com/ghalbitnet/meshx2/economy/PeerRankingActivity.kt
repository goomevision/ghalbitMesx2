package com.ghalbitnet.meshx2.economy

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.chat.GlobalContactDirectory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PeerRankingActivity : AppCompatActivity() {

    private data class PeerRankRow(
        val alias: String,
        val globalId: String,
        val sessions: Int,
        val trafficMb: Double,
        val gatewayReward: Double,
        val relayReward: Double,
        val burnAmount: Double,
        val deniedCount: Int,
        val allowedCount: Int,
        val updatedAt: Long,
        val reputationScore: Int,
        val reputationLabel: String,
        val reputationDetail: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_peer_ranking)

        val textView =
            findViewById<TextView>(R.id.txtPeerRanking)

        val rows =
            GlobalContactDirectory.getAll(this)
                .map { contact ->
                    val snapshot =
                        MeshServiceLedger.peerSnapshot(this, contact.globalId)
                    val decisions =
                        InternetBridgeRequestLogManager.decisionSummaryForPeer(this, contact.globalId)
                    val reputation =
                        PeerReputationManager.calculate(snapshot, decisions)

                    PeerRankRow(
                        alias = contact.alias,
                        globalId = contact.globalId,
                        sessions = snapshot.sessionCount,
                        trafficMb = snapshot.totalMegaBytes,
                        gatewayReward = snapshot.totalGatewayReward,
                        relayReward = snapshot.totalRelayReward,
                        burnAmount = snapshot.totalBurned,
                        deniedCount = decisions.denied,
                        allowedCount = decisions.allowed,
                        updatedAt = snapshot.lastUpdatedAt,
                        reputationScore = reputation.score,
                        reputationLabel = reputation.label,
                        reputationDetail = reputation.detail
                    )
                }
                .sortedWith(
                    compareByDescending<PeerRankRow> { it.reputationScore }
                        .thenByDescending { it.trafficMb }
                        .thenByDescending { it.gatewayReward + it.relayReward }
                        .thenByDescending { it.sessions }
                        .thenBy { it.deniedCount }
                        .thenBy { it.alias.lowercase(Locale.getDefault()) }
                )

        textView.text =
            if (rows.isEmpty()) {
                getString(R.string.peer_ranking_empty)
            } else {
                val formatter =
                    SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                rows.mapIndexed { index, row ->
                    buildString {
                        append(index + 1)
                        append(". ")
                        append(row.alias)
                        append('\n')
                        append(row.globalId)
                        append('\n')
                        append(
                            getString(
                                R.string.peer_ranking_line_reputation,
                                row.reputationScore,
                                row.reputationLabel
                            )
                        )
                        append('\n')
                        append(
                            getString(
                                R.string.peer_ranking_line_one,
                                row.trafficMb,
                                row.sessions,
                                row.allowedCount,
                                row.deniedCount
                            )
                        )
                        append('\n')
                        append(
                            getString(
                                R.string.peer_ranking_line_two,
                                row.gatewayReward,
                                row.relayReward,
                                row.burnAmount
                            )
                        )
                        append('\n')
                        append(row.reputationDetail)
                        append('\n')
                        append(
                            getString(
                                R.string.peer_ranking_line_three,
                                if (row.updatedAt > 0L) {
                                    formatter.format(Date(row.updatedAt))
                                } else {
                                    getString(R.string.remote_contact_economy_never)
                                }
                            )
                        )
                    }
                }.joinToString("\n\n")
            }
    }
}
