package com.ghalbitnet.meshx2.debug

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.core.log.LocalLogBuffer
import com.ghalbitnet.meshx2.stats.MeshStatistics
import com.ghalbitnet.meshx2.core.node.NodeStatusManager
import com.ghalbitnet.meshx2.network.AckTracker
import com.ghalbitnet.meshx2.routing.RouteTable

class DebugActivity : AppCompatActivity() {

    private lateinit var txtDebug: TextView

    private val handler =
        Handler(Looper.getMainLooper())

    private val refreshRunnable =
        object : Runnable {
            override fun run() {

                txtDebug.text =
                    buildString {
                        appendLine(MeshStatistics.report())
                        appendLine()
                        appendLine(NodeStatusManager.report())
                        appendLine()
                        appendLine(AckTracker.report())
                        appendLine()
                        appendLine(RouteTable.report())
                        appendLine()
                        appendLine("LOCAL LOG")
                        appendLine("======================")
                        appendLine(LocalLogBuffer.getText())
                    }

                handler.postDelayed(this, 2000)
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_debug)

        txtDebug =
            findViewById(R.id.txtDebug)

        txtDebug.movementMethod =
            ScrollingMovementMethod()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }
}
