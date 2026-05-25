package com.ghalbitnet.meshx2.economy

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ghalbitnet.meshx2.R

class MeshEconomyRateTableActivity : AppCompatActivity() {

    private lateinit var txtRateTableSummary: TextView
    private lateinit var txtRateTableBridgeSummary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mesh_economy_rate_table)

        txtRateTableSummary = findViewById(R.id.txtRateTableSummary)
        txtRateTableBridgeSummary = findViewById(R.id.txtRateTableBridgeSummary)

        val lockedButtons = listOf(
            R.id.btnRateBurn,
            R.id.btnRateGateway,
            R.id.btnRateRelay,
            R.id.btnRateTreasury,
            R.id.btnRateChat,
            R.id.btnRateMedia,
            R.id.btnRateCall,
            R.id.btnRateSos,
            R.id.btnRateControl,
            R.id.btnRateOther,
            R.id.btnRateBuilderShare,
            R.id.btnRatePresetBalanced,
            R.id.btnRatePresetGateway,
            R.id.btnRatePresetRelay,
            R.id.btnRatePresetSos,
            R.id.btnRateReset
        )

        lockedButtons.forEach { viewId ->
            findViewById<Button>(viewId).apply {
                isEnabled = false
                alpha = 0.65f
            }
        }

        Toast.makeText(
            this,
            getString(R.string.service_economy_rate_locked_notice),
            Toast.LENGTH_LONG
        ).show()

        renderSummary()
    }

    private fun renderSummary() {
        val policy =
            MeshEconomyServerPolicyManager.current(this)

        val appBonusTable =
            policy.appBonusTable

        val bridgeTable =
            policy.internetBridgeTable

        txtRateTableSummary.text =
            getString(
                R.string.service_economy_rate_summary_readonly,
                policy.sourceLabel,
                policy.versionLabel,
                MeshEconomyRateTableManager.round2(appBonusTable.gatewayPerMb),
                MeshEconomyRateTableManager.round2(appBonusTable.relayPerMb),
                MeshEconomyRateTableManager.round2(appBonusTable.treasuryPerMb),
                MeshEconomyRateTableManager.round2(appBonusTable.builderShareRate * 100.0),
                MeshEconomyRateTableManager.round2(appBonusTable.chatMultiplier),
                MeshEconomyRateTableManager.round2(appBonusTable.mediaMultiplier),
                MeshEconomyRateTableManager.round2(appBonusTable.callMultiplier),
                MeshEconomyRateTableManager.round2(appBonusTable.sosMultiplier),
                MeshEconomyRateTableManager.round2(appBonusTable.controlMultiplier),
                MeshEconomyRateTableManager.round2(appBonusTable.otherMultiplier)
            )

        txtRateTableBridgeSummary.text =
            getString(
                R.string.service_economy_rate_bridge_summary_readonly,
                MeshEconomyRateTableManager.round2(bridgeTable.burnPerMb),
                MeshEconomyRateTableManager.round2(bridgeTable.gatewayPerMb),
                MeshEconomyRateTableManager.round2(bridgeTable.relayPerMb),
                MeshEconomyRateTableManager.round2(bridgeTable.treasuryPerMb),
                MeshEconomyRateTableManager.round2(bridgeTable.builderShareRate * 100.0),
                MeshEconomyRateTableManager.round2(bridgeTable.chatMultiplier),
                MeshEconomyRateTableManager.round2(bridgeTable.mediaMultiplier),
                MeshEconomyRateTableManager.round2(bridgeTable.callMultiplier),
                MeshEconomyRateTableManager.round2(bridgeTable.sosMultiplier),
                MeshEconomyRateTableManager.round2(bridgeTable.controlMultiplier),
                MeshEconomyRateTableManager.round2(bridgeTable.otherMultiplier)
            )
    }
}
