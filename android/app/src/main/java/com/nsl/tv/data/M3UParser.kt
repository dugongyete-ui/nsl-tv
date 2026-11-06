package com.nsl.tv.data

/**
 * Parser M3U gaya Kodi — paritas laporan recon:
 * #EXTINF (tvg-name/tvg-logo/tvg-id/group-title), #EXTGRP, #EXTVLCOPT,
 * #KODIPROP (inputstream.adaptive.*), pipe headers "url|H=V&...", multi-URL = multi-source.
 */
object M3UParser {

    fun parse(content: String): List<Channel> {
        val channels = ArrayList<Channel>()
        var name: String? = null
        var tvgId: String? = null
        var tvgName: String? = null
        var logo: String? = null
        var group: String? = null
        val volOpts = LinkedHashMap<String, String>()
        val kodiProps = LinkedHashMap<String, String>()
        val urls = ArrayList<String>()

        fun reset() {
            name = null; tvgId = null; tvgName = null; logo = null; group = null
            volOpts.clear(); kodiProps.clear(); urls.clear()
        }

        fun flush() {
            val n = name ?: return
            if (urls.isEmpty()) { reset(); return }
            val sources = ArrayList<Source>(urls.size)
            for (raw in urls) {
                var url = raw
                val hdrs = LinkedHashMap<String, String>()
                // pipe headers: url|User-Agent=..&Referer=..
                if (url.contains('|')) {
                    val parts = url.split('|', limit = 2)
                    url = parts[0].trim()
                    parts[1].split('&').forEach { kv ->
                        val i = kv.indexOf('=')
                        if (i > 0) {
                            val k = kv.substring(0, i).trim().lowercase()
                            val v = kv.substring(i + 1).trim()
                            if (v.isNotEmpty()) hdrs[k] = v
                        }
                    }
                }
                // #EXTVLCOPT
                volOpts["http-user-agent"]?.let { hdrs.putIfAbsent("user-agent", it) }
                volOpts["http-referrer"]?.let { hdrs.putIfAbsent("referer", it) }
                volOpts["http-origin"]?.let { hdrs.putIfAbsent("origin", it) }
                volOpts["http-cookies"]?.let { hdrs.putIfAbsent("cookie", it) }
                // #KODIPROP
                val licType = kodiProps["inputstream.adaptive.license_type"]
                val licKey = kodiProps["inputstream.adaptive.license_key"]
                val licHdrs = kodiProps["inputstream.adaptive.license_headers"]
                val manifest = kodiProps["inputstream.adaptive.manifest_type"]?.lowercase()
                val drm = if (licType != null && licKey != null) {
                    val type = when (licType.lowercase()) {
                        "com.widevine.alpha", "widevine" -> "WIDEVINE"
                        "org.w3.clearkey", "clearkey" -> "CLEARKEY"
                        "com.microsoft.playready", "playready" -> "PLAYREADY"
                        else -> licType.uppercase()
                    }
                    val hdr = licHdrs?.split('&')?.mapNotNull { kv ->
                        val i = kv.indexOf('='); if (i > 0) kv.substring(0, i).trim().lowercase() to kv.substring(i + 1).trim() else null
                    }?.toMap() ?: emptyMap()
                    DrmConfig(type, licKey, null, hdr)
                } else null
                sources.add(
                    Source(
                        url = url,
                        manifestType = (manifest ?: Models.sniffType(url)),
                        headers = hdrs,
                        drm = drm,
                        resolutionLabel = null
                    )
                )
            }
            channels.add(
                Channel(
                    id = tvgId ?: "m3u_${channels.size}_${n.hashCode()}",
                    name = n,
                    logoUrl = logo,
                    groupTitle = (group ?: "LAINNYA").uppercase(),
                    order = channels.size,
                    tvgId = tvgId,
                    sources = sources,
                    isLiveEvent = false,
                    league = null, sportCategory = null, sportIcon = null,
                    eventStartEpoch = null, eventEndEpoch = null
                )
            )
            reset()
        }

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#EXTINF:") -> {
                    flush()
                    val attrs = line.substring(8)
                    val commaIdx = attrs.indexOf(',')
                    val attrPart = if (commaIdx >= 0) attrs.substring(0, commaIdx) else attrs
                    name = if (commaIdx >= 0) attrs.substring(commaIdx + 1).trim() else "Tanpa Nama"
                    regexAttr(attrPart, "tvg-id")?.let { tvgId = it }
                    regexAttr(attrPart, "tvg-name")?.let { tvgName = it; if (name.isNullOrBlank()) name = it }
                    regexAttr(attrPart, "tvg-logo")?.let { logo = it }
                    regexAttr(attrPart, "group-title")?.let { group = it }
                }
                line.startsWith("#EXTGRP:") -> group = line.substring(8).trim()
                line.startsWith("#EXTVLCOPT:") -> {
                    val kv = line.substring(11).trim()
                    val i = kv.indexOf('=')
                    if (i > 0) volOpts[kv.substring(0, i).trim().lowercase()] = kv.substring(i + 1).trim()
                }
                line.startsWith("#KODIPROP:") -> {
                    val kv = line.substring(10).trim()
                    val i = kv.indexOf('=')
                    if (i > 0) kodiProps[kv.substring(0, i).trim().lowercase()] = kv.substring(i + 1).trim()
                }
                line.isNotEmpty() && !line.startsWith("#") -> urls.add(line)
            }
        }
        flush()
        return channels
    }

    private fun regexAttr(text: String, key: String): String? {
        val m = Regex("""$key="([^"]*)"""").find(text) ?: Regex("""$key=([^"\s]+)""").find(text) ?: return null
        return m.groupValues[1].takeIf { it.isNotBlank() }
    }
}
