package com.ghalbitnet.meshx2.chat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ghalbitnet.meshx2.core.node.NodeStatusManager
import com.ghalbitnet.meshx2.identity.IdentityDisplayFormatter
import com.ghalbitnet.meshx2.profile.ProfileRepository
import com.ghalbitnet.meshx2.profile.ProfileSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SavedContactsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = "Percakapan & Kontak"

        val container =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(36, 36, 36, 36)
            }

        val introView =
            TextView(this).apply {
                text =
                    "Daftar ini menampilkan percakapan legacy dan kontak ringan, tetapi nama yang ditampilkan sudah mencoba memakai metadata canonical jika tersedia."
            }

        val emptyView =
            TextView(this).apply {
                text =
                    "Belum ada kontak tersimpan. Kontak akan muncul setelah Anda menemukan node atau membuka chat."
                setPadding(0, 24, 0, 0)
            }

        val listView =
            ListView(this).apply {
                dividerHeight = 12
                visibility = View.GONE
            }

        container.addView(introView)
        container.addView(
            emptyView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        container.addView(
            listView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(container)

        lifecycleScope.launch {
            val roster =
                withContext(Dispatchers.IO) {
                    val initial = LiveContactSync.build(this@SavedContactsActivity)
                    ProfileSyncManager.batchSyncProfiles(this@SavedContactsActivity, initial.mapNotNull { it.globalId })
                    initial
                }

            if (roster.isEmpty()) {
                listView.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
                return@launch
            }

            emptyView.visibility = View.GONE
            listView.visibility = View.VISIBLE

            listView.adapter =
                ArrayAdapter(
                    this@SavedContactsActivity,
                    android.R.layout.simple_list_item_1,
                    roster.map { item ->
                        val resolved =
                            ProfileRepository.getResolvedContact(
                                context = this@SavedContactsActivity,
                                globalId = item.globalId,
                                chatId = item.chatId,
                                fallbackDisplayName = item.displayName,
                                publicKeyHash = item.publicKeyHash,
                                routeHint = item.routeHint
                            )
                        val primaryLabel =
                            IdentityDisplayFormatter.primaryLabel(
                                canonicalDisplayName = resolved.primaryName,
                                walletAddress = item.walletAddress,
                                globalId = item.globalId,
                                publicKey = item.publicKey,
                                legacyName = item.chatId,
                                ipAddress = item.routeHint
                            )
                        val secondaryLabel =
                            IdentityDisplayFormatter.secondaryLabel(
                                primaryLabel = primaryLabel,
                                legacyName = resolved.displayName.takeIf { it != resolved.primaryName } ?: item.chatId,
                                walletAddress = item.walletAddress,
                                globalId = item.globalId,
                                publicKey = item.publicKey,
                                ipAddress = item.routeHint
                            )
                        buildString {
                            append(primaryLabel)
                            append("\n")
                            append(
                                when {
                                    item.isLive -> "Aktif di jaringan"
                                    item.isSaved -> "Saved contact"
                                    else -> "Offline contact"
                                }
                            )
                            secondaryLabel?.let {
                                append("\n")
                                append(it)
                            }
                        }
                    }
                )

            listView.setOnItemClickListener { _, _, position, _ ->
                val selected =
                    roster[position]
                Log.d("GHALBIT-CONTACT-UI", "open roster chatId=${selected.chatId} live=${selected.isLive} saved=${selected.isSaved} route=${selected.routeHint ?: "-"}")

                ConversationIdentityStore.upsert(
                    context = this@SavedContactsActivity,
                    chatId = selected.chatId,
                        metadata = ConversationIdentityMetadata(
                            chatId = selected.chatId,
                            globalId = selected.globalId,
                            publicKey = selected.publicKey,
                            publicKeyHash = selected.publicKeyHash,
                            walletAddress = selected.walletAddress,
                            canonicalDisplayName = ProfileRepository.getResolvedContact(
                                context = this@SavedContactsActivity,
                                globalId = selected.globalId,
                                chatId = selected.chatId,
                                fallbackDisplayName = selected.displayName,
                                publicKeyHash = selected.publicKeyHash,
                                routeHint = selected.routeHint
                            ).primaryName,
                            lastSeen = selected.lastSeen,
                            routeHint = selected.routeHint,
                            updatedAt = selected.lastSeen ?: System.currentTimeMillis()
                    )
                )

                startActivity(
                    Intent(this@SavedContactsActivity, ChatActivity::class.java).apply {
                        putExtra("peerName", selected.chatId)
                        putExtra("peerIp", selected.routeHint ?: "")
                        putExtra("peerGlobalId", selected.globalId)
                        putExtra("peerPublicKey", selected.publicKey)
                        putExtra("peerWalletAddress", selected.walletAddress)
                        putExtra(
                            "peerDisplayName",
                            ProfileRepository.getResolvedContact(
                                context = this@SavedContactsActivity,
                                globalId = selected.globalId,
                                chatId = selected.chatId,
                                fallbackDisplayName = selected.displayName,
                                publicKeyHash = selected.publicKeyHash,
                                routeHint = selected.routeHint
                            ).primaryName
                        )
                    }
                )
            }
        }
    }
}
