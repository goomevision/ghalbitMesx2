package com.ghalbitnet.meshx2.chat

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AlertDialog
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.core.network.TransportPreference
import com.ghalbitnet.meshx2.core.node.NodeStatusManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ContactListActivity : AppCompatActivity() {

    private lateinit var edtSearch: EditText
    private lateinit var listView: ListView
    private lateinit var tvEmpty: TextView
    private var refreshJob: Job? = null
    private var allItems: List<ContactListItem> = emptyList()
    private lateinit var contactAdapter: ContactListAdapter

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_contact_list)

        edtSearch =
            findViewById(R.id.edtSearchContacts)
        listView =
            findViewById(R.id.listContacts)

        tvEmpty =
            findViewById(R.id.tvEmpty)

        contactAdapter = ContactListAdapter(
            mutableListOf(),
            onOpenChat = { openChat(it) },
            onShowInfo = { showContactInfo(it) }
        )
        listView.adapter = contactAdapter
        bindSearch()

        loadContacts()
    }

    override fun onResume() {
        super.onResume()
        loadContacts()
        startAutoRefresh()
    }

    override fun onPause() {
        super.onPause()
        refreshJob?.cancel()
        refreshJob = null
    }

    private fun loadContacts() {

        val nodes =
            NodeStatusManager.getOnlineNodes()
                .filter { it.online }

        if (nodes.isEmpty()) {
            tvEmpty.text = getString(R.string.no_node_online)
            return
        }

        tvEmpty.text = ""

        lifecycleScope.launch {
            val items =
                withContext(Dispatchers.IO) {
                    val chatDb = ChatDatabase.getInstance(this@ContactListActivity)
                    nodes.map { node ->
                        val messages =
                            chatDb.chatDao().getMessages(node.name)
                        val lastMessage =
                            messages.lastOrNull()
                        val qualityScore =
                            calculateQualityScore(node.signal, node.latency, node.trusted)
                        val unreadCount =
                            ChatReadStateManager.unreadCount(
                                context = this@ContactListActivity,
                                chatId = node.name,
                                messages = messages
                            )

                        ContactListItem(
                            peerName = node.name,
                            displayName = ContactAliasManager.getDisplayName(
                                this@ContactListActivity,
                                node.name
                            ),
                            peerIp = node.ipAddress,
                            modeLabel = TransportPreference.modeForAddress(node.ipAddress).label,
                            pinned = ContactPinManager.isPinned(this@ContactListActivity, node.name),
                            signal = node.signal,
                            latency = node.latency,
                            trusted = node.trusted,
                            qualityLabel = formatQuality(qualityScore),
                            summary = formatSummary(lastMessage),
                            summaryTime = formatSummaryTime(lastMessage?.timestamp),
                            onlineLabel = getString(R.string.contact_status_online),
                            unreadCount = unreadCount,
                            qualityScore = qualityScore,
                            lastMessageTimestamp = lastMessage?.timestamp ?: 0L
                        )
                    }.sortedWith(
                        compareByDescending<ContactListItem> { it.pinned }
                            .thenByDescending { it.unreadCount > 0 }
                            .thenByDescending { it.unreadCount }
                            .thenByDescending { it.qualityScore }
                            .thenByDescending { it.lastMessageTimestamp }
                            .thenBy { it.displayName.lowercase(Locale.getDefault()) }
                    )
                }

            allItems = items
            applyFilter()
            listView.setOnItemClickListener { _, _, position, _ ->
                val selected = contactAdapter.getItem(position)
                openChat(selected)
            }
            listView.setOnItemLongClickListener { _, _, position, _ ->
                val selected = contactAdapter.getItem(position)
                showContactActions(selected)
                true
            }
        }
    }

    private fun openChat(selected: ContactListItem) {
        startActivity(
            Intent(this@ContactListActivity, ChatActivity::class.java).apply {
                putExtra("peerIp", selected.peerIp)
                putExtra("peerName", selected.peerName)
            }
        )
    }

    private fun showContactActions(selected: ContactListItem) {
        val pinLabel =
            if (selected.pinned) {
                getString(R.string.contact_action_unpin)
            } else {
                getString(R.string.contact_action_pin)
            }
        val aliasLabel =
            if (ContactAliasManager.hasAlias(this, selected.peerName)) {
                getString(R.string.contact_action_edit_contact)
            } else {
                getString(R.string.contact_action_save_contact)
            }

        val actions =
            arrayOf(
                getString(R.string.contact_action_open_chat),
                pinLabel,
                aliasLabel,
                getString(R.string.contact_action_info)
            )

        AlertDialog.Builder(this)
            .setTitle(selected.displayName)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> openChat(selected)
                    1 -> togglePin(selected)
                    2 -> promptSaveContact(selected)
                    3 -> showContactInfo(selected)
                }
            }
            .show()
    }

    private fun togglePin(selected: ContactListItem) {
        val pinned =
            ContactPinManager.togglePinned(
                this,
                selected.peerName
            )
        Toast.makeText(
            this,
            if (pinned) {
                getString(R.string.contact_pin_added, selected.peerName)
            } else {
                getString(R.string.contact_pin_removed, selected.peerName)
            },
            Toast.LENGTH_SHORT
        ).show()
        loadContacts()
    }

    private fun showContactInfo(selected: ContactListItem) {
        startActivity(NodeInfoActivity.createIntent(this, selected))
    }

    private fun promptSaveContact(selected: ContactListItem) {
        val input =
            EditText(this).apply {
                setText(ContactAliasManager.getAlias(this@ContactListActivity, selected.peerName).orEmpty())
                hint = getString(R.string.contact_alias_hint)
                setSelection(text.length)
            }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.contact_save_dialog_title))
            .setView(input)
            .setPositiveButton(R.string.contact_save_button) { _, _ ->
                ContactAliasManager.saveAlias(
                    this,
                    selected.peerName,
                    input.text?.toString().orEmpty()
                )
                Toast.makeText(
                    this,
                    getString(
                        R.string.contact_saved_message,
                        ContactAliasManager.getDisplayName(this, selected.peerName)
                    ),
                    Toast.LENGTH_SHORT
                ).show()
                loadContacts()
            }
            .setNeutralButton(R.string.contact_remove_button) { _, _ ->
                ContactAliasManager.removeAlias(this, selected.peerName)
                Toast.makeText(
                    this,
                    getString(R.string.contact_removed_message, selected.peerName),
                    Toast.LENGTH_SHORT
                ).show()
                loadContacts()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun bindSearch() {
        edtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                applyFilter()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun applyFilter() {
        val query =
            edtSearch.text?.toString()
                ?.trim()
                .orEmpty()
                .lowercase(Locale.getDefault())

        val filtered =
            if (query.isBlank()) {
                allItems
            } else {
                allItems.filter { item ->
                    item.peerName.lowercase(Locale.getDefault()).contains(query) ||
                        item.displayName.lowercase(Locale.getDefault()).contains(query) ||
                        item.peerIp.lowercase(Locale.getDefault()).contains(query) ||
                        item.modeLabel.lowercase(Locale.getDefault()).contains(query) ||
                        item.summary.lowercase(Locale.getDefault()).contains(query) ||
                        item.qualityLabel.lowercase(Locale.getDefault()).contains(query)
                }
            }

        contactAdapter.submitItems(filtered)
        tvEmpty.text =
            if (filtered.isEmpty()) {
                getString(R.string.contact_search_empty)
            } else {
                ""
            }
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob =
            lifecycleScope.launch {
                while (true) {
                    delay(8000L)
                    loadContacts()
                }
            }
    }

    private fun formatSummary(lastMessage: ChatMessage?): String {
        if (lastMessage == null) {
            return getString(R.string.contact_summary_empty)
        }

        return when (lastMessage.contentType) {
            "IMAGE" -> getString(R.string.contact_summary_photo)
            "FILE" -> getString(R.string.contact_summary_file)
            "AUDIO" -> getString(R.string.contact_summary_audio)
            "CALL" -> lastMessage.content
            "SOS" -> "SOS"
            else -> lastMessage.content
        }
    }

    private fun formatSummaryTime(timestamp: Long?): String {
        if (timestamp == null) {
            return getString(R.string.contact_summary_no_time)
        }

        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    private fun formatQuality(
        score: Int
    ): String {
        return when {
            score >= 120 -> getString(R.string.contact_quality_strong)
            score >= 75 -> getString(R.string.contact_quality_medium)
            else -> getString(R.string.contact_quality_weak)
        }
    }

    private fun calculateQualityScore(
        signal: Int,
        latency: Int,
        trusted: Int
    ): Int =
        signal.coerceIn(0, 100) +
            trusted.coerceIn(0, 100) -
            latency.coerceIn(0, 250) / 2
}
