package com.shubh.tvultraremote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class TvAdbController {

    @Volatile
    var connectedIp: String? = null
        private set

    private val appPackages = mapOf(
        "youtube" to "com.google.android.youtube.tv",
        "netflix" to "com.netflix.ninja",
        "prime video" to "com.amazon.amazonvideo.livingroom",
        "hotstar" to "in.startv.hotstar",
        "spotify" to "com.spotify.tv.android",
        "vlc" to "org.videolan.vlc"
    )

    // Real check: can we open a TCP connection to the TV's ADB port?
    suspend fun connect(ip: String, port: Int = 5555): Boolean = withContext(Dispatchers.IO) {
        val target = ip.trim()
        val ok = try {
            Socket().use { it.connect(InetSocketAddress(target, port), 3000) }
            true
        } catch (e: Exception) {
            false
        }
        connectedIp = if (ok) target else null
        ok
    }

    // Command sending is not built yet, so these report that honestly.
    private fun notReady(): String =
        if (connectedIp == null) "Not connected to TV" else "TV control not built yet (next step)"

    suspend fun keyEvent(keyCode: Int): String = withContext(Dispatchers.IO) { notReady() }

    suspend fun tap(x: Int, y: Int): String = withContext(Dispatchers.IO) { notReady() }

    suspend fun sendText(text: String): String = withContext(Dispatchers.IO) { notReady() }

    suspend fun launchApp(appKey: String): String = withContext(Dispatchers.IO) {
        val pkg = appPackages[appKey.lowercase()] ?: return@withContext "Unknown app: $appKey"
        "${notReady()} ($pkg)"
    }

    suspend fun handleVoiceCommand(spoken: String): String = withContext(Dispatchers.IO) {
        val cmd = spoken.lowercase().trim()
        val entry = appPackages.entries.firstOrNull { cmd.contains(it.key) }
        when {
            cmd.startsWith("open ") && entry != null -> "Open ${entry.key}: ${notReady()}"
            cmd.startsWith("open ") -> "App not in quick list: $cmd"
            else -> "Heard: \"$spoken\" (${notReady()})"
        }
    }
}