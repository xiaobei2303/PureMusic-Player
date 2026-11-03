package com.puremusic.player.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class LyricsRepository {
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()
    data class Lyrics(val synced: String, val plain: String)

    fun search(track: Track): Lyrics? {
        val q = URLEncoder.encode("${track.displayTitle} ${track.displayArtist}", "UTF-8")
        val request = Request.Builder().url("https://lrclib.net/api/search?q=$q")
            .header("User-Agent", "PureMusic/1.0 (Android local music player)").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val array = JSONArray(response.body?.string().orEmpty())
            if (array.length() == 0) return null
            var best: org.json.JSONObject? = null
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                if (item.optString("syncedLyrics").isNotBlank()) { best = item; break }
                if (best == null && item.optString("plainLyrics").isNotBlank()) best = item
            }
            best ?: return null
            return Lyrics(best.optString("syncedLyrics"), best.optString("plainLyrics"))
        }
    }
}
