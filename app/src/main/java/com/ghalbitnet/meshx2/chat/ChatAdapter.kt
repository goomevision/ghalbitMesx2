package com.ghalbitnet.meshx2.chat

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.settings.ChatMediaSettingsManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ChatAdapter(
    private val onMessageClick: (ChatMessage) -> Unit = {},
    private val onMessageLongClick: (ChatMessage) -> Unit = {}
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val VIEW_TYPE_SYSTEM_CALL = 3
        private const val VIEW_TYPE_SYSTEM_RELAY = 4
        private const val VIEW_TYPE_SYSTEM_WARNING = 5
        private const val VIEW_TYPE_SYSTEM_EMERGENCY = 6
        private const val PAYLOAD_DELIVERY = "delivery"
        private const val PAYLOAD_EDIT = "edit"
        private const val PAYLOAD_DELETE = "delete"
        private const val PAYLOAD_CONTENT = "content"
        private const val PAYLOAD_PLAYING = "playing"

        private object DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
                return oldItem.packetId == newItem.packetId
            }

            override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
                return oldItem == newItem
            }

            override fun getChangePayload(oldItem: ChatMessage, newItem: ChatMessage): Any {
                val payload = Bundle()
                if (oldItem.status != newItem.status) {
                    payload.putBoolean(PAYLOAD_DELIVERY, true)
                    if (newItem.status.uppercase(Locale.ROOT).contains("EDIT")) {
                        payload.putBoolean(PAYLOAD_EDIT, true)
                    }
                    if (newItem.status.uppercase(Locale.ROOT).contains("DELETE")) {
                        payload.putBoolean(PAYLOAD_DELETE, true)
                    }
                    Log.d("GHALBIT-CHAT-DIFF", "payload delivery id=${newItem.packetId}")
                }
                if (oldItem.content != newItem.content) {
                    payload.putBoolean(PAYLOAD_CONTENT, true)
                    if (newItem.status.uppercase(Locale.ROOT).contains("EDIT")) {
                        Log.d("GHALBIT-CHAT-DIFF", "payload edit id=${newItem.packetId}")
                    }
                    if (newItem.status.uppercase(Locale.ROOT).contains("DELETE")) {
                        Log.d("GHALBIT-CHAT-DIFF", "payload delete id=${newItem.packetId}")
                    }
                }
                return payload
            }
        }
    }

    private var playingPacketId: String? = null

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        if (isSystemEvent(item)) {
            return when (SystemEventStyle.fromMessage(item).category) {
                "RELAY_EVENT", "ROUTE_EVENT" -> VIEW_TYPE_SYSTEM_RELAY
                "WARNING_EVENT" -> VIEW_TYPE_SYSTEM_WARNING
                "EMERGENCY_EVENT" -> VIEW_TYPE_SYSTEM_EMERGENCY
                else -> VIEW_TYPE_SYSTEM_CALL
            }
        }
        return if (item.isSent) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SENT -> MessageViewHolder(inflater.inflate(R.layout.item_chat_sent, parent, false), viewType)
            VIEW_TYPE_RECEIVED -> MessageViewHolder(inflater.inflate(R.layout.item_chat_received, parent, false), viewType)
            VIEW_TYPE_SYSTEM_RELAY -> SystemEventViewHolder(inflater.inflate(R.layout.item_system_relay_event, parent, false))
            VIEW_TYPE_SYSTEM_WARNING -> SystemEventViewHolder(inflater.inflate(R.layout.item_system_warning_event, parent, false))
            VIEW_TYPE_SYSTEM_EMERGENCY -> SystemEventViewHolder(inflater.inflate(R.layout.item_system_emergency_event, parent, false))
            else -> SystemEventViewHolder(inflater.inflate(R.layout.item_system_call_event, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is MessageViewHolder) {
            bindMessage(holder, position, fullBind = true)
        } else if (holder is SystemEventViewHolder) {
            bindSystemEvent(holder, position)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (holder is SystemEventViewHolder) {
            bindSystemEvent(holder, position)
            return
        }
        val messageHolder = holder as MessageViewHolder
        val bundle = payloads.filterIsInstance<Bundle>().fold(Bundle()) { acc, item ->
            acc.putAll(item)
            acc
        }
        if (bundle.isEmpty) {
            bindMessage(messageHolder, position, fullBind = true)
        } else {
            bindMessage(messageHolder, position, fullBind = false, payload = bundle)
        }
    }

    private fun bindMessage(
        holder: MessageViewHolder,
        position: Int,
        fullBind: Boolean,
        payload: Bundle? = null
    ) {
        val msg = getItem(position)
        val isPlaying = msg.contentType == "AUDIO" && msg.packetId == playingPacketId

        if (fullBind) {
            val previousMessage = currentList.getOrNull(position - 1)
            bindImagePreview(holder, msg)
            bindDayHeader(holder.dayHeader, msg, previousMessage)
            bindMessageTag(holder, msg)
            holder.time.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
            if (holder.senderName != null) {
                holder.senderName.text = msg.senderName
            }
            holder.itemView.setOnClickListener(null)
            if (!msg.filePath.isNullOrBlank() && (msg.contentType == "AUDIO" || msg.contentType == "IMAGE" || msg.contentType == "FILE")) {
                holder.itemView.setOnClickListener { onMessageClick(msg) }
            }
            holder.itemView.setOnLongClickListener {
                onMessageLongClick(msg)
                true
            }
        }

        if (fullBind || payload?.getBoolean(PAYLOAD_CONTENT) == true || payload?.getBoolean(PAYLOAD_EDIT) == true || payload?.getBoolean(PAYLOAD_DELETE) == true || payload?.getBoolean(PAYLOAD_PLAYING) == true) {
            holder.content.text = when (msg.contentType) {
                "IMAGE" -> formatImageCaption(msg.content)
                "FILE" -> formatFileCaption(msg.content)
                "AUDIO" -> if (isPlaying) "${msg.content}\nSedang diputar" else msg.content
                else -> formatTextContent(msg)
            }
        }

        if (fullBind || payload?.getBoolean(PAYLOAD_DELIVERY) == true || payload?.getBoolean(PAYLOAD_EDIT) == true || payload?.getBoolean(PAYLOAD_DELETE) == true) {
            bindDelivery(holder, msg, isPlaying)
        }
    }

    private fun bindSystemEvent(holder: SystemEventViewHolder, position: Int) {
        val message = getItem(position)
        val previousMessage = currentList.getOrNull(position - 1)
        bindDayHeader(holder.dayHeader, message, previousMessage)
        val style = SystemEventStyle.fromMessage(message)
        holder.icon.text = style.icon
        holder.text.text = HumanEventFormatter.displayText(holder.itemView.context, message)
        holder.container.setBackgroundColor(style.accentColor and 0x33FFFFFF)
        ChatSystemAnimator.apply(holder.container, style.lifetime)
    }

    private fun bindDelivery(holder: MessageViewHolder, msg: ChatMessage, isPlaying: Boolean) {
        if (holder.deliveryIndicator != null && msg.isSent) {
            val state = ChatDeliveryState.fromDb(msg.status)
            holder.deliveryIndicator.setDeleted(msg.status.uppercase(Locale.ROOT).contains("DELETED"))
            holder.deliveryIndicator.setEdited(msg.status.uppercase(Locale.ROOT).contains("EDITED"))
            holder.deliveryIndicator.setDeliveryState(state)
            val shortLabel = formatStatusLabel(state, isPlaying)
            holder.status?.text = shortLabel
            holder.status?.visibility = if (shortLabel.isBlank()) View.GONE else View.VISIBLE
            if (msg.status.uppercase(Locale.ROOT).contains("EDITED")) {
                Log.d("GHALBIT-DELIVERY-UI", "edited=true")
            }
            if (msg.status.uppercase(Locale.ROOT).contains("DELETED")) {
                Log.d("GHALBIT-DELIVERY-UI", "deleted=true")
            }
        } else {
            holder.status?.text = ""
            holder.status?.visibility = View.GONE
        }
    }

    private fun bindDayHeader(dayHeader: TextView, msg: ChatMessage, previousMessage: ChatMessage?) {
        val shouldShowHeader = previousMessage == null || !isSameDay(previousMessage.timestamp, msg.timestamp)
        if (!shouldShowHeader) {
            dayHeader.visibility = View.GONE
            dayHeader.text = ""
            return
        }
        dayHeader.visibility = View.VISIBLE
        dayHeader.text = formatDayHeader(msg.timestamp)
    }

    private fun bindMessageTag(holder: MessageViewHolder, msg: ChatMessage) {
        val tagText = when (msg.contentType) {
            "IMAGE" -> "FOTO"
            "FILE" -> "FILE"
            "AUDIO" -> "SUARA"
            "CALL" -> "PANGGILAN"
            "CALL_EVENT" -> "EVENT"
            "SOS" -> "SOS"
            else -> ""
        }
        if (tagText.isBlank()) {
            holder.messageTag.visibility = View.GONE
            holder.messageTag.text = ""
            return
        }
        holder.messageTag.visibility = View.VISIBLE
        holder.messageTag.text = tagText
    }

    private fun bindImagePreview(holder: MessageViewHolder, msg: ChatMessage) {
        if (msg.contentType != "IMAGE" || msg.filePath.isNullOrBlank() || !ChatMediaSettingsManager.shouldShowImagePreview(holder.itemView.context)) {
            holder.preview.setImageDrawable(null)
            holder.preview.visibility = View.GONE
            return
        }
        val file = File(msg.filePath)
        if (!file.exists()) {
            holder.preview.setImageDrawable(null)
            holder.preview.visibility = View.GONE
            return
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 480, 360)
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        if (bitmap == null) {
            holder.preview.setImageDrawable(null)
            holder.preview.visibility = View.GONE
            return
        }
        holder.preview.setImageBitmap(bitmap)
        holder.preview.visibility = View.VISIBLE
    }

    private fun formatImageCaption(raw: String): String {
        val cleaned = raw.removePrefix("[Gambar]").trim()
        return if (
            cleaned.isBlank() ||
            cleaned.contains(".jpg", true) ||
            cleaned.contains(".jpeg", true) ||
            cleaned.contains(".png", true) ||
            cleaned.contains(".webp", true)
        ) {
            "Foto"
        } else {
            cleaned
        }
    }

    private fun formatFileCaption(raw: String): String {
        val cleaned = raw.removePrefix("[File]").trim()
        return if (cleaned.isBlank()) "Lampiran file" else "$cleaned\nKetuk untuk membuka"
    }

    private fun formatStatusLabel(state: ChatDeliveryState, isPlaying: Boolean): String {
        if (isPlaying) return "PLAY"
        return when (state) {
            ChatDeliveryState.RELAY_CONFIG_REQUIRED -> "RELAY"
            ChatDeliveryState.WAITING_FOR_ROUTE -> "JALUR"
            ChatDeliveryState.QUEUED_LOCAL,
            ChatDeliveryState.PENDING,
            ChatDeliveryState.DRAFT,
            ChatDeliveryState.DRAFT_TEXT,
            ChatDeliveryState.DRAFT_MEDIA,
            ChatDeliveryState.DRAFT_FILE,
            ChatDeliveryState.REVIEW_READY,
            ChatDeliveryState.EDITING_DRAFT -> "DRAFT"
            ChatDeliveryState.WAITING_FOR_PEER,
            ChatDeliveryState.QUEUED_REMOTE,
            ChatDeliveryState.MEDIA_QUEUED_REMOTE,
            ChatDeliveryState.ACCEPTED_BY_RELAY -> "ONLINE"
            ChatDeliveryState.FAILED_RETRYING -> "ULANG"
            ChatDeliveryState.FAILED_FINAL,
            ChatDeliveryState.MEDIA_EXPIRED,
            ChatDeliveryState.EXPIRED_REMOTE -> "GAGAL"
            ChatDeliveryState.MEDIA_RESUMING -> "LANJUT"
            ChatDeliveryState.EDITED_REMOTE -> "EDIT"
            ChatDeliveryState.DELETED_LOCAL,
            ChatDeliveryState.DELETED_REMOTE,
            ChatDeliveryState.DELETE_REQUESTED_REMOTE -> "HAPUS"
            else -> ""
        }
    }

    private fun formatTextContent(msg: ChatMessage): String {
        val normalizedStatus = msg.status.uppercase(Locale.ROOT)
        return when {
            normalizedStatus.contains("DELETED") -> "Pesan dihapus"
            else -> msg.content
        }
    }

    private fun formatDayHeader(timestamp: Long): String {
        val today = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestamp }
        if (isSameDay(today.timeInMillis, target.timeInMillis)) return "Hari ini"
        today.add(Calendar.DAY_OF_YEAR, -1)
        if (isSameDay(today.timeInMillis, timestamp)) return "Kemarin"
        return SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date(timestamp))
    }

    private fun isSameDay(firstTimestamp: Long, secondTimestamp: Long): Boolean {
        val first = Calendar.getInstance().apply { timeInMillis = firstTimestamp }
        val second = Calendar.getInstance().apply { timeInMillis = secondTimestamp }
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth / 2 >= reqWidth && currentHeight / 2 >= reqHeight) {
            currentWidth /= 2
            currentHeight /= 2
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    fun submitMessages(next: List<ChatMessage>) {
        Log.d("GHALBIT-CHAT-DIFF", "submit size=${next.size}")
        Log.d("GHALBIT-UI-PERF", "avoided full refresh")
        submitList(next.toList())
    }

    fun setPlayingMessage(packetId: String?) {
        if (playingPacketId == packetId) return
        val oldPacket = playingPacketId
        playingPacketId = packetId
        currentList.indexOfFirst { it.packetId == oldPacket }.takeIf { it >= 0 }?.let {
            notifyItemChanged(it, Bundle().apply { putBoolean(PAYLOAD_PLAYING, true) })
        }
        currentList.indexOfFirst { it.packetId == packetId }.takeIf { it >= 0 }?.let {
            notifyItemChanged(it, Bundle().apply { putBoolean(PAYLOAD_PLAYING, true) })
        }
    }

    class MessageViewHolder(itemView: View, viewType: Int) : RecyclerView.ViewHolder(itemView) {
        val dayHeader: TextView = itemView.findViewById(R.id.tvDayHeader)
        val messageTag: TextView = itemView.findViewById(R.id.tvMessageTag)
        val preview: ImageView = itemView.findViewById(R.id.ivMessageImage)
        val content: TextView = itemView.findViewById(R.id.tvMessageContent)
        val time: TextView = itemView.findViewById(R.id.tvMessageTime)
        val senderName: TextView? = if (viewType == VIEW_TYPE_RECEIVED) itemView.findViewById(R.id.tvSenderName) else null
        val status: TextView? = if (viewType == VIEW_TYPE_SENT) itemView.findViewById(R.id.tvMessageStatus) else null
        val deliveryIndicator: MessageDeliveryIndicatorView? = if (viewType == VIEW_TYPE_SENT) itemView.findViewById(R.id.viewDeliveryIndicator) else null
    }

    class SystemEventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dayHeader: TextView = itemView.findViewById(R.id.tvDayHeader)
        val container: View = itemView.findViewById(R.id.systemEventContainer)
        val icon: TextView = itemView.findViewById(R.id.tvSystemIcon)
        val text: TextView = itemView.findViewById(R.id.tvSystemText)
    }

    private fun isSystemEvent(message: ChatMessage): Boolean {
        return message.messageType == MessageVisibility.SYSTEM_EVENT.name ||
            message.contentType.endsWith("_EVENT")
    }
}
