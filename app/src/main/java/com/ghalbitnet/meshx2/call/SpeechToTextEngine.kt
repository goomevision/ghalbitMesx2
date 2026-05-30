package com.ghalbitnet.meshx2.call

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class SpeechToTextEngine(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private var sequenceNumber = 0

    fun start(callId: String, senderGlobalId: String, onFinal: (AiTranscriptPacket) -> Unit): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w("GHALBIT-STT", "failed")
            return false
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d("GHALBIT-STT", "listening")
                    }

                    override fun onResults(results: Bundle?) {
                        val transcript =
                            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        val confidence = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull() ?: 0f
                        val emergency = EmergencyPhraseDetector.detect(transcript)
                        Log.d("GHALBIT-STT", "final transcript")
                        Log.d("GHALBIT-STT", "confidence=$confidence")
                        onFinal(
                            AiTranscriptPacket(
                                sessionId = callId,
                                senderId = senderGlobalId,
                                text = transcript,
                                priority = if (emergency != null) "CRITICAL" else "NORMAL",
                                emotionHint = emergency,
                                timestamp = System.currentTimeMillis(),
                                sourceMode = AdaptiveVoiceMode.AI_RECONSTRUCTED_SPEECH.name,
                                language = "id-ID",
                                confidence = confidence,
                                sequenceNumber = ++sequenceNumber,
                                signature = "LOCAL_ONLY"
                            )
                        )
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        Log.d("GHALBIT-STT", "partial transcript")
                    }

                    override fun onError(error: Int) {
                        Log.w("GHALBIT-STT", "failed")
                    }

                    override fun onBeginningOfSpeech() = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                })
            }
        }
        recognizer?.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
        )
        return true
    }

    fun stop() {
        recognizer?.stopListening()
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
    }
}
