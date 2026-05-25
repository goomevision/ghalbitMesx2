package com.ghalbitnet.meshx2.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.ghalbitnet.meshx2.R

data class ContactListItem(
    val peerName: String,
    val displayName: String,
    val peerIp: String,
    val modeLabel: String,
    val pinned: Boolean,
    val signal: Int,
    val latency: Int,
    val trusted: Int,
    val qualityLabel: String,
    val summary: String,
    val summaryTime: String,
    val onlineLabel: String,
    val unreadCount: Int,
    val qualityScore: Int,
    val lastMessageTimestamp: Long
)

class ContactListAdapter(
    private val items: MutableList<ContactListItem>,
    private val onOpenChat: (ContactListItem) -> Unit,
    private val onShowInfo: (ContactListItem) -> Unit
) : BaseAdapter() {

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): ContactListItem = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    fun submitItems(next: List<ContactListItem>) {
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
                .inflate(R.layout.item_contact, parent, false)

        val item = getItem(position)

        view.findViewById<TextView>(R.id.tvContactName).text = item.displayName
        view.findViewById<TextView>(R.id.tvContactMeta).text =
            buildMetaLine(item)
        val pinnedView = view.findViewById<TextView>(R.id.tvPinnedBadge)
        if (item.pinned) {
            pinnedView.visibility = View.VISIBLE
            pinnedView.text = "PIN"
        } else {
            pinnedView.visibility = View.GONE
            pinnedView.text = ""
        }
        view.findViewById<TextView>(R.id.tvContactQuality).text = item.qualityLabel
        view.findViewById<TextView>(R.id.tvContactSummary).text = item.summary
        view.findViewById<TextView>(R.id.tvContactTime).text = item.summaryTime
        view.findViewById<TextView>(R.id.tvContactStatus).text = item.onlineLabel
        view.findViewById<TextView>(R.id.btnContactChat).setOnClickListener {
            onOpenChat(item)
        }
        view.findViewById<TextView>(R.id.btnContactInfo).setOnClickListener {
            onShowInfo(item)
        }
        val unreadView = view.findViewById<TextView>(R.id.tvUnreadBadge)
        if (item.unreadCount > 0) {
            unreadView.visibility = View.VISIBLE
            unreadView.text = item.unreadCount.coerceAtMost(99).toString()
        } else {
            unreadView.visibility = View.GONE
            unreadView.text = ""
        }

        return view
    }

    private fun buildMetaLine(item: ContactListItem): String {
        return if (item.displayName != item.peerName) {
            "${item.peerName}  |  ${item.modeLabel}  |  ${item.peerIp}"
        } else {
            "${item.modeLabel}  |  ${item.peerIp}"
        }
    }
}
