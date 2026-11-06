package com.nsl.tv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Struktur data aplikasi. */
public final class Models {

    private Models() {}

    /** Konfigurasi DRM sebuah sumber. */
    public static class Drm {
        public static final String WIDEVINE = "WIDEVINE";
        public static final String CLEARKEY = "CLEARKEY";
        public static final String PLAYREADY = "PLAYREADY";

        public String type;            // WIDEVINE / CLEARKEY / PLAYREADY / null
        public String licenseUrl;      // server lisensi (remote)
        public String jwkJson;         // ClearKey lokal (JSON JWK)
        public byte[] kidBytes;        // kid mentah untuk sintesis PSSH common
        public Map<String, String> headers = new HashMap<>();
    }

    /** Satu sumber stream (fallback berurutan). */
    public static class Source {
        public String url;
        public String manifestType;    // HLS / MPD / TS
        public Map<String, String> headers = new HashMap<>();
        public Drm drm;
    }

    /** Satu saluran TV. */
    public static class Channel {
        public String id = "";
        public String name = "";
        public String logoUrl;
        public String group = "";
        public int order = 9999;
        public boolean liveEvent;
        public String eventSchedule;
        public String league;
        public String sportCategory;
        public String sportIcon;
        public long startMs = 0L;
        public long endMs = 0L;
        public final List<Source> sources = new ArrayList<>();

        public boolean hasEvents() {
            return startMs > 0L || liveEvent;
        }
    }

    /** Data aplikasi dari endpoint channels. */
    public static class AppData {
        public List<Channel> channels = new ArrayList<>();
        public List<String> categories = new ArrayList<>();
        public String serverName = "";
        public String serverVersion = "";
        public String serverUpdated = "";
    }
}
