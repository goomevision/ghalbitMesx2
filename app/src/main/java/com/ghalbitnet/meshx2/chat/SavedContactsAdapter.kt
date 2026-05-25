package com.ghalbitnet.meshx2.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.ghalbitnet.meshx2.R

data class SavedContactItem(
    val peerName: String,
    val displayName: String,
    val groupLabel: String,
    val globalId: String,
    val note: String,
    val peerIp: String,
    val modeLabel: String,
    val statusLabel: String,
    val statusDetail: String,
    val bridgeUsageLabel: String,
    val bridgeQueueLabel: String,
    val online: Boolean,
    val isSaved: Boolean,
    val isInternetContact: Boolean
)

class SavedContactsAdapter(
    private val items: MutableList<SavedContactItem>,
    private val onOpenChat: (SavedContactItem) -> Unit,
    private val onEdit: (SavedContactItem) -> Unit
) : BaseAdapter() {

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): SavedContactItem = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    fun submitItems(next: List<SavedContactItem>) {
        items.clear()
        items.addAll(next)
        notifyDataSetChanged()
    }

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup
    ): View {
        val view =
            convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_saved_contact, parent, false)

        val item = getItem(position)

        view.findViewById<TextView>(R.id.tvSavedContactAlias).text = item.displayName
        view.findViewById<TextView>(R.id.tvSavedContactNode).text =
            if (item.isInternetContact) {
                view.context.getString(R.string.saved_contacts_global_id_value, item.globalId)
            } else if (item.isSaved) {
                item.peerName
            } else {
                view.context.getString(R.string.saved_contacts_recent_node, item.peerName)
            }
        view.findViewById<TextView>(R.id.tvSavedContactMeta).text =
            if (item.isInternetContact) {
                listOf(
                    item.modeLabel,
                    item.note.takeIf { it.isNotBlank() }
                ).joinToString("  |  ")
            } else if (item.peerIp.isBlank()) {
                item.modeLabel
            } else {
                "${item.modeLabel}  |  ${item.peerIp}"
            }
        val bridgeView =
            view.findViewById<TextView>(R.id.tvSavedContactBridge)
        if (item.isInternetContact) {
            bridgeView.visibility = View.VISIBLE
            bridgeView.text =
                listOf(
                    item.bridgeUsageLabel.takeIf { it.isNotBlank() },
                    item.bridgeQueueLabel.takeIf { it.isNotBlank() },
                    item.statusDetail.takeIf { it.isNotBlank() }
                ).joinToString("\n")
        } else {
            bridgeView.visibility = View.GONE
            bridgeView.text = ""
        }
        val groupView =
            view.findViewById<TextView>(R.id.tvSavedContactGroup)
        if (item.groupLabel.isBlank()) {
            groupView.visibility = View.GONE
            groupView.text = ""
        } else {
            groupView.visibility = View.VISIBLE
            groupView.text = item.groupLabel
        }

        val statusView =
            view.findViewById<TextView>(R.id.tvSavedContactStatus)
        statusView.text =
            if (item.isInternetContact) {
                item.statusLabel
            } else if (item.online) {
                view.context.getString(R.string.contact_status_online)
            } else {
                view.context.getString(R.string.contact_status_offline)
            }
        statusView.setBackgroundColor(
            android.graphics.Color.parseColor(
                when {
                    item.isInternetContact && item.statusLabel == view.context.getString(R.string.saved_contacts_status_remote_online) -> "#17331F"
                    item.isInternetContact && item.statusLabel == view.context.getString(R.string.saved_contacts_status_remote_standby) -> "#2C3348"
                    item.isInternetContact && item.statusLabel == view.context.getString(R.string.saved_contacts_status_hybrid_ready) -> "#17331F"
                    item.isInternetContact && item.statusLabel == view.context.getString(R.string.saved_contacts_status_registry_active) -> "#153041"
                    item.isInternetContact -> "#2C2548"
                    item.online -> "#17331F"
                    else -> "#3A1F1F"
                }
            )
        )
        statusView.setTextColor(
            android.graphics.Color.parseColor(
                when {
                    item.isInternetContact && item.statusLabel == view.context.getString(R.string.saved_contacts_status_remote_online) -> "#9FE870"
                    item.isInternetContact && item.statusLabel == view.context.getString(R.string.saved_contacts_status_remote_standby) -> "#D9E4FF"
                    item.isInternetContact && item.statusLabel == view.context.getString(R.string.saved_contacts_status_hybrid_ready) -> "#9FE870"
                    item.isInternetContact && item.statusLabel == view.context.getString(R.string.saved_contacts_status_registry_active) -> "#8FD8FF"
                    item.isInternetContact -> "#D8C7FF"
                    item.online -> "#9FE870"
                    else -> "#FFB3B3"
                }
            )
        )

        view.findViewById<TextView>(R.id.btnSavedContactEdit).text =
            if (item.isSaved) {
                view.context.getString(R.string.contact_action_edit_contact_short)
            } else {
                view.context.getString(R.string.contact_action_save_contact_short)
            }
        view.findViewById<TextView>(R.id.btnSavedContactChat).setOnClickListener {
            onOpenChat(item)
        }
        view.findViewById<TextView>(R.id.btnSavedContactEdit).setOnClickListener {
            onEdit(item)
        }

        return view
    }
}
