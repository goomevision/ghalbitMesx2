package com.ghalbitnet.meshx2.settings

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

object WifiHotspotQrProofManager {

    private const val PREFS_NAME = "hotspot_qr_proof"
    private const val KEY_SIGNATURE = "signature"
    private const val KEY_LAST_PAYLOAD = "last_payload"

    data class WifiQrData(
        val ssid: String,
        val password: String,
        val securityType: String
    )

    fun hasValidProof(
        context: Context,
        ssid: String,
        password: String
    ): Boolean {
        val signature = prefs(context).getString(KEY_SIGNATURE, "").orEmpty()
        return signature == signature(ssid, password)
    }

    fun markProof(
        context: Context,
        ssid: String,
        password: String,
        payload: String
    ) {
        prefs(context)
            .edit()
            .putString(KEY_SIGNATURE, signature(ssid, password))
            .putString(KEY_LAST_PAYLOAD, payload)
            .apply()
    }

    fun markProofFromCredentials(
        context: Context,
        ssid: String,
        password: String
    ) {
        val lastPayload = prefs(context).getString(KEY_LAST_PAYLOAD, "").orEmpty()
        prefs(context)
            .edit()
            .putString(KEY_SIGNATURE, signature(ssid, password))
            .putString(KEY_LAST_PAYLOAD, lastPayload)
            .apply()
    }

    fun invalidate(context: Context) {
        prefs(context)
            .edit()
            .remove(KEY_SIGNATURE)
            .remove(KEY_LAST_PAYLOAD)
            .apply()
    }

    fun decodeFromImage(
        context: Context,
        uri: Uri
    ): WifiQrData? {
        val bitmap =
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return null

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val binary = BinaryBitmap(HybridBinarizer(source))
        val payload = runCatching { MultiFormatReader().decode(binary).text }.getOrNull() ?: return null
        val data = parseWifiPayload(payload) ?: return null
        markProof(context, data.ssid, data.password, payload)
        return data
    }

    private fun parseWifiPayload(payload: String): WifiQrData? {
        if (!payload.startsWith("WIFI:", ignoreCase = true)) {
            return null
        }
        val body = payload.removePrefix("WIFI:")
        val fields = splitWifiFields(body)
        var ssid = ""
        var password = ""
        var security = ""
        fields.forEach { field ->
            when {
                field.startsWith("S:") -> ssid = unescape(field.removePrefix("S:"))
                field.startsWith("P:") -> password = unescape(field.removePrefix("P:"))
                field.startsWith("T:") -> security = unescape(field.removePrefix("T:"))
            }
        }
        if (ssid.isBlank() || password.isBlank()) {
            return null
        }
        return WifiQrData(ssid, password, security.ifBlank { "WPA" })
    }

    private fun splitWifiFields(body: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        body.forEach { ch ->
            when {
                escaped -> {
                    current.append(ch)
                    escaped = false
                }
                ch == '\\' -> escaped = true
                ch == ';' -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) {
            result += current.toString()
        }
        return result
    }

    private fun unescape(value: String): String {
        return value
            .replace("\\;", ";")
            .replace("\\:", ":")
            .replace("\\\\", "\\")
    }

    private fun signature(
        ssid: String,
        password: String
    ): String = ssid.trim() + "|" + password.trim()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
