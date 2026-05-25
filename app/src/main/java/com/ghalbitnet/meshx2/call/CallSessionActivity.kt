package com.ghalbitnet.meshx2.call

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ghalbitnet.meshx2.MainActivity
import com.ghalbitnet.meshx2.R
import com.ghalbitnet.meshx2.chat.ContactAliasManager
import com.ghalbitnet.meshx2.chat.ChatDatabase
import com.ghalbitnet.meshx2.chat.ChatMessage
import com.ghalbitnet.meshx2.core.log.MeshLogger
import com.ghalbitnet.meshx2.core.utils.AppNotificationManager
import com.ghalbitnet.meshx2.core.utils.UiFeedbackManager
import com.ghalbitnet.meshx2.file.FileTransferManager
import com.ghalbitnet.meshx2.model.MeshPacket
import com.ghalbitnet.meshx2.network.ReliablePacketSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

class CallSessionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PEER_NAME = "peerName"
        const val EXTRA_PEER_IP = "peerIp"
        const val EXTRA_CALL_ID = "callId"
        const val EXTRA_INCOMING = "incoming"
        private const val MIN_VOICE_DURATION_MS = 700L
        private const val CALL_TIMEOUT_MS = 30_000L

        fun createIntent(
            context: Context,
            peerName: String,
            peerIp: String,
            callId: String,
            incoming: Boolean
        ): Intent {
            return Intent(context, CallSessionActivity::class.java).apply {
                putExtra(EXTRA_PEER_NAME, peerName)
                putExtra(EXTRA_PEER_IP, peerIp)
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_INCOMING, incoming)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }

    private lateinit var txtCallTitle: TextView
    private lateinit var txtCallStatus: TextView
    private lateinit var btnAccept: Button
    private lateinit var btnReject: Button
    private lateinit var btnTalk: Button
    private lateinit var btnEnd: Button

    private var peerName: String = ""
    private var peerIp: String = ""
    private var callId: String = ""
    private var incoming = false
    private var connected = false
    private var displayName: String = ""

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentRecordingFile: File? = null
    private var isRecording = false
    private var recordStartAt = 0L
    private var recordingStatusJob: Job? = null
    private var callTimeoutJob: Job? = null
    private var pendingVoiceStart = false
    private var incomingRingtone: Ringtone? = null

    private val audioPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted && pendingVoiceStart) {
                startTalkRecording()
            } else if (!granted) {
                txtCallStatus.text = getString(R.string.chat_voice_permission)
                UiFeedbackManager.showToast(
                    this,
                    getString(R.string.chat_voice_permission)
                )
            }
            pendingVoiceStart = false
        }

    private val packetReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                val source =
                    intent?.getStringExtra("source") ?: return
                val type =
                    intent.getStringExtra("type") ?: return
                val payload =
                    intent.getStringExtra("payload") ?: ""

                if (source != peerName) {
                    return
                }

                when (type) {
                    "CALL_ACCEPT" -> {
                        if (extractCallId(payload) == callId) {
                            connected = true
                            stopCallTimeout()
                            stopIncomingRingtone()
                            txtCallStatus.text = getString(R.string.call_connected)
                            updateButtons()
                        }
                    }

                    "CALL_REJECT" -> {
                        if (extractCallId(payload) == callId) {
                            stopCallTimeout()
                            stopIncomingRingtone()
                            txtCallStatus.text = getString(R.string.call_rejected)
                            saveCallNote(
                                content = getString(R.string.call_note_rejected),
                                isSent = true,
                                status = "REJECTED"
                            )
                            UiFeedbackManager.showToast(
                                this@CallSessionActivity,
                                getString(R.string.call_rejected)
                            )
                            finish()
                        }
                    }

                    "CALL_END" -> {
                        if (extractCallId(payload) == callId) {
                            stopCallTimeout()
                            stopIncomingRingtone()
                            txtCallStatus.text = getString(R.string.call_ended)
                            finish()
                        }
                    }

                    "CALL_BUSY" -> {
                        if (extractCallId(payload) == callId) {
                            stopCallTimeout()
                            stopIncomingRingtone()
                            txtCallStatus.text = getString(R.string.call_busy_remote)
                            saveCallNote(
                                content = getString(R.string.call_note_busy_remote),
                                isSent = true,
                                status = "BUSY"
                            )
                            UiFeedbackManager.showToast(
                                this@CallSessionActivity,
                                getString(R.string.call_busy_remote)
                            )
                            finish()
                        }
                    }
                }
            }
        }

    private val audioReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                val source =
                    intent?.getStringExtra(FileTransferManager.EXTRA_AUDIO_SOURCE)
                        ?: return

                if (source != peerName || !connected) {
                    return
                }

                val filePath =
                    intent.getStringExtra(FileTransferManager.EXTRA_AUDIO_FILE_PATH)
                        ?: return

                val label =
                    intent.getStringExtra(FileTransferManager.EXTRA_AUDIO_LABEL)
                        ?: getString(R.string.chat_voice_label)

                playIncomingAudio(filePath, label)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_call_session)

        peerName =
            intent.getStringExtra(EXTRA_PEER_NAME) ?: "UNKNOWN"
        peerIp =
            intent.getStringExtra(EXTRA_PEER_IP) ?: ""
        displayName =
            ContactAliasManager.getDisplayName(this, peerName)
        callId =
            intent.getStringExtra(EXTRA_CALL_ID) ?: UUID.randomUUID().toString()
        incoming =
            intent.getBooleanExtra(EXTRA_INCOMING, false)

        VoiceCallRegistry.start(callId, peerName, peerIp)

        txtCallTitle = findViewById(R.id.txtCallTitle)
        txtCallStatus = findViewById(R.id.txtCallStatus)
        btnAccept = findViewById(R.id.btnAcceptCall)
        btnReject = findViewById(R.id.btnRejectCall)
        btnTalk = findViewById(R.id.btnTalkCall)
        btnEnd = findViewById(R.id.btnEndCall)

        txtCallTitle.text = getString(R.string.call_with, displayName)
        txtCallStatus.text =
            if (incoming) {
                getString(R.string.call_ringing)
            } else {
                getString(R.string.call_calling)
            }

        btnAccept.setOnClickListener {
            acceptCall()
        }

        btnReject.setOnClickListener {
            rejectCall()
        }

        btnEnd.setOnClickListener {
            endCall()
        }

        btnTalk.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    beginTalk()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    finishTalk(sendAudio = true)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    finishTalk(sendAudio = false)
                    true
                }
                else -> false
            }
        }

        if (!incoming) {
            sendCallSignal("CALL_INVITE")
            startCallTimeout()
        } else {
            startIncomingRingtone()
            startCallTimeout()
        }

        updateButtons()
    }

    override fun onResume() {
        super.onResume()

        LocalBroadcastManager.getInstance(this).registerReceiver(
            packetReceiver,
            IntentFilter("com.ghalbitnet.meshx2.NEW_MESH_PACKET")
        )

        LocalBroadcastManager.getInstance(this).registerReceiver(
            audioReceiver,
            IntentFilter(FileTransferManager.ACTION_AUDIO_MESSAGE_RECEIVED)
        )
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(packetReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(audioReceiver)
        super.onPause()
    }

    override fun onDestroy() {
        stopTalkRecording(cancelOnly = true)
        recordingStatusJob?.cancel()
        recordingStatusJob = null
        stopCallTimeout()
        stopIncomingRingtone()
        releasePlayer()
        VoiceCallRegistry.clear()
        super.onDestroy()
    }

    private fun acceptCall() {
        connected = true
        incoming = false
        stopCallTimeout()
        stopIncomingRingtone()
        txtCallStatus.text = getString(R.string.call_connecting)
        sendCallSignal("CALL_ACCEPT")
        updateButtons()
        txtCallStatus.text = getString(R.string.call_connected)
    }

    private fun rejectCall() {
        stopCallTimeout()
        stopIncomingRingtone()
        sendCallSignal("CALL_REJECT")
        txtCallStatus.text = getString(R.string.call_rejected)
        finish()
    }

    private fun endCall() {
        stopCallTimeout()
        stopIncomingRingtone()
        sendCallSignal("CALL_END")
        txtCallStatus.text = getString(R.string.call_ended)
        finish()
    }

    private fun updateButtons() {
        btnAccept.isEnabled = incoming && !connected
        btnAccept.alpha = if (incoming && !connected) 1f else 0.5f
        btnReject.isEnabled = incoming && !connected
        btnReject.alpha = if (incoming && !connected) 1f else 0.5f
        btnTalk.isEnabled = connected
        btnTalk.alpha = if (connected) 1f else 0.5f
        btnEnd.isEnabled = connected || !incoming
        btnEnd.alpha = if (connected || !incoming) 1f else 0.5f
    }

    private fun startCallTimeout() {
        stopCallTimeout()
        callTimeoutJob =
            lifecycleScope.launch {
                delay(CALL_TIMEOUT_MS)
                if (connected || isFinishing || isDestroyed) {
                    return@launch
                }

                stopIncomingRingtone()
                val timeoutStatus =
                    if (incoming) {
                        getString(R.string.call_missed)
                    } else {
                        getString(R.string.call_no_answer)
                    }

                txtCallStatus.text = timeoutStatus
                saveCallNote(
                    content = if (incoming) {
                        getString(R.string.call_note_missed)
                    } else {
                        getString(R.string.call_note_no_answer)
                    },
                    isSent = !incoming,
                    status = if (incoming) "MISSED" else "NO_ANSWER"
                )
                if (incoming) {
                    AppNotificationManager.notifyMissedCall(
                        context = applicationContext,
                        peerName = peerName
                    )
                }
                sendCallSignal("CALL_END")
                UiFeedbackManager.showToast(this@CallSessionActivity, timeoutStatus)
                delay(600)
                finish()
            }
    }

    private fun stopCallTimeout() {
        callTimeoutJob?.cancel()
        callTimeoutJob = null
    }

    private fun startIncomingRingtone() {
        if (!incoming || connected) {
            return
        }

        try {
            val ringtoneUri =
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: return

            incomingRingtone = RingtoneManager.getRingtone(this, ringtoneUri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                }
                play()
            }
        } catch (e: Exception) {
            MeshLogger.e("CALL", "Incoming ringtone failed", e)
        }
    }

    private fun stopIncomingRingtone() {
        try {
            incomingRingtone?.stop()
        } catch (_: Exception) {
        }
        incomingRingtone = null
    }

    private fun sendCallSignal(type: String) {
        if (peerIp.isBlank()) {
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val payload =
                    JSONObject()
                        .put("callId", callId)
                        .put("peerName", MainActivity.myGlobalPeerId)
                        .toString()

                val packet =
                    MeshPacket(
                        packetId = "$type-${System.currentTimeMillis()}",
                        source = MainActivity.myGlobalPeerId,
                        destination = peerName,
                        type = type,
                        payload = payload,
                        encrypted = false
                    )

                ReliablePacketSender.sendWithRetry(
                    peerIp,
                    packet
                )
            } catch (e: Exception) {
                MeshLogger.e("CALL", "Signal send failed", e)
            }
        }
    }

    private fun beginTalk() {
        if (!connected) {
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingVoiceStart = true
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        startTalkRecording()
    }

    private fun finishTalk(sendAudio: Boolean) {
        pendingVoiceStart = false
        if (!isRecording) {
            return
        }
        stopTalkRecording(cancelOnly = !sendAudio)
    }

    private fun startTalkRecording() {
        if (isRecording) {
            return
        }

        try {
            val voiceDir = File(cacheDir, "call_voice")
            if (!voiceDir.exists()) {
                voiceDir.mkdirs()
            }

            val outputFile =
                File(voiceDir, "call_${System.currentTimeMillis()}.m4a")

            val recorder =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(this)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(22050)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            currentRecordingFile = outputFile
            isRecording = true
            recordStartAt = System.currentTimeMillis()
            btnTalk.text = getString(R.string.call_release_to_send)
            startRecordingTicker()
        } catch (e: Exception) {
            MeshLogger.e("CALL", "Record start failed", e)
            txtCallStatus.text = getString(R.string.chat_voice_failed)
            stopTalkRecording(cancelOnly = true)
        }
    }

    private fun stopTalkRecording(cancelOnly: Boolean) {
        val recorder = mediaRecorder
        val audioFile = currentRecordingFile
        mediaRecorder = null
        currentRecordingFile = null
        val durationMs =
            System.currentTimeMillis() - recordStartAt
        recordStartAt = 0L
        isRecording = false
        recordingStatusJob?.cancel()
        recordingStatusJob = null
        btnTalk.text = getString(R.string.call_hold_to_talk)

        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.reset() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}

        if (cancelOnly || audioFile == null || !audioFile.exists()) {
            audioFile?.delete()
            return
        }

        if (durationMs < MIN_VOICE_DURATION_MS) {
            audioFile.delete()
            txtCallStatus.text = getString(R.string.chat_voice_too_short)
            return
        }

        txtCallStatus.text =
            getString(R.string.call_sending_voice)
        sendCallAudio(audioFile)
    }

    private fun sendCallAudio(audioFile: File) {
        FileTransferManager.sendFile(
            context = this,
            fileUri = Uri.fromFile(audioFile),
            destinationPeerId = peerName,
            keyStore = com.ghalbitnet.meshx2.security.KeyStoreManager(this),
            myPeerId = MainActivity.myGlobalPeerId,
            listener =
                object : FileTransferManager.TransferStatusListener {
                    override fun onProgress(message: String, busy: Boolean) {
                        runOnUiThread {
                            txtCallStatus.text = getString(R.string.call_sending_voice)
                        }
                    }

                    override fun onComplete(message: String) {
                        runOnUiThread {
                            txtCallStatus.text = getString(R.string.call_connected)
                        }
                    }

                    override fun onError(message: String) {
                        runOnUiThread {
                            txtCallStatus.text = getString(R.string.chat_voice_failed)
                            UiFeedbackManager.showToast(
                                this@CallSessionActivity,
                                getString(R.string.chat_voice_failed)
                            )
                        }
                    }
                }
        )
    }

    private fun playIncomingAudio(
        filePath: String,
        label: String
    ) {
        try {
            releasePlayer()
            mediaPlayer =
                MediaPlayer().apply {
                    setDataSource(filePath)
                    setOnCompletionListener {
                        releasePlayer()
                        txtCallStatus.text = getString(R.string.call_connected)
                    }
                    prepare()
                    start()
                }
            txtCallStatus.text =
                "${getString(R.string.call_audio_incoming)} $label"
        } catch (e: Exception) {
            MeshLogger.e("CALL", "Play incoming audio failed", e)
        }
    }

    private fun releasePlayer() {
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun startRecordingTicker() {
        recordingStatusJob?.cancel()
        recordingStatusJob =
            lifecycleScope.launch {
                while (isRecording) {
                    val elapsed =
                        System.currentTimeMillis() - recordStartAt
                    txtCallStatus.text =
                        "${getString(R.string.chat_recording)} ${formatDuration(elapsed)}"
                    delay(250)
                }
            }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds =
            (durationMs / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun extractCallId(payload: String): String? {
        return try {
            JSONObject(payload).optString("callId")
        } catch (_: Exception) {
            null
        }
    }

    private fun saveCallNote(
        content: String,
        isSent: Boolean,
        status: String
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                ChatDatabase.getInstance(applicationContext)
                    .chatDao()
                    .insertMessage(
                        ChatMessage(
                            packetId = "CALL-NOTE-${callId}-${status}-${System.currentTimeMillis()}",
                            chatId = peerName,
                            senderName = if (isSent) "ME" else peerName,
                            content = content,
                            contentType = "CALL",
                            isSent = isSent,
                            status = status
                        )
                    )
            } catch (e: Exception) {
                MeshLogger.e("CALL", "Save call note failed", e)
            }
        }
    }
}
