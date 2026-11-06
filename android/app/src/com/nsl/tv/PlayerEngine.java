package com.nsl.tv;

import android.content.Context;
import android.view.TextureView;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.Format;
import androidx.media3.common.DrmInitData;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager;
import androidx.media3.exoplayer.drm.DrmSession;
import androidx.media3.exoplayer.drm.DrmSessionEventListener;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback;
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mesin putar berbasis ExoPlayer/Media3:
 * multi-sumber fallback, DRM Widevine/ClearKey, header anti-hotlink, kualitas tertinggi.
 */
public final class PlayerEngine {

    public interface Listener {
        void onBuffering(boolean loading);
        void onPlaying(boolean playing);
        void onVideoSize(int width, int height);
        void onError(String message);
        void onReady();
    }

    private ExoPlayer player;
    private androidx.media3.exoplayer.trackselection.DefaultTrackSelector trackSelector;
    private final Listener listener;

    public PlayerEngine(Listener listener) {
        this.listener = listener;
    }

    public void create(Context ctx, TextureView surface) {
        DefaultRenderersFactory rf = new DefaultRenderersFactory(ctx);
        rf.setEnableDecoderFallback(true);
        rf.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);

        DefaultLoadControl lc = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(15000, 50000, 500, 1500)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();

        trackSelector = new androidx.media3.exoplayer.trackselection.DefaultTrackSelector(ctx);

        player = new ExoPlayer.Builder(ctx, rf)
                .setTrackSelector(trackSelector)
                .setLoadControl(lc)
                .build();
        player.setVideoTextureView(surface);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                listener.onBuffering(state == Player.STATE_BUFFERING);
                if (state == Player.STATE_READY) listener.onReady();
            }
            @Override public void onIsPlayingChanged(boolean isPlaying) {
                listener.onPlaying(isPlaying);
            }
            @Override public void onVideoSizeChanged(VideoSize vs) {
                listener.onVideoSize(vs.width, vs.height);
            }
            @Override public void onPlayerError(PlaybackException error) {
                String msg = error.getCause() != null ? error.getCause().getMessage() : error.getMessage();
                listener.onError(msg == null ? "Kesalahan pemutar" : msg);
            }
        });
    }

    /** Muat satu sumber. */
    public void play(Models.Source s) {
        DataSource.Factory ds = buildDataSource(s);
        DrmSessionManager drm = buildDrm(s, ds);
        DefaultLoadErrorHandlingPolicy errPolicy = new DefaultLoadErrorHandlingPolicy(1);

        MediaItem item = MediaItem.fromUri(s.url);
        MediaSource ms;
        String type = s.manifestType == null ? Data.sniffManifest(s.url, null) : s.manifestType;
        switch (type) {
            case "MPD":
                ms = new DashMediaSource.Factory(ds)
                        .setLoadErrorHandlingPolicy(errPolicy)
                        .setDrmSessionManagerProvider(mediaItem -> drm)
                        .createMediaSource(item);
                break;
            case "HLS":
                ms = new HlsMediaSource.Factory(ds)
                        .setAllowChunklessPreparation(true)
                        .setLoadErrorHandlingPolicy(errPolicy)
                        .setDrmSessionManagerProvider(mediaItem -> drm)
                        .createMediaSource(item);
                break;
            default:
                ms = new ProgressiveMediaSource.Factory(ds)
                        .setLoadErrorHandlingPolicy(errPolicy)
                        .setDrmSessionManagerProvider(mediaItem -> drm)
                        .createMediaSource(item);
                break;
        }
        // paksa kualitas tertinggi (default), user bisa turunkan manual
        forceHighest();
        // hentikan sesi lama (terutama sesi DRM) sebelum menukar sumber
        player.stop();
        player.clearMediaItems();
        player.setMediaSource(ms, /* positionResetMs= */ C.TIME_UNSET);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private DataSource.Factory buildDataSource(Models.Source s) {
        Map<String, String> props = new HashMap<>();
        String ua = Api.UA;
        if (s.headers != null) {
            for (Map.Entry<String, String> e : s.headers.entrySet()) {
                String k = e.getKey().toLowerCase(java.util.Locale.US);
                if (k.equals("user-agent")) ua = e.getValue();
                else props.put(e.getKey(), e.getValue());
            }
        }
        return new DefaultHttpDataSource.Factory()
                .setUserAgent(ua)
                .setDefaultRequestProperties(props)
                .setConnectTimeoutMs(10000)
                .setReadTimeoutMs(15000)
                .setAllowCrossProtocolRedirects(true);
    }

    private DrmSessionManager buildDrm(Models.Source s, DataSource.Factory ds) {
        if (s.drm == null) return null;
        try {
            switch (s.drm.type) {
                case Models.Drm.WIDEVINE: {
                    if (s.drm.licenseUrl == null) return null;
                    HttpMediaDrmCallback cb = new HttpMediaDrmCallback(s.drm.licenseUrl, ds);
                    if (s.drm.headers != null) {
                        for (Map.Entry<String, String> e : s.drm.headers.entrySet()) {
                            cb.setKeyRequestProperty(e.getKey(), e.getValue());
                        }
                    }
                    DefaultDrmSessionManager.Builder b = new DefaultDrmSessionManager.Builder()
                            .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                            .setPlayClearSamplesWithoutKeys(true)
                            .setMultiSession(android.os.Build.VERSION.SDK_INT > 25);
                    return b.build(cb);
                }
                case Models.Drm.CLEARKEY: {
                    DefaultDrmSessionManager.Builder b = new DefaultDrmSessionManager.Builder()
                            .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                            .setMultiSession(android.os.Build.VERSION.SDK_INT > 25)
                            .setPlayClearSamplesWithoutKeys(true);
                    androidx.media3.exoplayer.drm.MediaDrmCallback cb;
                    if (s.drm.jwkJson != null) {
                        cb = new LocalMediaDrmCallback(s.drm.jwkJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    } else if (s.drm.licenseUrl != null) {
                        HttpMediaDrmCallback hcb = new HttpMediaDrmCallback(s.drm.licenseUrl, ds);
                        if (s.drm.headers != null) {
                            for (Map.Entry<String, String> e : s.drm.headers.entrySet()) {
                                hcb.setKeyRequestProperty(e.getKey(), e.getValue());
                            }
                        }
                        cb = hcb;
                    } else {
                        return null;
                    }
                    DefaultDrmSessionManager inner = b.build(cb);
                    // ClearKey Android hanya memahami PSSH "common encryption".
                    // Banyak manifest hanya membawa PSSH Widevine/PlayReady —
                    // sintesis PSSH common dari kid agar kunci tetap bisa dipakai.
                    if (s.drm.kidBytes != null && s.drm.kidBytes.length == 16) {
                        final byte[] pssh = commonPssh(s.drm.kidBytes);
                        final DefaultDrmSessionManager mgr = inner;
                        return new DrmSessionManager() {
                            @Override public void prepare() { mgr.prepare(); }
                            @Override public void release() { mgr.release(); }
                            @Override public void setPlayer(android.os.Looper looper,
                                    androidx.media3.exoplayer.analytics.PlayerId playerId) {
                                mgr.setPlayer(looper, playerId);
                            }
                            @Override public DrmSessionManager.DrmSessionReference preacquireSession(
                                    DrmSessionEventListener.EventDispatcher ed, Format format) {
                                return mgr.preacquireSession(ed, swap(format));
                            }
                            @Override public DrmSession acquireSession(
                                    DrmSessionEventListener.EventDispatcher ed, Format format) {
                                return mgr.acquireSession(ed, swap(format));
                            }
                            @Override public int getCryptoType(Format format) { return mgr.getCryptoType(format); }
                            private Format swap(Format f) {
                                if (f == null || f.drmInitData == null) return f;
                                DrmInitData nd = new DrmInitData(
                                        new DrmInitData.SchemeData(C.CLEARKEY_UUID, C.CENC_TYPE_cenc, pssh));
                                return f.buildUpon().setDrmInitData(nd).build();
                            }
                        };
                    }
                    return inner;
                }
                default:
                    return null; // PLAYREADY tidak didukung Android MediaDrm umum
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** PSSH box v1 (system id common encryption 1077efec-…) berisi satu kid. */
    private static byte[] commonPssh(byte[] kid) {
        byte[] sysId = Data.b64UrlDecode("EHfv7MCyTQKs4zweUuL7Sw"); // 1077efec-c0b2-4d02-ace3-3c1e52e2fb4b
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(52);
        buf.putInt(52);
        buf.put((byte) 'p').put((byte) 's').put((byte) 's').put((byte) 'h');
        buf.putInt(0x01000000); // version 1, flags 0
        buf.put(sysId, 0, 16);
        buf.putInt(1);          // KID_count
        buf.put(kid, 0, 16);
        buf.putInt(0);          // DataSize
        return buf.array();
    }

    private void forceHighest() {
        if (trackSelector == null) return;
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setForceHighestSupportedBitrate(true)
                .setTunnelingEnabled(false));
    }

    // ---- Kontrol ----

    public void playPause() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
        } else {
            if (player.isCurrentMediaItemLive() && player.isCurrentMediaItemSeekable()) {
                player.seekToDefaultPosition();
            }
            player.play();
        }
    }

    public boolean isPlaying() { return player != null && player.isPlaying(); }

    public void setVolume(float v) { if (player != null) player.setVolume(v); }

    public float getVolume() { return player == null ? 1f : player.getVolume(); }

    public void stop() {
        if (player != null) player.stop();
    }

    public boolean isLive() { return player != null && player.isCurrentMediaItemLive(); }

    public void setPlayWhenReady(boolean p) { if (player != null) player.setPlayWhenReady(p); }

    public PlaybackParameters params() { return player == null ? null : player.getPlaybackParameters(); }

    /** Daftar label kualitas video tersedia: index 0 = AUTO. */
    public List<Quality> qualities() {
        List<Quality> out = new ArrayList<>();
        if (player == null) return out;
        Tracks tracks = player.getCurrentTracks();
        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int i = 0; i < g.length; i++) {
                Format f = g.getTrackFormat(i);
                if (!g.isTrackSupported(i)) continue;
                boolean exists = false;
                for (Quality q : out) if (q.height == f.height && q.bitrate == f.bitrate) { exists = true; break; }
                if (!exists) out.add(new Quality(f.height, f.bitrate, g.getMediaTrackGroup(), i));
            }
        }
        out.sort((a, b) -> Integer.compare(b.height * 100000 + b.bitrate, a.height * 100000 + a.bitrate));
        return out;
    }

    public void applyQuality(Quality q) {
        if (player == null) return;
        TrackSelectionParameters.Builder pb = player.getTrackSelectionParameters().buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO);
        if (q != null) {
            pb.setOverrideForType(new TrackSelectionOverride(q.group, q.trackIndex));
            trackSelector.setParameters(trackSelector.buildUponParameters().setForceHighestSupportedBitrate(false));
        } else {
            forceHighest();
        }
        player.setTrackSelectionParameters(pb.build());
    }

    public void release() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    public static final class Quality {
        public final int height;
        public final int bitrate;
        public final androidx.media3.common.TrackGroup group;
        public final int trackIndex;

        Quality(int height, int bitrate, androidx.media3.common.TrackGroup group, int trackIndex) {
            this.height = height;
            this.bitrate = bitrate;
            this.group = group;
            this.trackIndex = trackIndex;
        }

        public String label() {
            if (height > 0) return height + "p";
            return Math.max(1, bitrate / 1000) + " kbps";
        }
    }
}
