package com.ghalbitnet.meshx2.chat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.online.OnlinePresenceManager
import com.ghalbitnet.meshx2.online.PendingMessageStore
import com.ghalbitnet.meshx2.profile.ProfileRepository
import com.ghalbitnet.meshx2.profile.ProfileSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactListActivity : AppCompatActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_contact_list)

        val listView =
            findViewById<ListView>(R.id.listContacts)

        val tvEmpty =
            findViewById<TextView>(R.id.tvEmpty)

        OnlinePresenceManager.bind(this)
        lifecycleScope.launch {
            val prepared = withContext(Dispatchers.IO) {
                val contacts = LiveContactSync.build(this@ContactListActivity)
                ProfileSyncManager.batchSyncProfiles(this@ContactListActivity, contacts.mapNotNull { it.globalId })
                val liveContacts =
                    contacts.filter {
                        it.isLive ||
                            (!it.globalId.isNullOrBlank() && OnlinePresenceManager.getOnlineRoute(this@ContactListActivity, it.globalId) != null) ||
                            PendingMessageStore.countForChat(this@ContactListActivity, it.chatId) > 0
                    }
                val labels =
                    liveContacts.map { contact ->
                        val resolved =
                            ProfileRepository.getResolvedContact(
                                context = this@ContactListActivity,
                                globalId = contact.globalId,
                                chatId = contact.chatId,
                                fallbackDisplayName = contact.displayName,
                                publicKeyHash = contact.publicKeyHash,
                                routeHint = contact.routeHint
                            )
                        buildString {
                            append(resolved.primaryName)
                            if (resolved.primaryName != resolved.displayName && resolved.displayName.isNotBlank()) {
                                append(" (")
                                append(resolved.displayName)
                                append(")")
                            }
                            append(" | ")
                            val onlineRoute = contact.globalId?.let { OnlinePresenceManager.getOnlineRoute(this@ContactListActivity, it) }
                            val pending = PendingMessageStore.countForChat(this@ContactListActivity, contact.chatId) > 0
                            append(
                                AdaptiveRouteManager.contactStatusLabel(
                                    context = this@ContactListActivity,
                                    contact = contact,
                                    pending = pending
                                )
                            )
                            contact.globalId?.let {
                                append(" | ")
                                append(it)
                            }
                            (contact.routeHint ?: onlineRoute?.relayUrl)?.let {
                                append(" | ")
                                append(it)
                            }
                        }
                    }
                liveContacts to labels
            }
            val liveContacts = prepared.first
            val labels = prepared.second
            if (liveContacts.isEmpty()) {
                tvEmpty.text = getString(R.string.no_node_online)
                return@launch
            }
            tvEmpty.text = ""
            listView.adapter = ArrayAdapter(this@ContactListActivity, android.R.layout.simple_list_item_1, labels)
            listView.setOnItemClickListener { _, _, position, _ ->
                val selected = liveContacts[position]
                Log.d("GHALBIT-CONTACT-UI", "open live chatId=${selected.chatId} globalId=${selected.globalId ?: "-"} route=${selected.routeHint ?: "-"}")
                val resolved =
                    ProfileRepository.getResolvedContact(
                        context = this@ContactListActivity,
                        globalId = selected.globalId,
                        chatId = selected.chatId,
                        fallbackDisplayName = selected.displayName,
                        publicKeyHash = selected.publicKeyHash,
                        routeHint = selected.routeHint
                    )
                ConversationIdentityStore.upsert(
                    context = this@ContactListActivity,
                    chatId = selected.chatId,
                    metadata = ConversationIdentityMetadata(
                        chatId = selected.chatId,
                        globalId = selected.globalId,
                        publicKey = selected.publicKey,
                        publicKeyHash = selected.publicKeyHash,
                        walletAddress = selected.walletAddress,
                        canonicalDisplayName = resolved.primaryName,
                        lastSeen = selected.lastSeen,
                        routeHint = selected.routeHint,
                        updatedAt = selected.lastSeen ?: System.currentTimeMillis()
                    )
                )
                startActivity(
                    Intent(this@ContactListActivity, ChatActivity::class.java).apply {
                        putExtra("peerIp", selected.routeHint ?: "")
                        putExtra("peerName", selected.chatId)
                        putExtra("peerGlobalId", selected.globalId)
                        putExtra("peerPublicKey", selected.publicKey)
                        putExtra("peerWalletAddress", selected.walletAddress)
                        putExtra("peerDisplayName", resolved.primaryName)
                    }
                )
            }
        }
    }
}
