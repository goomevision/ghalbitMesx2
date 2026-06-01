package com.ghalbitnet.meshx2.diagnostics.autodiag

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.ui.GhalbitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AutoDiagnosticActivity : AppCompatActivity() {

    private lateinit var txtResult: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auto_diagnostic)
        GhalbitTheme.applyWindow(this, "AutoDiagnosticActivity")
        supportActionBar?.title = "Auto Diagnostic Center"
        txtResult = findViewById(R.id.txtAutoDiagnosticResult)

        findViewById<Button>(R.id.btnRunFullDiagnostic).setOnClickListener {
            runDiagnostic()
        }
        findViewById<Button>(R.id.btnCopyDiagnosticReport).setOnClickListener {
            val current = txtResult.text?.toString().orEmpty()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Auto Diagnostic", current))
            Toast.makeText(this, "Laporan disalin.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun runDiagnostic() {
        txtResult.text = "Running full diagnostic..."
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { AutoDiagnosticOrchestrator.run(this@AutoDiagnosticActivity) }
            val markdown = AutoDiagnosticReportGenerator.toMarkdown(result)
            txtResult.text = AutoDiagnosticReportGenerator.toHumanSummary(result)
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Auto Diagnostic Markdown", markdown))
            Toast.makeText(
                this@AutoDiagnosticActivity,
                "Diagnostic selesai: ${result.status} (${result.totalScore}/100).",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

