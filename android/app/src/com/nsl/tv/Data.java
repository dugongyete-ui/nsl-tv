package com.nsl.tv;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parser & normalizer data saluran — mengikuti aturan parser app asli. */
public final class Data {

    /** Cache data aktif (dipakai lintas activity). */
    public static Models.AppData CURRENT = null;

    private Data() {}

    public static Models.AppData parseChannels(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (!root.optBoolean("success", false)) {
            throw new Exception(root.optString("message", "Respons server tidak valid"));
        }
        Models.AppData data = new Models.AppData();

        JSONObject server = root.optJSONObject("server");
        if (server != null) {
            data.serverName = server.optString("name", "");
            data.serverVersion = server.optString("version", "");
            data.serverUpdated = server.optString("lastUpdated", "");
        }

        JSONArray cats = root.optJSONArray("categories");
        if (cats != null) {
            for (int i = 0; i < cats.length(); i++) {
                String c = cats.optString(i, "").trim();
                if (c.isEmpty()) continue;
                if (c.contains("Favorites")) continue; // favorit dikelola lokal
                data.categories.add(c);
            }
        }

        JSONArray arr = root.optJSONArray("channels");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                try {
                    data.channels.add(parseChannel(arr.getJSONObject(i)));
                } catch (Exception ignored) {
                    // lewati saluran rusak
                }
            }
        }
        Collections.sort(data.channels, (a, b) -> {
            if (a.order != b.order) return Integer.compare(a.order, b.order);
            return a.name.compareToIgnoreCase(b.name);
        });
        CURRENT = data;
        return data;
    }

    private static Models.Channel parseChannel(JSONObject o) throws Exception {
        Models.Channel ch = new Models.Channel();
        ch.id = o.optString("id", "");
        if (ch.id.isEmpty()) ch.id = "ch_" + System.nanoTime();
        ch.name = o.optString("name", "").trim();
        if (ch.name.isEmpty()) ch.name = "Tanpa Nama";
        ch.logoUrl = o.optString("logoUrl", null);
        ch.group = o.optString("groupTitle", "LAINNYA");
        ch.order = o.optInt("order", 9999);

        String name = ch.name;
        if (name.startsWith("\uD83D\uDD34 LIVE") || name.contains(" LIVE ")) ch.liveEvent = true;

        ch.eventSchedule = firstNonEmpty(o.optString("eventSchedule", null), optStr(o.optJSONObject("eventInfo"), "schedule"), optStr(o.optJSONObject("eventInfo"), "time"));
        ch.league = firstNonEmpty(o.optString("league", null), optStr(o.optJSONObject("eventInfo"), "league"));
        ch.sportCategory = firstNonEmpty(o.optString("sportCategory", null), optStr(o.optJSONObject("eventInfo"), "sportCategory"));
        ch.sportIcon = o.optString("sportIcon", null);
        if (ch.sportIcon == null || ch.sportIcon.trim().isEmpty()) ch.sportIcon = iconForSport(ch.sportCategory);

        ch.startMs = parseEpoch(o.optLong("eventStartTimeEpoch", 0L));
        ch.endMs = parseEpoch(o.optLong("eventEndTimeEpoch", 0L));

        JSONArray sources = o.optJSONArray("sources");
        if (sources != null && sources.length() > 0) {
            for (int i = 0; i < sources.length(); i++) {
                Models.Source s = parseSource(sources.getJSONObject(i));
                if (s != null && s.url != null && !s.url.isEmpty()) ch.sources.add(s);
            }
        }
        if (ch.sources.isEmpty()) {
            // fallback: url + headers + drmConfig tingkat atas
            String url = o.optString("url", "");
            if (!url.isEmpty()) {
                Models.Source s = new Models.Source();
                s.url = url;
                s.headers = parseHeaders(o.optJSONObject("headers"));
                s.drm = parseDrm(o.optJSONObject("drmConfig"));
                s.manifestType = sniffManifest(url, o.optString("manifestType", null));
                ch.sources.add(s);
            }
        }
        if (ch.sources.isEmpty()) throw new Exception("saluran tanpa sumber");
        return ch;
    }

    private static Models.Source parseSource(JSONObject o) throws Exception {
        if (o == null) return null;
        Models.Source s = new Models.Source();
        s.url = o.optString("url", "");
        s.headers = parseHeaders(o.optJSONObject("headers"));
        s.drm = parseDrm(o.optJSONObject("drmConfig"));
        s.manifestType = sniffManifest(s.url, o.optString("manifestType", null));
        return s;
    }

    /** Normalisasi tipe DRM + pemisahan JWK lokal vs server lisensi. */
    private static Models.Drm parseDrm(JSONObject o) {
        if (o == null) return null;
        String type = normDrmType(o.optString("type", ""));
        if (type == null) return null;

        Models.Drm drm = new Models.Drm();
        drm.type = type;
        drm.headers = parseHeaders(o.optJSONObject("headers"));

        String licenseKey = o.optString("licenseKey", "").trim();
        String licenseUrl = o.optString("licenseUrl", "").trim();
        if (!licenseKey.isEmpty()) {
            if (licenseKey.startsWith("{")) {
                drm.jwkJson = normalizeJwk(licenseKey); // ClearKey lokal (JWK, base64url)
                drm.kidBytes = extractJwkKid(drm.jwkJson);
            } else if (licenseKey.startsWith("http")) {
                drm.licenseUrl = licenseKey;
            } else if (licenseKey.matches("[0-9a-fA-F]{16,64}:[0-9a-fA-F]{16,64}")) {
                // Format hex "kid:key" → ubah menjadi JWK ClearKey lokal
                String[] parts = licenseKey.split(":");
                byte[] kid = hexToBytes(parts[0]);
                byte[] key = hexToBytes(parts[1]);
                if (kid != null && key != null) {
                    drm.kidBytes = kid;
                    drm.jwkJson = "{\"keys\":[{\"kty\":\"oct\",\"k\":\"" + b64Url(key)
                            + "\",\"kid\":\"" + b64Url(kid) + "\"}],\"type\":\"temporary\"}";
                }
            }
        }
        if (drm.licenseUrl == null && !licenseUrl.isEmpty() && licenseUrl.startsWith("http")) {
            drm.licenseUrl = licenseUrl;
        }
        if (drm.licenseUrl == null && drm.jwkJson == null) return null;
        return drm;
    }

    /**
     * Samakan bentuk JWK dengan yang diharapkan ClearKey CDM Android:
     * k/kid dinormalisasi ke base64url tanpa padding (RFC 7515),
     * terlepas dari sumber memakai base64 standar atau URL-safe.
     */
    private static String normalizeJwk(String jwkJson) {
        try {
            JSONObject j = new JSONObject(jwkJson);
            JSONArray keys = j.optJSONArray("keys");
            if (keys == null) return jwkJson;
            JSONArray out = new JSONArray();
            for (int i = 0; i < keys.length(); i++) {
                JSONObject k = keys.optJSONObject(i);
                if (k == null) continue;
                JSONObject nk = new JSONObject();
                nk.put("kty", k.optString("kty", "oct"));
                byte[] kk = b64Any(k.optString("k", ""));
                byte[] kid = b64Any(k.optString("kid", ""));
                if (kk != null) nk.put("k", b64Url(kk));
                if (kid != null) nk.put("kid", b64Url(kid));
                out.put(nk);
            }
            JSONObject res = new JSONObject();
            res.put("keys", out);
            res.put("type", j.optString("type", "temporary"));
            return res.toString();
        } catch (Exception e) {
            return jwkJson;
        }
    }

    private static byte[] b64Any(String s) {
        if (s == null || s.isEmpty()) return null;
        byte[] r = b64UrlDecode(s);
        if (r != null) return r;
        try {
            return android.util.Base64.decode(s, android.util.Base64.DEFAULT);
        } catch (Exception e) {
            return null;
        }
    }

    /** Ambil kid mentah (16 byte) dari JSON JWK bila ada. */
    private static byte[] extractJwkKid(String jwkJson) {
        try {
            JSONObject j = new JSONObject(jwkJson);
            JSONArray keys = j.optJSONArray("keys");
            if (keys != null && keys.length() > 0) {
                String kid = keys.optJSONObject(0).optString("kid", "");
                if (!kid.isEmpty()) return b64UrlDecode(kid);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static byte[] hexToBytes(String hex) {
        try {
            int len = hex.length() / 2;
            byte[] out = new byte[len];
            for (int i = 0; i < len; i++) {
                out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /** Base64 URL-safe tanpa padding (RFC 7515). */
    public static String b64Url(byte[] data) {
        return android.util.Base64.encodeToString(data,
                android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP);
    }

    public static byte[] b64UrlDecode(String s) {
        try {
            return android.util.Base64.decode(s.trim(),
                    android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    private static String normDrmType(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String t = raw.trim().toLowerCase(Locale.US);
        if (t.contains("widevine") || t.contains("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")) return Models.Drm.WIDEVINE;
        if (t.contains("clearkey") || t.contains("e8319c59-db6b-4a62-3850-9f350e961d60")) return Models.Drm.CLEARKEY;
        if (t.contains("playready") || t.contains("9a04f079-9840-4286-ab92-e65be0885f95")
                || t.contains("1.3.6.1.4.1.25996.1.1")) return Models.Drm.PLAYREADY;
        return null;
    }

    public static String sniffManifest(String url, String explicit) {
        if (explicit != null && !explicit.trim().isEmpty()) {
            String e = explicit.trim().toLowerCase(Locale.US);
            if (e.equals("hls") || e.equals("m3u8")) return "HLS";
            if (e.equals("mpd") || e.equals("dash")) return "MPD";
            if (e.equals("ts")) return "TS";
        }
        if (url == null) return "TS";
        String u = url.toLowerCase(Locale.US);
        int q = u.indexOf('?');
        String clean = q >= 0 ? u.substring(0, q) : u;
        if (clean.endsWith(".mpd")) return "MPD";
        if (clean.endsWith(".m3u8") || clean.contains(".m3u8")) return "HLS";
        if (clean.endsWith(".ts")) return "TS";
        if (clean.endsWith(".mp4")) return "PROG";
        return "HLS"; // mayoritas endpoint IPTV adalah HLS
    }

    public static Map<String, String> parseHeaders(JSONObject o) {
        Map<String, String> map = new HashMap<>();
        if (o != null) {
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                String v = o.optString(k, "");
                if (!k.isEmpty() && !v.isEmpty()) map.put(k, v);
            }
        }
        return map;
    }

    private static long parseEpoch(long v) {
        if (v <= 0L) return 0L;
        if (v < 10_000_000_000L) return v * 1000L; // detik → ms
        return v;
    }

    private static String optStr(JSONObject o, String key) {
        if (o == null) return null;
        String v = o.optString(key, "");
        return v.isEmpty() ? null : v;
    }

    private static String firstNonEmpty(String a, String b) {
        return firstNonEmpty(a, b, null);
    }

    private static String firstNonEmpty(String a, String b, String c) {
        if (a != null && !a.trim().isEmpty()) return a.trim();
        if (b != null && !b.trim().isEmpty()) return b.trim();
        return (c != null && !c.trim().isEmpty()) ? c.trim() : null;
    }

    public static String iconForSport(String cat) {
        if (cat == null) return "🏆";
        String c = cat.toLowerCase(Locale.US);
        if (c.contains("sepak") || c.contains("football")) return "⚽";
        if (c.contains("basket")) return "🏀";
        if (c.contains("tenis") || c.contains("tennis")) return "🎾";
        if (c.contains("voli") || c.contains("volley")) return "🏐";
        if (c.contains("balap") || c.contains("racing") || c.contains("moto")) return "🏎️";
        if (c.contains("combat") || c.contains("ufc") || c.contains("tinju")) return "🥊";
        if (c.contains("esport") || c.contains("e-sport")) return "🎮";
        if (c.contains("tangkis") || c.contains("badminton")) return "🏸";
        return "🏆";
    }

    // ---- Filter util ----

    public static List<Models.Channel> byCategory(List<Models.Channel> all, String category, String query) {
        List<Models.Channel> out = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.US);
        for (Models.Channel c : all) {
            boolean matchCat = "SEMUA".equals(category) || c.group.equalsIgnoreCase(category);
            boolean matchQ = q.isEmpty() || c.name.toLowerCase(Locale.US).contains(q)
                    || c.group.toLowerCase(Locale.US).contains(q);
            if (matchCat && matchQ) out.add(c);
        }
        return out;
    }

    public static List<Models.Channel> favorites(List<Models.Channel> all, java.util.Set<String> favIds, String query) {
        List<Models.Channel> out = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.US);
        for (Models.Channel c : all) {
            if (!favIds.contains(c.id)) continue;
            if (!q.isEmpty() && !c.name.toLowerCase(Locale.US).contains(q)) continue;
            out.add(c);
        }
        return out;
    }

    public static Models.Channel byId(String id) {
        if (CURRENT == null || id == null) return null;
        for (Models.Channel c : CURRENT.channels) if (id.equals(c.id)) return c;
        return null;
    }

    /** Saluran acara: kategori EVENT / live event / punya jadwal epoch. */
    public static List<Models.Channel> events(List<Models.Channel> all) {
        List<Models.Channel> out = new ArrayList<>();
        for (Models.Channel c : all) {
            boolean eventCat = "EVENT".equalsIgnoreCase(c.group);
            if (eventCat || c.hasEvents()) out.add(c);
        }
        long now = System.currentTimeMillis();
        Collections.sort(out, (a, b) -> {
            boolean la = a.startMs > 0 && a.startMs <= now && (a.endMs == 0 || a.endMs > now);
            boolean lb = b.startMs > 0 && b.startMs <= now && (b.endMs == 0 || b.endMs > now);
            if (la != lb) return la ? -1 : 1;
            if (a.startMs == 0 && b.startMs == 0) return a.name.compareToIgnoreCase(b.name);
            if (a.startMs == 0) return 1;
            if (b.startMs == 0) return -1;
            // yang sudah lewat ditaruh paling akhir
            boolean pa = a.endMs > 0 && a.endMs < now;
            boolean pb = b.endMs > 0 && b.endMs < now;
            if (pa != pb) return pa ? 1 : -1;
            return Long.compare(a.startMs, b.startMs);
        });
        return out;
    }
}
