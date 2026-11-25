package com.example.liveclass

import com.example.BuildConfig
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class LiveKitCredentials(
    val url: String,
    val apiKey: String,
    val apiSecret: String
)

object LiveKitTokenGenerator {

    fun normalizeLiveKitUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank() || trimmed.equals("null", ignoreCase = true) || trimmed.startsWith("YOUR_")) {
            return null
        }
        val normalized = when {
            trimmed.startsWith("https://", ignoreCase = true) ->
                "wss://${trimmed.substring(8)}"
            trimmed.startsWith("http://", ignoreCase = true) ->
                "ws://${trimmed.substring(7)}"
            else -> trimmed
        }
        val lower = normalized.lowercase()
        val host = when {
            lower.startsWith("wss://") -> normalized.substring(6)
            lower.startsWith("ws://") -> normalized.substring(5)
            else -> return null
        }.trim()
        if (host.isBlank() || host.startsWith("/")) return null
        return normalized
    }

    fun loadCredentialsFromBuildConfig(): LiveKitCredentials? {
        val url = normalizeLiveKitUrl(BuildConfig.LIVEKIT_URL) ?: return null
        val key = BuildConfig.LIVEKIT_API_KEY.trim()
        val secret = BuildConfig.LIVEKIT_API_SECRET.trim()
        if (
            key.isBlank() || key.equals("null", ignoreCase = true) || key.startsWith("YOUR_") ||
            secret.isBlank() || secret.equals("null", ignoreCase = true) || secret.startsWith("YOUR_")
        ) {
            return null
        }
        return LiveKitCredentials(url = url, apiKey = key, apiSecret = secret)
    }

    fun isConfigured(): Boolean = loadCredentialsFromBuildConfig() != null

    /**
     * Generates a signed HS256 LiveKit participant access JWT scoped to [roomName] and [participantUid].
     * Teachers receive microphone + camera + screen_share + screen_share_audio grants;
     * students never publish camera video and only receive microphone grant when approved by teacher.
     */
    fun generateRoomToken(
        apiKey: String,
        apiSecret: String,
        roomName: String,
        participantUid: String,
        participantName: String,
        isTeacher: Boolean,
        canPublishAudio: Boolean,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000L,
        ttlSeconds: Long = 4L * 3600L
    ): String {
        require(apiKey.isNotBlank() && apiSecret.isNotBlank()) { "LiveKit credentials are missing." }
        require(roomName.isNotBlank() && participantUid.isNotBlank()) { "Room and user identity are required." }

        val headerJsonString = """{"alg":"HS256","typ":"JWT"}"""

        val publishSources = buildList {
            if (isTeacher) {
                add("microphone")
                add("camera")
                add("screen_share")
                add("screen_share_audio")
            } else if (canPublishAudio) {
                add("microphone")
            }
        }
        val publishSourcesJson = publishSources.joinToString(prefix = "[", postfix = "]") { jsonQuote(it) }
        val canPublish = isTeacher || canPublishAudio
        val cleanDisplayName = participantName.ifBlank { participantUid }
        val nbf = nowEpochSeconds - 10L
        val exp = nowEpochSeconds + ttlSeconds

        val videoJson = """{"roomJoin":true,"room":${jsonQuote(roomName)},"canSubscribe":true,"canPublish":$canPublish,"canPublishData":true,"canPublishSources":$publishSourcesJson}"""
        val payloadJsonString = """{"iss":${jsonQuote(apiKey)},"sub":${jsonQuote(participantUid)},"name":${jsonQuote(cleanDisplayName)},"nbf":$nbf,"exp":$exp,"jti":${jsonQuote(participantUid)},"video":$videoJson}"""

        val headerPart = base64UrlEncode(headerJsonString.toByteArray(Charsets.UTF_8))
        val payloadPart = base64UrlEncode(payloadJsonString.toByteArray(Charsets.UTF_8))
        val signingInput = "$headerPart.$payloadPart"

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(apiSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val signaturePart = base64UrlEncode(mac.doFinal(signingInput.toByteArray(Charsets.UTF_8)))
        return "$signingInput.$signaturePart"
    }

    private fun jsonQuote(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch < ' ') {
                    sb.append(String.format("\\u%04x", ch.code))
                } else {
                    sb.append(ch)
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private const val BASE64_URL_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    internal fun base64UrlEncode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        val out = StringBuilder((input.size * 4 + 2) / 3)
        var i = 0
        while (i < input.size) {
            val b0 = input[i++].toInt() and 0xFF
            val hasB1 = i < input.size
            val b1 = if (hasB1) input[i++].toInt() and 0xFF else 0
            val hasB2 = i < input.size
            val b2 = if (hasB2) input[i++].toInt() and 0xFF else 0

            val triple = (b0 shl 16) or (b1 shl 8) or b2
            out.append(BASE64_URL_CHARS[(triple ushr 18) and 0x3F])
            out.append(BASE64_URL_CHARS[(triple ushr 12) and 0x3F])
            if (hasB1) out.append(BASE64_URL_CHARS[(triple ushr 6) and 0x3F])
            if (hasB2) out.append(BASE64_URL_CHARS[triple and 0x3F])
        }
        return out.toString()
    }
}
