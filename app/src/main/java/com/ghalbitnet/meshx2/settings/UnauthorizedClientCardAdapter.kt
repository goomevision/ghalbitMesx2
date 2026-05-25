package com.ghalbitnet.meshx2.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.access.ClientDisplayIdentity
import com.ghalbitnet.meshx2.access.UnauthorizedClientUiModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UnauthorizedClientCardAdapter(
    private val onCopyMac: (UnauthorizedClientUiModel) -> Unit,
    private val onCopyIp: (UnauthorizedClientUiModel) -> Unit,
    private val onBlock: (UnauthorizedClientUiModel) -> Unit,
    private val onAllow: (UnauthorizedClientUiModel) -> Unit,
    private val onOpenSettings: (UnauthorizedClientUiModel) -> Unit
) : RecyclerView.Adapter<UnauthorizedClientCardAdapter.UnauthorizedClientCardViewHolder>() {

    data class CardItem(
        val model: UnauthorizedClientUiModel,
        val identity: ClientDisplayIdentity
    )

    private val items = mutableListOf<CardItem>()

    fun submitItems(next: List<CardItem>) {
        items.clear()
        items.addAll(next)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UnauthorizedClientCardViewHolder {
        val view =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_unauthorized_client_card, parent, false)
        return UnauthorizedClientCardViewHolder(view, onCopyMac, onCopyIp, onBlock, onAllow, onOpenSettings)
    }

    override fun onBindViewHolder(holder: UnauthorizedClientCardViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class UnauthorizedClientCardViewHolder(
        itemView: View,
        private val onCopyMac: (UnauthorizedClientUiModel) -> Unit,
        private val onCopyIp: (UnauthorizedClientUiModel) -> Unit,
        private val onBlock: (UnauthorizedClientUiModel) -> Unit,
        private val onAllow: (UnauthorizedClientUiModel) -> Unit,
        private val onOpenSettings: (UnauthorizedClientUiModel) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val txtTitle: TextView = itemView.findViewById(R.id.txtUnauthorizedCardTitle)
        private val txtBody: TextView = itemView.findViewById(R.id.txtUnauthorizedCardBody)
        private val btnBlock: Button = itemView.findViewById(R.id.btnUnauthorizedCardBlock)
        private val btnAllow: Button = itemView.findViewById(R.id.btnUnauthorizedCardAllow)
        private val btnCopyMac: Button = itemView.findViewById(R.id.btnUnauthorizedCardCopyMac)
        private val btnCopyIp: Button = itemView.findViewById(R.id.btnUnauthorizedCardCopyIp)
        private val btnSettings: Button = itemView.findViewById(R.id.btnUnauthorizedCardSettings)

        fun bind(item: CardItem) {
            val model = item.model
            val identity = item.identity
            txtTitle.text = "#${identity.displayNumber} PERANGKAT TIDAK DIIZINKAN"
            txtBody.text =
                buildString {
                    append("Nama: ${identity.displayName ?: "-"}")
                    append("\nMAC: ${identity.macAddress ?: "-"}")
                    append("\nIP: ${identity.ipAddress}")
                    append("\nStatus: ${statusLabel(model)}")
                    append("\nTrust: ${identity.trustLevel.name}")
                    append("\nPertama terlihat: ${formatTime(model.firstSeen)}")
                    append("\nTerakhir terlihat: ${formatTime(identity.lastSeen)}")
                    append("\nAlasan: Perangkat ini tersambung ke hotspot tetapi tidak memiliki izin Ghalbit.")
                    append("\nCocokkan MAC ini dengan daftar perangkat hotspot Android, lalu blokir perangkat tersebut.")
                }
            btnBlock.setOnClickListener { onBlock(model) }
            btnAllow.setOnClickListener { onAllow(model) }
            btnCopyMac.setOnClickListener { onCopyMac(model) }
            btnCopyIp.setOnClickListener { onCopyIp(model) }
            btnSettings.setOnClickListener { onOpenSettings(model) }
        }

        private fun formatTime(value: Long): String =
            SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(Date(value))

        private fun statusLabel(model: UnauthorizedClientUiModel): String =
            when (model.authStatus) {
                com.ghalbitnet.meshx2.access.NetworkAccessPolicy.AuthStatus.UNKNOWN_NO_HELLO_AUTH -> "Tidak ada HELLO_AUTH"
                com.ghalbitnet.meshx2.access.NetworkAccessPolicy.AuthStatus.UNAUTHORIZED -> "HELLO_AUTH tidak sah"
                com.ghalbitnet.meshx2.access.NetworkAccessPolicy.AuthStatus.EXPIRED -> "Token tidak valid"
                com.ghalbitnet.meshx2.access.NetworkAccessPolicy.AuthStatus.BLOCKED -> "BLOCKED"
                else -> model.authStatus.name
            }
    }
}
