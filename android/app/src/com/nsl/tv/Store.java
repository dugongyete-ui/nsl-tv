package com.nsl.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import java.util.UUID;

/** Penyimpanan lokal perangkat (device id, favorit, setelan). */
public final class Store {

    private static final String PREFS = "nsl_tv_prefs";
    private static final String KEY_DEVICE_ID = "unique_device_id_v1";
    private static final String KEY_FAVORITES = "favorite_channel_ids";
    private static final String KEY_AUTO_PLAY_LAST = "auto_play_last_channel";
    private static final String KEY_LAST_CHANNEL = "last_played_channel_id";

    private static SharedPreferences prefs;
    private static String deviceId;

    private Store() {}

    public static void init(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** android_id huruf kecil; fallback UUID tanpa strip dipotong 16. */
    public static synchronized String deviceId(Context ctx) {
        if (deviceId != null) return deviceId;
        deviceId = prefs.getString(KEY_DEVICE_ID, null);
        if (deviceId == null || deviceId.length() < 8) {
            deviceId = freshId();
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply();
        }
        return deviceId;
    }

    /** Identitas baru (UUID tanpa strip dipotong 16) & simpan. */
    public static synchronized void rotateDeviceId(Context ctx) {
        deviceId = freshId();
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply();
    }

    private static String freshId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // ---- Favorit ----

    public static java.util.Set<String> favorites() {
        return new java.util.HashSet<>(prefs.getStringSet(KEY_FAVORITES, new java.util.HashSet<>()));
    }

    public static boolean isFavorite(String id) {
        return prefs.getStringSet(KEY_FAVORITES, new java.util.HashSet<>()).contains(id);
    }

    public static void setFavorite(String id, boolean fav) {
        java.util.Set<String> set = favorites();
        if (fav) set.add(id); else set.remove(id);
        prefs.edit().putStringSet(KEY_FAVORITES, set).apply();
    }

    public static void clearFavorites() {
        prefs.edit().remove(KEY_FAVORITES).apply();
    }

    // ---- Setelan & riwayat ----

    public static boolean autoPlayLast() {
        return prefs.getBoolean(KEY_AUTO_PLAY_LAST, true);
    }

    public static void setAutoPlayLast(boolean v) {
        prefs.edit().putBoolean(KEY_AUTO_PLAY_LAST, v).apply();
    }

    public static String lastChannelId() {
        return prefs.getString(KEY_LAST_CHANNEL, null);
    }

    public static void setLastChannel(String id) {
        prefs.edit().putString(KEY_LAST_CHANNEL, id).apply();
    }
}
