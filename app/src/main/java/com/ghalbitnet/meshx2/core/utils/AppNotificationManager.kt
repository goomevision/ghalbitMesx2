package com.ghalbitnet.meshx2.core.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.Person
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.call.CallNotificationActionReceiver
import com.ghalbitnet.meshx2.call.CallSessionActivity
import com.ghalbitnet.meshx2.core.log.MeshLogger
import com.ghalbitnet.meshx2.identity.CentralIdentityResolver
import com.ghalbitnet.meshx2.identity.IdentityDiagnosticsFormatter
import com.ghalbitnet.meshx2.identity.IdentityDisplayFormatter
import com.ghalbitnet.meshx2.sos.SosInboxActivity
import java.util.concurrent.ConcurrentHashMap

object AppNotificationManager {
    private const val MESSAGE_CHANNEL_ID = "GHALBIT_CHAT_CHANNEL"
    private const val ALERT_CHANNEL_ID = "GHALBIT_ALERT_CHANNEL"
    private const val CALL_CHANNEL_ID = "GHALBIT_CALL_CHANNEL"
    private const val CHAT_GROUP_KEY = "ghalbit_chat_group"
    const val KEY_TEXT_REPLY = "key_text_reply"
    const val EXTRA_PEER_NAME = "extra_peer_name"
    const val EXTRA_PEER_GLOBAL_ID = "extra_peer_global_id"
    const val EXTRA_PEER_PUBLIC_KEY = "extra_peer_public_key"
    const val EXTRA_PEER_WALLET_ADDRESS = "extra_peer_wallet_address"
    const val EXTRA_PEER_DISPLAY_NAME = "extra_peer_display_name"
    private const val PREFS_NAME = "ghalbit_notification_prefs"
    private const val PREF_CHAT_ENABLED = "chat_enabled"
    private const val PREF_MEDIA_ENABLED = "media_enabled"
    private const val PREF_SOS_ENABLED = "sos_enabled"
    private const val PREF_CALL_ENABLED = "call_enabled"
    private const val PREF_CHAT_QUIET = "chat_quiet"

    private val recentChatLines =
        ConcurrentHashMap<String, MutableList<String>>()

    fun notifyChatMessage(
        context: Context,
        peerName: String,
        message: String,
        isSilent: Boolean = false,
        peerGlobalId: String? = null,
        peerPublicKey: String? = null,
        peerWalletAddress: String? = null,
        peerDisplayName: String? = null,
        messageId: String? = null
    ) {
        ensureChannels(context)
        if (!canNotify(context) || !isChatEnabled(context)) return

        val resolvedIdentity =
            CentralIdentityResolver.resolve(
                context = context,
                legacyChatId = peerName,
                peerName = peerName,
                globalIdHint = peerGlobalId,
                publicKeyHint = peerPublicKey,
                walletAddressHint = peerWalletAddress,
                displayNameHint = peerDisplayName,
                useKeyStore = false
            )
        val resolvedGlobalId = resolvedIdentity.globalId
        val resolvedPublicKey = resolvedIdentity.publicKey
        val resolvedWalletAddress = resolvedIdentity.walletAddress
        val resolvedDisplayName = resolvedIdentity.primaryLabel

        MeshLogger.i(
            "NOTIFICATION_CHAT_IDENTITY",
            IdentityDiagnosticsFormatter.formatResolved(resolvedIdentity)
        )

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                peerName.hashCode(),
                GhalbitDeepLinkRouter.chatIntent(
                    context = context,
                    conversationId = peerName,
                    senderGlobalId = resolvedGlobalId,
                    senderPublicKey = resolvedPublicKey,
                    senderWalletAddress = resolvedWalletAddress,
                    senderDisplayName = resolvedDisplayName,
                    messageId = messageId
                ).apply {
                    putExtra("peerName", peerName)
                    putExtra("peerIp", "")
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val history =
            recentChatLines.getOrPut(peerName) { mutableListOf() }
        history.add(message)
        while (history.size > 5) {
            history.removeAt(0)
        }

        val person =
            Person.Builder()
                .setName(resolvedDisplayName)
                .build()

        val style =
            NotificationCompat.MessagingStyle(person)
                .setConversationTitle(resolvedDisplayName)
        history.forEach {
            style.addMessage(it, System.currentTimeMillis(), person)
        }

        val replyIntent =
            Intent(context, ChatReplyReceiver::class.java).apply {
                putExtra(EXTRA_PEER_NAME, peerName)
                putExtra(EXTRA_PEER_GLOBAL_ID, resolvedGlobalId)
                putExtra(EXTRA_PEER_PUBLIC_KEY, resolvedPublicKey)
                putExtra(EXTRA_PEER_WALLET_ADDRESS, resolvedWalletAddress)
                putExtra(EXTRA_PEER_DISPLAY_NAME, resolvedDisplayName)
            }

        val replyPendingIntent =
            PendingIntent.getBroadcast(
                context,
                ("REPLY:$peerName").hashCode(),
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

        val remoteInput =
            RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel(context.getString(R.string.notification_action_reply_hint))
                .build()

        val markReadIntent =
            Intent(context, ChatMarkReadReceiver::class.java).apply {
                putExtra(EXTRA_PEER_NAME, peerName)
                putExtra(EXTRA_PEER_GLOBAL_ID, resolvedGlobalId)
            }

        val markReadPendingIntent =
            PendingIntent.getBroadcast(
                context,
                ("READ:$peerName").hashCode(),
                markReadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val builder =
            NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(resolvedDisplayName)
                .setContentText(message)
                .setStyle(style)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setGroup(CHAT_GROUP_KEY)
                .setGroupSummary(false)
                .setNumber(history.size)
                .addAction(
                    0,
                    context.getString(R.string.notification_action_open_chat),
                    pendingIntent
                )
                .addAction(
                    NotificationCompat.Action.Builder(
                        0,
                        context.getString(R.string.notification_action_reply),
                        replyPendingIntent
                    ).addRemoteInput(remoteInput).build()
                )
                .addAction(
                    0,
                    context.getString(R.string.notification_action_mark_read),
                    markReadPendingIntent
                )
                .setPriority(
                    if (isSilent) NotificationCompat.PRIORITY_DEFAULT
                    else NotificationCompat.PRIORITY_HIGH
                )
                .setOnlyAlertOnce(true)

        if (isSilent || isChatQuiet(context)) {
            builder.setSilent(true)
        }

        val manager = NotificationManagerCompat.from(context)
        manager.notify(("CHAT:$peerName").hashCode(), builder.build())
        manager.notify(CHAT_GROUP_KEY.hashCode(), buildChatSummaryNotification(context))
        Log.d("GHALBIT-NOTIFY", "message shown id=${messageId ?: peerName}")
    }

    fun notifySos(
        context: Context,
        peerName: String,
        payload: String,
        peerGlobalId: String? = null,
        peerPublicKey: String? = null,
        peerWalletAddress: String? = null,
        peerDisplayName: String? = null
    ) {
        ensureChannels(context)
        if (!canNotify(context) || !isSosEnabled(context)) return

        val resolvedIdentity =
            CentralIdentityResolver.resolve(
                context = context,
                legacyChatId = peerName,
                peerName = peerName,
                globalIdHint = peerGlobalId,
                publicKeyHint = peerPublicKey,
                walletAddressHint = peerWalletAddress,
                displayNameHint = peerDisplayName,
                useKeyStore = false
            )
        val resolvedDisplayName = resolvedIdentity.primaryLabel

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                ("SOS:$peerName").hashCode(),
                Intent(context, SosInboxActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        NotificationManagerCompat.from(context).notify(
            ("SOS:$peerName").hashCode(),
            NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.notification_sos_title, resolvedDisplayName))
                .setContentText(payload)
                .setStyle(NotificationCompat.BigTextStyle().bigText(payload))
                .setContentIntent(pendingIntent)
                .addAction(
                    0,
                    context.getString(R.string.notification_action_open_app),
                    pendingIntent
                )
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        )
    }

    fun notifyIncomingCall(
        context: Context,
        peerName: String,
        peerIp: String,
        callId: String,
        peerGlobalId: String? = null,
        peerPublicKey: String? = null,
        peerWalletAddress: String? = null,
        peerDisplayName: String? = null
    ) {
        ensureChannels(context)
        if (!canNotify(context) || !isCallEnabled(context)) return

        val resolvedIdentity =
            CentralIdentityResolver.resolve(
                context = context,
                legacyChatId = peerName,
                peerName = peerName,
                peerIp = peerIp,
                globalIdHint = peerGlobalId,
                publicKeyHint = peerPublicKey,
                walletAddressHint = peerWalletAddress,
                displayNameHint = peerDisplayName,
                useKeyStore = false
            )
        val resolvedDisplayName = resolvedIdentity.primaryLabel

        val openIntent =
            Intent(context, CallNotificationActionReceiver::class.java).apply {
                action = GhalbitDeepLinkRouter.ACTION_OPEN_CALL
                putExtra(CallSessionActivity.EXTRA_PEER_NAME, peerName)
                putExtra(CallSessionActivity.EXTRA_PEER_IP, peerIp)
                putExtra(CallSessionActivity.EXTRA_CALL_ID, callId)
                putExtra(CallSessionActivity.EXTRA_PEER_GLOBAL_ID, resolvedIdentity.globalId ?: peerGlobalId)
                putExtra(CallSessionActivity.EXTRA_PEER_PUBLIC_KEY, resolvedIdentity.publicKey ?: peerPublicKey)
                putExtra(CallSessionActivity.EXTRA_PEER_WALLET_ADDRESS, resolvedIdentity.walletAddress ?: peerWalletAddress)
                putExtra(CallSessionActivity.EXTRA_PEER_DISPLAY_NAME, resolvedDisplayName)
            }
        val acceptIntent =
            Intent(context, CallNotificationActionReceiver::class.java).apply {
                action = GhalbitDeepLinkRouter.ACTION_ACCEPT_CALL
                putExtras(openIntent.extras ?: android.os.Bundle())
            }
        val rejectIntent =
            Intent(context, CallNotificationActionReceiver::class.java).apply {
                action = GhalbitDeepLinkRouter.ACTION_REJECT_CALL
                putExtras(openIntent.extras ?: android.os.Bundle())
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                ("CALL:$callId").hashCode(),
                GhalbitDeepLinkRouter.callIntent(
                    context = context,
                    peerName = peerName,
                    peerIp = peerIp,
                    callId = callId,
                    peerGlobalId = resolvedIdentity.globalId ?: peerGlobalId,
                    peerPublicKey = resolvedIdentity.publicKey ?: peerPublicKey,
                    peerWalletAddress = resolvedIdentity.walletAddress ?: peerWalletAddress,
                    peerDisplayName = resolvedDisplayName,
                    action = GhalbitDeepLinkRouter.ACTION_OPEN_CALL
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        val acceptPendingIntent =
            PendingIntent.getBroadcast(
                context,
                ("CALL_ACCEPT:$callId").hashCode(),
                acceptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        val rejectPendingIntent =
            PendingIntent.getBroadcast(
                context,
                ("CALL_REJECT:$callId").hashCode(),
                rejectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        NotificationManagerCompat.from(context).notify(
            ("CALL:$callId").hashCode(),
            NotificationCompat.Builder(context, CALL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.notification_call_title, resolvedDisplayName))
                .setContentText(context.getString(R.string.notification_call_text))
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
                .addAction(
                    0,
                    context.getString(R.string.notification_action_answer),
                    acceptPendingIntent
                )
                .addAction(
                    0,
                    context.getString(R.string.call_reject),
                    rejectPendingIntent
                )
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOnlyAlertOnce(true)
                .build()
        )
        Log.d("GHALBIT-CALL", "notification shown callId=$callId")
    }

    fun notifyMissedCall(
        context: Context,
        peerName: String,
        peerGlobalId: String? = null,
        peerPublicKey: String? = null,
        peerWalletAddress: String? = null,
        peerDisplayName: String? = null
    ) {
        ensureChannels(context)
        if (!canNotify(context) || !isCallEnabled(context)) return

        val resolvedIdentity =
            CentralIdentityResolver.resolve(
                context = context,
                legacyChatId = peerName,
                peerName = peerName,
                globalIdHint = peerGlobalId,
                publicKeyHint = peerPublicKey,
                walletAddressHint = peerWalletAddress,
                displayNameHint = peerDisplayName,
                useKeyStore = false
            )
        val resolvedDisplayName = resolvedIdentity.primaryLabel

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                ("MISSED:$peerName").hashCode(),
                GhalbitDeepLinkRouter.chatIntent(
                    context = context,
                    conversationId = peerName,
                    senderGlobalId = resolvedIdentity.globalId ?: peerGlobalId,
                    senderPublicKey = resolvedIdentity.publicKey ?: peerPublicKey,
                    senderWalletAddress = resolvedIdentity.walletAddress ?: peerWalletAddress,
                    senderDisplayName = resolvedDisplayName
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        NotificationManagerCompat.from(context).notify(
            ("MISSED:$peerName").hashCode(),
            NotificationCompat.Builder(context, CALL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.notification_missed_call_title, resolvedDisplayName))
                .setContentText(context.getString(R.string.notification_missed_call_text))
                .setContentIntent(pendingIntent)
                .addAction(
                    0,
                    context.getString(R.string.notification_action_open_chat),
                    pendingIntent
                )
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        )
    }

    fun clearChatNotifications(
        context: Context,
        peerName: String
    ) {
        NotificationManagerCompat.from(context)
            .cancel(("CHAT:$peerName").hashCode())
        recentChatLines.remove(peerName)
        if (recentChatLines.isEmpty()) {
            NotificationManagerCompat.from(context)
                .cancel(CHAT_GROUP_KEY.hashCode())
        } else {
            NotificationManagerCompat.from(context)
                .notify(CHAT_GROUP_KEY.hashCode(), buildChatSummaryNotification(context))
        }
    }

    private fun buildChatSummaryNotification(context: Context): android.app.Notification {
        val inboxStyle =
            NotificationCompat.InboxStyle()
                .setSummaryText(
                    context.getString(
                        R.string.notification_chat_summary_count,
                        recentChatLines.size
                    )
                )

        recentChatLines.forEach { (peer, lines) ->
            val latest =
                lines.lastOrNull().orEmpty()
            val resolvedIdentity =
                CentralIdentityResolver.resolve(
                    context = context,
                    legacyChatId = peer,
                    peerName = peer,
                    useKeyStore = false
                )
            inboxStyle.addLine("${resolvedIdentity.primaryLabel}: $latest")
        }

        return NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notification_chat_summary_title))
            .setContentText(
                context.getString(
                    R.string.notification_chat_summary_count,
                    recentChatLines.size
                )
            )
            .setStyle(inboxStyle)
            .setGroup(CHAT_GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager =
            context.getSystemService(NotificationManager::class.java)

        val channels =
            listOf(
                NotificationChannel(
                    MESSAGE_CHANNEL_ID,
                    "Pesan Ghalbit Mesh",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifikasi pesan dan media masuk"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 120, 80, 120)
                    setSound(null, null)
                },
                NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Peringatan Ghalbit Mesh",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifikasi SOS dan peringatan penting"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 120, 250, 120, 250)
                },
                NotificationChannel(
                    CALL_CHANNEL_ID,
                    "Panggilan Ghalbit Mesh",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifikasi panggilan masuk dan tak terjawab"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 250, 400, 250, 400)
                    setSound(null, null)
                    Log.d("GHALBIT-CALL-NOTIFY", "channel sound disabled manual ringtone")
                }
            )

        channels.forEach(manager::createNotificationChannel)
        Log.d("GHALBIT-CALL-NOTIFY", "onlyAlertOnce enabled")
    }

    private fun canNotify(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun isChatEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(PREF_CHAT_ENABLED, true)
    }

    fun setChatEnabled(
        context: Context,
        enabled: Boolean
    ) {
        prefs(context).edit().putBoolean(PREF_CHAT_ENABLED, enabled).apply()
    }

    fun isMediaEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(PREF_MEDIA_ENABLED, true)
    }

    fun setMediaEnabled(
        context: Context,
        enabled: Boolean
    ) {
        prefs(context).edit().putBoolean(PREF_MEDIA_ENABLED, enabled).apply()
    }

    fun isSosEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(PREF_SOS_ENABLED, true)
    }

    fun setSosEnabled(
        context: Context,
        enabled: Boolean
    ) {
        prefs(context).edit().putBoolean(PREF_SOS_ENABLED, enabled).apply()
    }

    fun isCallEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(PREF_CALL_ENABLED, true)
    }

    fun setCallEnabled(
        context: Context,
        enabled: Boolean
    ) {
        prefs(context).edit().putBoolean(PREF_CALL_ENABLED, enabled).apply()
    }

    fun isChatQuiet(context: Context): Boolean {
        return prefs(context).getBoolean(PREF_CHAT_QUIET, false)
    }

    fun setChatQuiet(
        context: Context,
        enabled: Boolean
    ) {
        prefs(context).edit().putBoolean(PREF_CHAT_QUIET, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
