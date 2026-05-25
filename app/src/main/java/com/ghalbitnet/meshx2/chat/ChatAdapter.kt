package com.ghalbitnet.meshx2.chat
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.settings.ChatMediaSettingsManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val onMessageClick: (ChatMessage) -> Unit = {},
    private val onMessageLongClick: (ChatMessage) -> Unit = {}
) :
    RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    private var playingPacketId: String? = null

    override fun getItemViewType(position: Int) = if (messages[position].isSent) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = if (viewType == VIEW_TYPE_SENT) R.layout.item_chat_sent else R.layout.item_chat_received
        return MessageViewHolder(LayoutInflater.from(parent.context).inflate(layout, parent, false), viewType)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val msg = messages[position]
        val isPlaying =
            msg.contentType == "AUDIO" &&
                msg.packetId == playingPacketId
        val previousMessage =
            messages.getOrNull(position - 1)

        bindImagePreview(holder, msg)
        bindDayHeader(holder, msg, previousMessage)
        bindMessageTag(holder, msg)

        holder.content.text = when (msg.contentType) {
            "IMAGE" -> formatImageCaption(msg.content)
            "FILE" -> formatFileCaption(msg.content)
            "AUDIO" -> {
                if (isPlaying) {
                    "${msg.content}\nSedang diputar"
                } else {
                    msg.content
                }
            }
            else -> msg.content
        }
        holder.time.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
        if (holder.senderName != null) {
            holder.senderName.text =
                if (msg.senderName == "ME") {
                    msg.senderName
                } else {
                    ContactAliasManager.getDisplayName(
                        holder.itemView.context,
                        msg.senderName
                    )
                }
        }
        holder.status?.text =
            if (msg.isSent) {
                if (isPlaying) {
                    "${formatStatus(msg.status)} - PLAY"
                } else {
                    formatStatus(msg.status)
                }
            } else {
                ""
            }
        holder.itemView.setOnClickListener(null)
        if (
            !msg.filePath.isNullOrBlank() &&
            (msg.contentType == "AUDIO" ||
                msg.contentType == "IMAGE" ||
                msg.contentType == "FILE")
        ) {
            holder.itemView.setOnClickListener {
                onMessageClick(msg)
            }
        }

        holder.itemView.setOnLongClickListener {
            onMessageLongClick(msg)
            true
        }
    }

    private fun bindDayHeader(
        holder: MessageViewHolder,
        msg: ChatMessage,
        previousMessage: ChatMessage?
    ) {
        val shouldShowHeader =
            previousMessage == null || !isSameDay(previousMessage.timestamp, msg.timestamp)
        if (!shouldShowHeader) {
            holder.dayHeader.visibility = View.GONE
            holder.dayHeader.text = ""
            return
        }

        holder.dayHeader.visibility = View.VISIBLE
        holder.dayHeader.text = formatDayHeader(msg.timestamp)
    }

    private fun bindMessageTag(
        holder: MessageViewHolder,
        msg: ChatMessage
    ) {
        val tagText =
            when (msg.contentType) {
                "IMAGE" -> "FOTO"
                "FILE" -> "FILE"
                "AUDIO" -> "SUARA"
                "CALL" -> "PANGGILAN"
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

    private fun bindImagePreview(
        holder: MessageViewHolder,
        msg: ChatMessage
    ) {
        if (
            msg.contentType != "IMAGE" ||
            msg.filePath.isNullOrBlank() ||
            !ChatMediaSettingsManager.shouldShowImagePreview(holder.itemView.context)
        ) {
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

        val bounds =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        val sampleSize =
            calculateInSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                reqWidth = 480,
                reqHeight = 360
            )

        val bitmap =
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
            )

        if (bitmap == null) {
            holder.preview.setImageDrawable(null)
            holder.preview.visibility = View.GONE
            return
        }

        holder.preview.setImageBitmap(bitmap)
        holder.preview.visibility = View.VISIBLE
    }

    private fun formatImageCaption(raw: String): String {
        val cleaned =
            raw.removePrefix("[Gambar]").trim()
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
        val cleaned =
            raw.removePrefix("[File]").trim()
        return if (cleaned.isBlank()) {
            "Lampiran file"
        } else {
            "$cleaned\nKetuk untuk membuka"
        }
    }

    private fun formatStatus(raw: String): String {
        return when (raw.uppercase(Locale.getDefault())) {
            "SENDING" -> "MENGIRIM"
            "SENT" -> "TERKIRIM"
            "RECEIVED" -> "DITERIMA"
            "FAILED" -> "GAGAL"
            "PLAYED" -> "DIPUTAR"
            else -> raw
        }
    }

    private fun formatDayHeader(timestamp: Long): String {
        val today = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }

        if (isSameDay(today.timeInMillis, timestamp)) {
            return "Hari ini"
        }

        today.add(Calendar.DAY_OF_YEAR, -1)
        if (isSameDay(today.timeInMillis, timestamp)) {
            return "Kemarin"
        }

        return SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date(timestamp))
    }

    private fun isSameDay(
        firstTimestamp: Long,
        secondTimestamp: Long
    ): Boolean {
        val first = Calendar.getInstance().apply {
            timeInMillis = firstTimestamp
        }
        val second = Calendar.getInstance().apply {
            timeInMillis = secondTimestamp
        }
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        var currentWidth = width
        var currentHeight = height

        while (
            currentWidth / 2 >= reqWidth &&
            currentHeight / 2 >= reqHeight
        ) {
            currentWidth /= 2
            currentHeight /= 2
            inSampleSize *= 2
        }

        return inSampleSize.coerceAtLeast(1)
    }

    override fun getItemCount() = messages.size

    fun submitMessages(next: List<ChatMessage>) {
        messages.clear()
        messages.addAll(next)
        notifyDataSetChanged()
    }

    fun setPlayingMessage(packetId: String?) {
        if (playingPacketId == packetId) {
            return
        }

        playingPacketId = packetId
        notifyDataSetChanged()
    }

    class MessageViewHolder(itemView: View, viewType: Int) : RecyclerView.ViewHolder(itemView) {
        val dayHeader: TextView = itemView.findViewById(R.id.tvDayHeader)
        val messageTag: TextView = itemView.findViewById(R.id.tvMessageTag)
        val preview: ImageView = itemView.findViewById(R.id.ivMessageImage)
        val content: TextView = itemView.findViewById(R.id.tvMessageContent)
        val time: TextView = itemView.findViewById(R.id.tvMessageTime)
        val senderName: TextView? = if (viewType == VIEW_TYPE_RECEIVED) itemView.findViewById(R.id.tvSenderName) else null
        val status: TextView? = if (viewType == VIEW_TYPE_SENT) itemView.findViewById(R.id.tvMessageStatus) else null
    }
}
