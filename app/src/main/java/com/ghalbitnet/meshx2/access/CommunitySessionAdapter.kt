package com.ghalbitnet.meshx2.access

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ghalbitnet.meshx2.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommunitySessionAdapter(
    private val onDetail: (CommunitySessionEntity) -> Unit
) : RecyclerView.Adapter<CommunitySessionAdapter.CommunitySessionViewHolder>() {

    private val items = mutableListOf<CommunitySessionEntity>()

    fun submitItems(next: List<CommunitySessionEntity>) {
        items.clear()
        items.addAll(next)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommunitySessionViewHolder {
        val view =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_community_session, parent, false)
        return CommunitySessionViewHolder(view, onDetail)
    }

    override fun onBindViewHolder(holder: CommunitySessionViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class CommunitySessionViewHolder(
        itemView: View,
        private val onDetail: (CommunitySessionEntity) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val txtTitle: TextView = itemView.findViewById(R.id.txtCommunitySessionTitle)
        private val txtMeta: TextView = itemView.findViewById(R.id.txtCommunitySessionMeta)
        private val txtTraffic: TextView = itemView.findViewById(R.id.txtCommunitySessionTraffic)
        private val btnDetail: Button = itemView.findViewById(R.id.btnCommunitySessionDetail)

        fun bind(item: CommunitySessionEntity) {
            txtTitle.text = "${item.ipAddress} - ${item.trustLevel}"
            txtMeta.text =
                buildString {
                    append("Node: ${item.nodeId ?: "-"}")
                    append("\nAuth: ${item.authStatus} | Token: ${item.accessTokenStatus}")
                    append("\nTerakhir: ${formatTime(item.lastSeen)}")
                }
            txtTraffic.text =
                String.format(
                    Locale.getDefault(),
                    "Up %.2f MB | Down %.2f MB | Total %.2f MB",
                    item.uploadBytes / 1024.0 / 1024.0,
                    item.downloadBytes / 1024.0 / 1024.0,
                    item.totalBytes / 1024.0 / 1024.0
                )
            btnDetail.setOnClickListener { onDetail(item) }
        }

        private fun formatTime(value: Long): String =
            SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(Date(value))
    }
}
