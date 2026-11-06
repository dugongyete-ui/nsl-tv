package com.nsl.tv.data

import org.json.JSONArray
import org.json.JSONObject

/** Model data saluran & sumber streaming (paritas dengan API Dopamine TV). */
data class DrmConfig(
    val type: String,          // WIDEVINE | CLEARKEY | PLAYREADY
    val licenseKey: String?,   // URL atau JWK JSON atau "kid:key" hex
    val licenseUrl: String?,
    val headers: Map<String, String>
)

data class Source(
    val url: String,
    val manifestType: String,  // hls | mpd | ts
    val headers: Map<String, String>,
    val drm: DrmConfig?,
    val resolutionLabel: String?
)

data class Channel(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val groupTitle: String,
    val order: Int,
    val tvgId: String?,
    val sources: List<Source>,
    val isLiveEvent: Boolean,
    val league: String?,
    val sportCategory: String?,
    val sportIcon: String?,
    val eventStartEpoch: Long?,
    val eventEndEpoch: Long?
) {
    val isEvent: Boolean get() = isLiveEvent || eventStartEpoch != null
}

data class ServerInfo(val name: String, val version: String)
data class AppUpdate(val latestVersion: String, val versionCode: Int, val changelog: String, val url: String?)

data class ChannelsResponse(
    val channels: List<Channel>,
    val categories: List<String>,
    val server: ServerInfo?,
    val appUpdate: AppUpdate?
)

object Models {

    fun parseChannels(body: String): ChannelsResponse {
        val root = JSONObject(body)
        val channelsJson = root.optJSONArray("channels") ?: JSONArray()
        val channels = ArrayList<Channel>(channelsJson.length())
        for (i in 0 until channelsJson.length()) {
            val c = channelsJson.optJSONObject(i) ?: continue
            val sourcesJson = c.optJSONArray("sources") ?: JSONArray()
            val sources = ArrayList<Source>(sourcesJson.length())
            for (j in 0 until sourcesJson.length()) {
                val s = sourcesJson.optJSONObject(j) ?: continue
                val url = s.optString("url", "").trim()
                if (url.isEmpty()) continue
                val drmJson = s.optJSONObject("drmConfig")
                val drm = drmJson?.let {
                    DrmConfig(
                        type = it.optString("type", "WIDEVINE").uppercase(),
                        licenseKey = it.optString("licenseKey", null as String?),
                        licenseUrl = it.optString("licenseUrl", null as String?),
                        headers = headerMap(it.optJSONObject("headers"))
                    )
                }
                sources.add(
                    Source(
                        url = url,
                        manifestType = s.optString("manifestType", sniffType(url)).lowercase(),
                        headers = headerMap(s.optJSONObject("headers")),
                        drm = drm,
                        resolutionLabel = s.optString("resolutionLabel", null as String?)
                    )
                )
            }
            channels.add(
                Channel(
                    id = c.optString("id", "ch_$i"),
                    name = c.optString("name", "Saluran ${i + 1}"),
                    logoUrl = c.optString("logoUrl", null as String?),
                    groupTitle = c.optString("groupTitle", "LAINNYA").uppercase(),
                    order = c.optInt("order", 9999),
                    tvgId = c.optString("tvgId", null as String?),
                    sources = sources,
                    isLiveEvent = c.optBoolean("isLiveEvent", false),
                    league = c.optString("league", null as String?),
                    sportCategory = c.optString("sportCategory", null as String?),
                    sportIcon = c.optString("sportIcon", null as String?),
                    eventStartEpoch = if (c.has("eventStartTimeEpoch")) c.optLong("eventStartTimeEpoch") else null,
                    eventEndEpoch = if (c.has("eventEndTimeEpoch")) c.optLong("eventEndTimeEpoch") else null
                )
            )
        }
        val cats = ArrayList<String>()
        val catsJson = root.optJSONArray("categories") ?: JSONArray()
        for (i in 0 until catsJson.length()) {
            val v = catsJson.opt(i) ?: continue
            val name = when (v) {
                is String -> v
                is JSONObject -> v.optString("name", "")
                else -> ""
            }
            if (name.isNotBlank()) cats.add(name.uppercase())
        }
        val server = root.optJSONObject("server")?.let {
            ServerInfo(it.optString("name", ""), it.optString("version", ""))
        }
        val upd = root.optJSONObject("appUpdate")?.let {
            AppUpdate(
                it.optString("latestVersion", ""),
                it.optInt("versionCode", 0),
                it.optString("changelog", ""),
                it.optString("url", null as String?)
            )
        }
        return ChannelsResponse(channels, cats, server, upd)
    }

    private fun headerMap(o: JSONObject?): Map<String, String> {
        if (o == null) return emptyMap()
        val m = LinkedHashMap<String, String>()
        for (k in o.keys()) {
            val v = o.optString(k, "")
            if (v.isNotBlank()) m[k.lowercase()] = v
        }
        return m
    }

    fun sniffType(url: String): String {
        val u = url.lowercase().substringBefore('|')
        return when {
            u.contains(".mpd") -> "mpd"
            u.contains(".m3u8") || u.contains("/hls/") || u.contains("m3u") -> "hls"
            u.contains(".ts") || u.contains("/ts/") -> "ts"
            else -> "hls"
        }
    }
}
