package com.ghalbitnet.meshx2.vpn

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ghalbitnet.meshx2.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class UsageHistoryAdapter : RecyclerView.Adapter<UsageHistoryAdapter.UsageHistoryViewHolder>() {

    private val items = mutableListOf<UsageSessionEntity>()

    fun submitItems(next: List<UsageSessionEntity>) {
        items.clear()
        items.addAll(next)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsageHistoryViewHolder {
        val view =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_usage_session, parent, false)
        return UsageHistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: UsageHistoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class UsageHistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val title: TextView = itemView.findViewById(R.id.txtUsageItemTitle)
        private val meta: TextView = itemView.findViewById(R.id.txtUsageItemMeta)
        private val traffic: TextView = itemView.findViewById(R.id.txtUsageItemTraffic)
        private val sync: TextView = itemView.findViewById(R.id.txtUsageItemSync)

        fun bind(item: UsageSessionEntity) {
            title.text =
                buildString {
                    append(formatTime(item.startTime))
                    append(" • ")
                    append(item.operatingMode)
                }
            meta.text =
                buildString {
                    append(if (item.endTime == null) "Aktif" else "Selesai")
                    append(" • ")
                    append(formatDuration(item.startTime, item.endTime))
                    append("\n")
                    append(item.sessionId)
                }
            traffic.text =
                buildString {
                    append("Total ")
                    append(formatBytes(item.totalBytes))
                    append(" • Up ")
                    append(formatBytes(item.totalUploadBytes))
                    append(" • Down ")
                    append(formatBytes(item.totalDownloadBytes))
                }
            sync.text =
                if (item.isSynced) {
                    "Synced"
                } else {
                    "Belum synced"
                }
        }

        private fun formatBytes(bytes: Long): String {
            val megaBytes = bytes / 1024.0 / 1024.0
            return String.format(Locale.getDefault(), "%.2f MB", megaBytes)
        }

        private fun formatTime(timeMillis: Long): String =
            SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(timeMillis))

        private fun formatDuration(startTime: Long, endTime: Long?): String {
            val durationMillis = max(0L, (endTime ?: System.currentTimeMillis()) - startTime)
            val totalSeconds = durationMillis / 1000L
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0) {
                String.format(Locale.getDefault(), "%dh %02dm %02ds", hours, minutes, seconds)
            } else {
                String.format(Locale.getDefault(), "%dm %02ds", minutes, seconds)
            }
        }
    }
}
