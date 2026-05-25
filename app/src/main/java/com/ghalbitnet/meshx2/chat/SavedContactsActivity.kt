package com.ghalbitnet.meshx2.chat

import android.widget.Button
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.core.utils.UiFeedbackManager
import com.ghalbitnet.meshx2.core.network.TransportPreference
import com.ghalbitnet.meshx2.core.node.NodeStatusManager
import com.ghalbitnet.meshx2.economy.InternetBridgePolicyManager
import com.ghalbitnet.meshx2.economy.InternetBridgeRequestQueueManager
import com.ghalbitnet.meshx2.economy.MeshServiceLedger
import com.ghalbitnet.meshx2.security.KeyStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import java.util.Locale

class SavedContactsActivity : AppCompatActivity() {

    companion object {
        private const val DRAFT_PREFS = "group_broadcast_drafts"
    }

    private lateinit var edtSearch: EditText
    private lateinit var btnAddInternetContact: Button
    private lateinit var listView: ListView
    private lateinit var tvEmpty: TextView
    private lateinit var layoutGroupFilters: LinearLayout
    private lateinit var edtGroupBroadcastMessage: EditText
    private lateinit var btnGroupTemplate: Button
    private lateinit var btnGroupBroadcast: Button
    private lateinit var btnGroupBroadcastHistory: Button
    private lateinit var adapter: SavedContactsAdapter
    private var allItems: List<SavedContactItem> = emptyList()
    private var selectedGroupFilter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_contacts)

        edtSearch = findViewById(R.id.edtSearchSavedContacts)
        btnAddInternetContact = findViewById(R.id.btnAddInternetContact)
        listView = findViewById(R.id.listSavedContacts)
        tvEmpty = findViewById(R.id.tvSavedContactsEmpty)
        layoutGroupFilters = findViewById(R.id.layoutGroupFilters)
        edtGroupBroadcastMessage = findViewById(R.id.edtGroupBroadcastMessage)
        btnGroupTemplate = findViewById(R.id.btnGroupTemplate)
        btnGroupBroadcast = findViewById(R.id.btnGroupBroadcast)
        btnGroupBroadcastHistory = findViewById(R.id.btnGroupBroadcastHistory)

        adapter = SavedContactsAdapter(
            mutableListOf(),
            onOpenChat = { openChat(it) },
            onEdit = { editContact(it) }
        )
        listView.adapter = adapter
        bindSearch()
        bindDraftEditor()
        btnGroupTemplate.setOnClickListener { chooseBroadcastTemplate() }
        btnGroupBroadcast.setOnClickListener { sendTypedGroupBroadcast() }
        btnGroupBroadcastHistory.setOnClickListener {
            showGroupBroadcastHistory()
        }
        btnAddInternetContact.setOnClickListener {
            showInternetContactDialog(null)
        }
        loadSavedContacts()
    }

    override fun onResume() {
        super.onResume()
        loadSavedContacts()
    }

    private fun loadSavedContacts() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                InternetBridgeRequestQueueManager.reevaluate(this@SavedContactsActivity)
            }
            allItems =
                withContext(Dispatchers.IO) {
                    buildContactItems()
                }

            renderGroupFilters()
            applyFilter()
            listView.setOnItemClickListener { _, _, position, _ ->
                openChat(adapter.getItem(position))
            }
            listView.setOnItemLongClickListener { _, _, position, _ ->
                editContact(adapter.getItem(position))
                true
            }
        }
    }

    private fun buildContactItems(): List<SavedContactItem> {
        val savedContacts =
            ContactAliasManager.getSavedContacts(this)
        val onlineNodes =
            NodeStatusManager.getOnlineNodes()
        val savedNames =
            savedContacts.map { it.peerName }.toSet()
        val keyStore =
            KeyStoreManager(this)
        val chatIds =
            ChatDatabase.getInstance(this)
                .chatDao()
                .getChatIds()
                .filterNot { it == "ME" || it.isBlank() }
                .filterNot { it in savedNames }

        val savedItems =
            savedContacts.map { contact ->
                val node =
                    NodeStatusManager.findNode(contact.peerName)
                val peerIp =
                    node?.ipAddress ?: keyStore.getPeerAddress(contact.peerName).orEmpty()

                SavedContactItem(
                    peerName = contact.peerName,
                    displayName = contact.alias,
                    groupLabel = contact.group.orEmpty(),
                    globalId = "",
                    note = "",
                    peerIp = peerIp,
                    modeLabel = resolveModeLabel(peerIp, node != null),
                    statusLabel = "",
                    statusDetail = "",
                    bridgeUsageLabel = "",
                    bridgeQueueLabel = "",
                    online = node?.online == true,
                    isSaved = true,
                    isInternetContact = false
                )
            }

        val remoteItems =
            GlobalContactDirectory.getAll(this).map { contact ->
                val remoteState =
                    RemotePresenceRegistry.contactState(
                        context = this,
                        nodes = onlineNodes,
                        globalId = contact.globalId
                    )
                val bridgeDecision =
                    InternetBridgePolicyManager.evaluate(this, contact.globalId)
                val queueEntry =
                    InternetBridgeRequestQueueManager.currentForPeer(this, contact.globalId)
                val queuePosition =
                    InternetBridgeRequestQueueManager.queuePositionForPeer(this, contact.globalId)
                SavedContactItem(
                    peerName = contact.globalId,
                    displayName = contact.alias,
                    groupLabel = contact.group,
                    globalId = contact.globalId,
                    note = contact.note,
                    peerIp = "",
                    modeLabel = getString(R.string.saved_contacts_remote_label),
                    statusLabel = remoteState.label,
                    statusDetail = remoteState.detail,
                    bridgeUsageLabel = getString(
                        R.string.saved_contacts_bridge_usage,
                        MeshServiceLedger.dailyBridgeUsageMb(this, contact.globalId),
                        bridgeDecision.dailyQuotaMb
                    ),
                    bridgeQueueLabel = when (queueEntry?.status) {
                        InternetBridgeRequestQueueManager.QueueStatus.ACTIVE ->
                            getString(R.string.saved_contacts_bridge_queue_active)
                        InternetBridgeRequestQueueManager.QueueStatus.WAITING ->
                            getString(
                                R.string.saved_contacts_bridge_queue_waiting,
                                queuePosition ?: 1
                            )
                        InternetBridgeRequestQueueManager.QueueStatus.DENIED ->
                            getString(R.string.saved_contacts_bridge_queue_denied)
                        null ->
                            getString(R.string.saved_contacts_bridge_queue_empty)
                    },
                    online = false,
                    isSaved = true,
                    isInternetContact = true
                )
            }

        val recentItems =
            chatIds.map { peerName ->
                val node =
                    NodeStatusManager.findNode(peerName)
                val peerIp =
                    node?.ipAddress ?: keyStore.getPeerAddress(peerName).orEmpty()

                SavedContactItem(
                    peerName = peerName,
                    displayName = peerName,
                    groupLabel = "",
                    globalId = "",
                    note = "",
                    peerIp = peerIp,
                    modeLabel = resolveModeLabel(peerIp, node != null),
                    statusLabel = "",
                    statusDetail = "",
                    bridgeUsageLabel = "",
                    bridgeQueueLabel = "",
                    online = node?.online == true,
                    isSaved = false,
                    isInternetContact = false
                )
            }

        return (savedItems + remoteItems + recentItems)
            .distinctBy { "${it.isInternetContact}:${it.peerName}" }
            .sortedWith(
                compareByDescending<SavedContactItem> { it.isSaved }
                    .thenByDescending { it.isInternetContact }
                    .thenByDescending { it.online }
                    .thenBy { it.groupLabel.lowercase(Locale.getDefault()) }
                    .thenBy { it.displayName.lowercase(Locale.getDefault()) }
        )
    }

    private fun resolveModeLabel(
        peerIp: String,
        hasNodeSnapshot: Boolean
    ): String {
        return when {
            peerIp.isNotBlank() ->
                TransportPreference.modeForAddress(peerIp).label
            hasNodeSnapshot ->
                getString(R.string.contact_status_saved_only)
            else ->
                getString(R.string.saved_contacts_recent_label)
        }
    }

    private fun bindSearch() {
        edtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun bindDraftEditor() {
        edtGroupBroadcastMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                saveCurrentDraft(
                    selectedGroupFilter,
                    s?.toString().orEmpty()
                )
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
            allItems.filter { item ->
                val matchesGroup =
                    selectedGroupFilter.isNullOrBlank() ||
                        item.groupLabel.equals(selectedGroupFilter, ignoreCase = true)

                val matchesQuery =
                    query.isBlank() ||
                    item.displayName.lowercase(Locale.getDefault()).contains(query) ||
                        item.groupLabel.lowercase(Locale.getDefault()).contains(query) ||
                        item.peerName.lowercase(Locale.getDefault()).contains(query) ||
                        item.globalId.lowercase(Locale.getDefault()).contains(query) ||
                        item.note.lowercase(Locale.getDefault()).contains(query) ||
                        item.peerIp.lowercase(Locale.getDefault()).contains(query)

                matchesGroup && matchesQuery
            }

        adapter.submitItems(filtered)
        updateGroupBroadcastButton()
        val remoteCount =
            allItems.count { it.isInternetContact }
        tvEmpty.text =
            if (filtered.isEmpty()) {
                if (selectedGroupFilter.isNullOrBlank()) {
                    RemotePresencePlanner.summary(this, NodeStatusManager.getOnlineNodes(), remoteCount) +
                        "\n\n" + getString(R.string.saved_contacts_empty)
                } else {
                    getString(R.string.saved_contacts_empty_group, selectedGroupFilter)
                }
            } else {
                RemotePresencePlanner.summary(this, NodeStatusManager.getOnlineNodes(), remoteCount)
            }
    }

    private fun updateGroupBroadcastButton() {
        if (selectedGroupFilter.isNullOrBlank()) {
            btnGroupTemplate.visibility = TextView.GONE
            edtGroupBroadcastMessage.visibility = TextView.GONE
            btnGroupBroadcast.visibility = TextView.GONE
            btnGroupBroadcastHistory.visibility = TextView.GONE
            return
        }

        btnGroupTemplate.visibility = TextView.VISIBLE
        edtGroupBroadcastMessage.visibility = TextView.VISIBLE
        btnGroupBroadcast.visibility = TextView.VISIBLE
        btnGroupBroadcastHistory.visibility = TextView.VISIBLE
        edtGroupBroadcastMessage.hint =
            getString(
                R.string.saved_contacts_broadcast_title,
                selectedGroupFilter
            )
        restoreCurrentDraft()
        btnGroupBroadcast.text =
            getString(R.string.saved_contacts_broadcast_send_new)
    }

    private fun renderGroupFilters() {
        val groups =
            allItems.mapNotNull { it.groupLabel.takeIf(String::isNotBlank) }
                .distinct()
                .sortedBy { it.lowercase(Locale.getDefault()) }

        layoutGroupFilters.removeAllViews()

        addGroupChip(
            label = getString(R.string.saved_contacts_filter_all),
            selected = selectedGroupFilter == null
        ) {
            selectedGroupFilter = null
            renderGroupFilters()
            applyFilter()
        }

        groups.forEach { group ->
            addGroupChip(
                label = group,
                selected = group.equals(selectedGroupFilter, ignoreCase = true)
            ) {
                saveCurrentDraft(
                    selectedGroupFilter,
                    edtGroupBroadcastMessage.text?.toString().orEmpty()
                )
                selectedGroupFilter =
                    if (group.equals(selectedGroupFilter, ignoreCase = true)) {
                        null
                    } else {
                        group
                    }
                renderGroupFilters()
                applyFilter()
            }
        }
    }

    private fun addGroupChip(
        label: String,
        selected: Boolean,
        onClick: () -> Unit
    ) {
        val chip =
            TextView(this).apply {
                text = label
                textSize = 12f
                setPadding(24, 10, 24, 10)
                setBackgroundColor(
                    Color.parseColor(
                        if (selected) "#84B600" else "#1A3558"
                    )
                )
                setTextColor(
                    Color.parseColor(
                        if (selected) "#11210A" else "#E8F1F8"
                    )
                )
                setOnClickListener { onClick() }
            }

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 12
            }

        layoutGroupFilters.addView(chip, params)
    }

    private fun openChat(item: SavedContactItem) {
        if (item.peerIp.isBlank()) {
            if (item.isInternetContact) {
                startActivity(
                    RemoteContactInfoActivity.createIntent(
                        context = this,
                        globalId = item.globalId
                    )
                )
                return
            }

            Toast.makeText(
                this,
                getString(R.string.saved_contacts_offline_message, item.displayName),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        startActivity(
            Intent(this, ChatActivity::class.java).apply {
                putExtra("peerIp", item.peerIp)
                putExtra("peerName", item.peerName)
            }
        )
    }

    private fun editContact(item: SavedContactItem) {
        if (item.isInternetContact) {
            showInternetContactDialog(item)
            return
        }

        val nameInput =
            EditText(this).apply {
                setText(ContactAliasManager.getAlias(this@SavedContactsActivity, item.peerName).orEmpty())
                hint = getString(R.string.contact_alias_hint)
                setSelection(text.length)
            }
        val groupInput =
            EditText(this).apply {
                setText(ContactAliasManager.getGroup(this@SavedContactsActivity, item.peerName).orEmpty())
                hint = getString(R.string.contact_group_hint)
                setSelection(text.length)
            }
        val container =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 12, 32, 0)
                addView(nameInput)
                addView(groupInput)
            }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.contact_save_dialog_title))
            .setView(container)
            .setPositiveButton(R.string.contact_save_button) { _, _ ->
                ContactAliasManager.saveContactProfile(
                    this,
                    item.peerName,
                    nameInput.text?.toString().orEmpty(),
                    groupInput.text?.toString().orEmpty()
                )
                Toast.makeText(
                    this,
                    getString(
                        R.string.contact_saved_message,
                        ContactAliasManager.getDisplayName(this, item.peerName)
                    ),
                    Toast.LENGTH_SHORT
                ).show()
                loadSavedContacts()
            }
            .setNeutralButton(R.string.contact_remove_button) { _, _ ->
                ContactAliasManager.removeAlias(this, item.peerName)
                ContactAliasManager.removeGroup(this, item.peerName)
                Toast.makeText(
                    this,
                    getString(R.string.contact_removed_message, item.peerName),
                    Toast.LENGTH_SHORT
                ).show()
                loadSavedContacts()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showInternetContactDialog(item: SavedContactItem?) {
        val nameInput =
            EditText(this).apply {
                setText(item?.displayName.orEmpty())
                hint = getString(R.string.contact_alias_hint)
            }
        val globalIdInput =
            EditText(this).apply {
                setText(item?.globalId.orEmpty())
                hint = getString(R.string.saved_contacts_global_id_hint)
            }
        val groupInput =
            EditText(this).apply {
                setText(item?.groupLabel.orEmpty())
                hint = getString(R.string.contact_group_hint)
            }
        val noteInput =
            EditText(this).apply {
                setText(item?.note.orEmpty())
                hint = getString(R.string.saved_contacts_remote_note_hint)
                minLines = 2
            }

        val container =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 12, 32, 0)
                addView(nameInput)
                addView(globalIdInput)
                addView(groupInput)
                addView(noteInput)
            }

        AlertDialog.Builder(this)
            .setTitle(
                if (item == null) {
                    getString(R.string.saved_contacts_add_internet_contact)
                } else {
                    getString(R.string.saved_contacts_edit_internet_contact)
                }
            )
            .setView(container)
            .setPositiveButton(R.string.contact_save_button) { _, _ ->
                val globalId =
                    GlobalContactDirectory.normalizeGlobalId(
                        globalIdInput.text?.toString().orEmpty()
                    )
                val alias =
                    nameInput.text?.toString().orEmpty().trim()

                if (globalId.isBlank() || alias.isBlank()) {
                    Toast.makeText(
                        this,
                        getString(R.string.saved_contacts_global_id_required),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                GlobalContactDirectory.saveContact(
                    this,
                    globalId = globalId,
                    alias = alias,
                    group = groupInput.text?.toString().orEmpty(),
                    note = noteInput.text?.toString().orEmpty()
                )
                Toast.makeText(
                    this,
                    getString(R.string.contact_saved_message, alias),
                    Toast.LENGTH_SHORT
                ).show()
                loadSavedContacts()
            }
            .setNeutralButton(R.string.contact_remove_button) { _, _ ->
                item?.globalId?.let {
                    GlobalContactDirectory.removeContact(this, it)
                    Toast.makeText(
                        this,
                        getString(R.string.contact_removed_message, item.displayName),
                        Toast.LENGTH_SHORT
                    ).show()
                    loadSavedContacts()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun chooseBroadcastTemplate() {
        val templates =
            GroupBroadcastManager.defaultTemplates(this)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.saved_contacts_broadcast_choose_template))
            .setItems(templates.toTypedArray()) { _, which ->
                edtGroupBroadcastMessage.setText(templates[which])
                edtGroupBroadcastMessage.setSelection(edtGroupBroadcastMessage.text.length)
            }
            .show()
    }

    private fun sendTypedGroupBroadcast() {
        val group =
            selectedGroupFilter ?: return
        val recipients =
            allItems.filter {
                it.isSaved &&
                    it.online &&
                    it.groupLabel.equals(group, ignoreCase = true) &&
                    it.peerIp.isNotBlank()
            }

        if (recipients.isEmpty()) {
            UiFeedbackManager.showToast(
                this,
                getString(R.string.saved_contacts_broadcast_none_online, group)
            )
            return
        }

        val message =
            edtGroupBroadcastMessage.text?.toString()
                ?.trim()
                .orEmpty()

        if (message.isBlank()) {
            UiFeedbackManager.showToast(
                this,
                getString(R.string.message_empty)
            )
            return
        }

        sendGroupBroadcast(group, recipients, message)
    }

    private fun sendGroupBroadcast(
        group: String,
        recipients: List<SavedContactItem>,
        message: String
    ) {
        lifecycleScope.launch {
            btnGroupBroadcast.isEnabled = false
            btnGroupBroadcast.text = getString(R.string.saved_contacts_broadcast_sending)

            val deliveryResults =
                withContext(Dispatchers.IO) {
                    val chatDao =
                        ChatDatabase.getInstance(this@SavedContactsActivity).chatDao()
                    val keyStore =
                        KeyStoreManager(this@SavedContactsActivity)
                    val results =
                        mutableListOf<GroupBroadcastManager.RecipientStatus>()

                    recipients.forEach { item ->
                        val packetId =
                            "GROUP-${item.peerName}-${UUID.randomUUID()}"

                        chatDao.insertMessage(
                            ChatMessage(
                                packetId = packetId,
                                chatId = item.peerName,
                                senderName = "ME",
                                content = "[${group}] $message",
                                isSent = true,
                                status = "SENDING"
                            )
                        )

                        val ok =
                            ChatSendHelper.sendTextMessage(
                                keyStore = keyStore,
                                peerName = item.peerName,
                                peerIp = item.peerIp,
                                message = "[${group}] $message",
                                packetId = packetId
                            )

                        chatDao.updateStatus(
                            packetId,
                            if (ok) "SENT" else "FAILED"
                        )

                        results += GroupBroadcastManager.RecipientStatus(
                            peerName = item.peerName,
                            displayName = item.displayName,
                            delivered = ok
                        )
                    }

                    results
                }

            btnGroupBroadcast.isEnabled = true
            btnGroupBroadcast.text = getString(R.string.saved_contacts_broadcast_send_new)
            edtGroupBroadcastMessage.setText("")
            clearCurrentDraft()

            val total = recipients.size
            val successCount =
                deliveryResults.count { it.delivered }
            GroupBroadcastManager.saveHistory(
                this@SavedContactsActivity,
                GroupBroadcastManager.BroadcastEntry(
                    group = group,
                    message = message,
                    successCount = successCount,
                    totalCount = total,
                    timestamp = System.currentTimeMillis(),
                    recipients = deliveryResults
                )
            )
            UiFeedbackManager.showToast(
                this@SavedContactsActivity,
                if (successCount == total) {
                    getString(
                        R.string.saved_contacts_broadcast_success,
                        successCount,
                        group
                    )
                } else {
                    getString(
                        R.string.saved_contacts_broadcast_partial,
                        successCount,
                        total,
                        group
                    )
                }
            )
        }
    }

    private fun showGroupBroadcastHistory() {
        val group =
            selectedGroupFilter ?: return
        val history =
            GroupBroadcastManager.getHistoryForGroup(this, group)

        if (history.isEmpty()) {
            UiFeedbackManager.showToast(
                this,
                getString(R.string.saved_contacts_broadcast_history_empty, group)
            )
            return
        }

        val formatter =
            SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
        val body =
            history.joinToString("\n\n") { entry ->
                val recipients =
                    if (entry.recipients.isEmpty()) {
                        ""
                    } else {
                        "\n" + entry.recipients.joinToString("\n") { recipient ->
                            val statusLabel =
                                if (recipient.delivered) {
                                    getString(R.string.saved_contacts_broadcast_recipient_ok)
                                } else {
                                    getString(R.string.saved_contacts_broadcast_recipient_failed)
                                }
                            "- ${recipient.displayName} ($statusLabel)"
                        }
                    }
                "${formatter.format(Date(entry.timestamp))}\n" +
                    "${entry.message}\n" +
                    "${entry.successCount}/${entry.totalCount} kontak$recipients"
            }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.saved_contacts_broadcast_history))
            .setMessage(body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun saveCurrentDraft(
        group: String?,
        text: String
    ) {
        if (group.isNullOrBlank()) {
            return
        }

        getSharedPreferences(DRAFT_PREFS, MODE_PRIVATE)
            .edit()
            .putString(group, text)
            .apply()
    }

    private fun restoreCurrentDraft() {
        val group =
            selectedGroupFilter ?: return
        val draft =
            getSharedPreferences(DRAFT_PREFS, MODE_PRIVATE)
                .getString(group, "")
                .orEmpty()

        if (edtGroupBroadcastMessage.text?.toString() == draft) {
            return
        }

        edtGroupBroadcastMessage.setText(draft)
        edtGroupBroadcastMessage.setSelection(edtGroupBroadcastMessage.text.length)
    }

    private fun clearCurrentDraft() {
        val group =
            selectedGroupFilter ?: return

        getSharedPreferences(DRAFT_PREFS, MODE_PRIVATE)
            .edit()
            .remove(group)
            .apply()
    }
}
