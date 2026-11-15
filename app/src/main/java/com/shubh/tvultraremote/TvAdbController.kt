package com.shubh.tvultraremote

import dev.mobile.dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class TvAdbController {
    private var dadb: Dadb? = null

    private val appPackages = mapOf(
        "youtube" to "com.google.android.youtube.tv",
        "netflix" to "com.netflix.ninja",
        "prime" to "com.amazon.amazonvideo.livingroom",
        "prime video" to "com.amazon.amazonvideo.livingroom",
        "hotstar" to "in.startv.hotstar",
        "jiocinema" to "com.jio.media.stb.ondemand",
        "spotify" to "com.spotify.tv.android",
        "vlc" to "org.videolan.vlc"
    )

    suspend fun connect(ip: String, port: Int = 5555): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            dadb?.close()
            dadb = Dadb.create(ip.trim(), port)
            true
        }.getOrDefault(false)
    }

    suspend fun keyEvent(keyCode: Int) = withContext(Dispatchers.IO) {
        runCatching { dadb?.shell("input keyevent $keyCode") }
    }

    suspend fun tap(x: Int, y: Int) = withContext(Dispatchers.IO) {
        runCatching { dadb?.shell("input tap $x $y") }
    }

    suspend fun sendText(text: String) = withContext(Dispatchers.IO) {
        val escaped = text.replace(" ", "%s").replace("'", "\\'")
        runCatching { dadb?.shell("input text '$escaped'") }
    }

    suspend fun launchApp(appKey: String) = withContext(Dispatchers.IO) {
        val pkg = appPackages[appKey.lowercase()] ?: return@withContext
        runCatching {
            dadb?.shell("monkey -p $pkg -c android.intent.category.LEANBACK_LAUNCHER 1")
        }
    }

    suspend fun handleVoiceCommand(spoken: String): String = withContext(Dispatchers.IO) {
        val cmd = spoken.lowercase().trim()
        when {
            cmd.startsWith("search ") && cmd.contains(" on youtube") -> {
                val query = cmd.removePrefix("search ").substringBefore(" on youtube").trim()
                val encoded = URLEncoder.encode(query, "UTF-8")
                runCatching {
                    dadb?.shell("am start -a android.intent.action.VIEW -d 'https://www.youtube.com/results?search_query=$encoded' com.google.android.youtube.tv")
                }
                "Searching YouTube for: $query"
            }
            cmd.startsWith("open ") -> {
                val name = cmd.removePrefix("open ").trim()
                val entry = appPackages.entries.firstOrNull { name.contains(it.key) }
                if (entry != null) {
                    launchApp(entry.key)
                    "Opening ${entry.key.uppercase()} on TV"
                } else {
                    "App '$name' not found in quick list"
                }
            }
            cmd.contains("mute") -> { keyEvent(164); "Muted TV" }
            cmd.contains("volume up") -> { keyEvent(24); "Volume Up" }
            cmd.contains("volume down") -> { keyEvent(25); "Volume Down" }
            cmd.contains("home") -> { keyEvent(3); "Went Home" }
            cmd.contains("back") -> { keyEvent(4); "Pressed Back" }
            cmd.contains("pause") || cmd.contains("play") -> { keyEvent(85); "Toggled Play/Pause" }
            else -> {
                sendText(spoken)
                "Typed '$spoken' on TV"
            }
        }
    }
}
