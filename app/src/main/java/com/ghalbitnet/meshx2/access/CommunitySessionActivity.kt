package com.ghalbitnet.meshx2.access

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.vpn.VpnLogManager
import kotlinx.coroutines.launch

class CommunitySessionActivity : AppCompatActivity() {

    private lateinit var txtEmpty: TextView
    private lateinit var btnRefresh: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CommunitySessionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_community_session)

        txtEmpty = findViewById(R.id.txtCommunitySessionEmpty)
        btnRefresh = findViewById(R.id.btnCommunitySessionRefresh)
        recyclerView = findViewById(R.id.rvCommunitySessions)
        adapter = CommunitySessionAdapter { session ->
            Toast.makeText(
                this,
                "IP ${session.ipAddress}\nTrust ${session.trustLevel}\nAuth ${session.authStatus}\nToken ${session.accessTokenStatus}",
                Toast.LENGTH_LONG
            ).show()
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        VpnLogManager.info("COMMUNITY_SESSION_VIEW_OPENED", "Provider membuka community session view.")

        btnRefresh.setOnClickListener { refresh() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        VpnLogManager.info("COMMUNITY_SESSION_REFRESHED", "Memuat ulang community sessions.")
        CommunitySessionRepository.syncFromCurrentState(this)
        lifecycleScope.launch {
            val items = CommunitySessionRepository.allSessions(this@CommunitySessionActivity)
            adapter.submitItems(items)
            txtEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
