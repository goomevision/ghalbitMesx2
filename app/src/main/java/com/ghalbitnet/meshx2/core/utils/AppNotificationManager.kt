package com.ghalbitnet.meshx2.core.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioAttributes
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.Person
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.ghalbitnet.meshx2.MainActivity
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.call.CallSessionActivity
import com.ghalbitnet.meshx2.chat.ChatActivity
import com.ghalbitnet.meshx2.chat.ContactAliasManager
import com.ghalbitnet.meshx2.chat.SavedContactsActivity
import com.ghalbitnet.meshx2.token.WalletActivity
import java.util.concurrent.ConcurrentHashMap

object AppNotificationManager {
    private const val MESSAGE_CHANNEL_ID = "GHALBIT_CHAT_CHANNEL"
    private const val ALERT_CHANNEL_ID = "GHALBIT_ALERT_CHANNEL"
    private const val CALL_CHANNEL_ID = "GHALBIT_CALL_CHANNEL"
    private const val CHAT_GROUP_KEY = "ghalbit_chat_group"
    private const val LOW_BALANCE_NOTIFICATION_ID = 64226
    const val KEY_TEXT_REPLY = "key_text_reply"
    const val EXTRA_PEER_NAME = "extra_peer_name"
    private const val PREFS_NAME = "ghalbit_notification_prefs"
    private const val PREF_CHAT_ENABLED = "chat_enabled"
    private const val PREF_MEDIA_ENABLED = "media_enabled"
    private const val PREF_SOS_ENABLED = "sos_enabled"
    private const val PREF_CALL_ENABLED = "call_enabled"
    private const val PREF_CHAT_QUIET = "chat_quiet"
    private const val PREF_LOW_BALANCE_LAST_AT = "low_balance_last_at"

    private val recentChatLines =
        ConcurrentHashMap<String, MutableList<String>>()

    fun notifyChatMessage(
        context: Context,
        peerName: String,
        message: String,
        isSilent: Boolean = false
    ) {
        ensureChannels(context)
        if (!canNotify(context) || !isChatEnabled(context)) return
        val displayName =
            ContactAliasManager.getDisplayName(context, peerName)

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                peerName.hashCode(),
                chatIntent(context, peerName).apply {
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
                .setName(displayName)
                .build()

        val style =
            NotificationCompat.MessagingStyle(person)
                .setConversationTitle(displayName)
        history.forEach {
            style.addMessage(it, System.currentTimeMillis(), person)
        }

        val replyIntent =
            Intent(context, ChatReplyReceiver::class.java).apply {
                putExtra(EXTRA_PEER_NAME, peerName)
            }

        val replyPendingIntent =
            PendingIntent.getBroadcast(
                context,
                ("REPLY:$peerName").hashCode(),
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val remoteInput =
            RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel(context.getString(R.string.notification_action_reply_hint))
                .build()

        val markReadIntent =
            Intent(context, ChatMarkReadReceiver::class.java).apply {
                putExtra(EXTRA_PEER_NAME, peerName)
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
                .setContentTitle(displayName)
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

        if (isSilent || isChatQuiet(context)) {
            builder.setSilent(true)
        }

        val manager = NotificationManagerCompat.from(context)
        manager.notify(("CHAT:$peerName").hashCode(), builder.build())
        manager.notify(CHAT_GROUP_KEY.hashCode(), buildChatSummaryNotification(context))
    }

    fun notifySos(
        context: Context,
        peerName: String,
        payload: String
    ) {
        ensureChannels(context)
        if (!canNotify(context) || !isSosEnabled(context)) return
        val displayName =
            ContactAliasManager.getDisplayName(context, peerName)

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                ("SOS:$peerName").hashCode(),
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        NotificationManagerCompat.from(context).notify(
            ("SOS:$peerName").hashCode(),
            NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.notification_sos_title, displayName))
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
        callId: String
    ) {
        ensureChannels(context)
        if (!canNotify(context) || !isCallEnabled(context)) return
        val displayName =
            ContactAliasManager.getDisplayName(context, peerName)

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                ("CALL:$callId").hashCode(),
                CallSessionActivity.createIntent(
                    context = context,
                    peerName = peerName,
                    peerIp = peerIp,
                    callId = callId,
                    incoming = true
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        NotificationManagerCompat.from(context).notify(
            ("CALL:$callId").hashCode(),
            NotificationCompat.Builder(context, CALL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.notification_call_title, displayName))
                .setContentText(context.getString(R.string.notification_call_text))
                .setContentIntent(pendingIntent)
                .addAction(
                    0,
                    context.getString(R.string.notification_action_answer),
                    pendingIntent
                )
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        )
    }

    fun notifyMissedCall(
        context: Context,
        peerName: String
    ) {
        ensureChannels(context)
        if (!canNotify(context) || !isCallEnabled(context)) return
        val displayName =
            ContactAliasManager.getDisplayName(context, peerName)

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                ("MISSED:$peerName").hashCode(),
                Intent(context, ChatActivity::class.java).apply {
                    putExtra("peerName", peerName)
                    putExtra("peerIp", "")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        NotificationManagerCompat.from(context).notify(
            ("MISSED:$peerName").hashCode(),
            NotificationCompat.Builder(context, CALL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.notification_missed_call_title, displayName))
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

    fun notifyLowWalletBalance(
        context: Context,
        balance: Double,
        minimumRequired: Double
    ) {
        ensureChannels(context)
        if (!canNotify(context)) return

        val now = System.currentTimeMillis()
        val lastAt = prefs(context).getLong(PREF_LOW_BALANCE_LAST_AT, 0L)
        if (now - lastAt < 10 * 60 * 1000L) {
            return
        }
        prefs(context).edit().putLong(PREF_LOW_BALANCE_LAST_AT, now).apply()

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                LOW_BALANCE_NOTIFICATION_ID,
                Intent(context, WalletActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val walletPendingIntent =
            PendingIntent.getActivity(
                context,
                LOW_BALANCE_NOTIFICATION_ID + 1,
                Intent(context, WalletActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val contactsPendingIntent =
            PendingIntent.getActivity(
                context,
                LOW_BALANCE_NOTIFICATION_ID + 2,
                Intent(context, SavedContactsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        NotificationManagerCompat.from(context).notify(
            LOW_BALANCE_NOTIFICATION_ID,
            NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.notification_wallet_low_title))
                .setContentText(
                    context.getString(
                        R.string.notification_wallet_low_text,
                        balance,
                        minimumRequired
                    )
                )
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        context.getString(
                            R.string.notification_wallet_low_text,
                            balance,
                            minimumRequired
                        )
                    )
                )
                .setContentIntent(pendingIntent)
                .addAction(
                    0,
                    context.getString(R.string.notification_action_open_wallet),
                    walletPendingIntent
                )
                .addAction(
                    0,
                    context.getString(R.string.notification_action_open_contacts),
                    contactsPendingIntent
                )
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        )
    }

    fun clearLowWalletBalanceNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(LOW_BALANCE_NOTIFICATION_ID)
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
            val displayName =
                ContactAliasManager.getDisplayName(context, peer)
            inboxStyle.addLine("$displayName: $latest")
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

    private fun chatIntent(
        context: Context,
        peerName: String
    ): Intent {
        return Intent(context, ChatActivity::class.java).apply {
            putExtra("peerName", peerName)
            putExtra("peerIp", "")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
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
                    val ringtoneUri: Uri? =
                        android.media.RingtoneManager.getDefaultUri(
                            android.media.RingtoneManager.TYPE_RINGTONE
                        )
                    val attributes =
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .build()
                    setSound(ringtoneUri, attributes)
                }
            )

        channels.forEach(manager::createNotificationChannel)
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
