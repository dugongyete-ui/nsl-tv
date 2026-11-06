package com.nsl.tv;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Rational;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Layar pemutar: overlay native, PIP, fallback sumber, kualitas, daftar saluran. */
public class PlayerActivity extends android.app.Activity implements PlayerEngine.Listener {

    public static final String EXTRA_CHANNEL_ID = "channel_id";

    private TextureView videoSurface;
    private PlayerEngine engine;
    private Models.Channel channel;
    private int sourceIndex = 0;
    private boolean retriedCurrent = false;
    private boolean sourceReady = false;
    private String lastError = "";
    private boolean wantPlay = true;
    private boolean resumeOnStart = false;

    private View overlay, drawer, errorBox;
    private TextView pTitle, pSource, pLive, pQuality, btnAspect, pError;
    private ImageView btnPlay, btnMute;
    private ProgressBar pLoading;
    private ListView drawerList;
    private Adapters.DrawerAdapter drawerAdapter;

    private int aspectMode = 0; // 0 FIT, 1 CROP, 2 STRETCH
    private int videoW = 16, videoH = 9;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable hideOverlay = this::hideOverlayNow;

    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private final AudioManager.OnAudioFocusChangeListener focusListener = change -> {
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            engine.setPlayWhenReady(false);
        } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
            if (wantPlay) engine.setPlayWhenReady(true);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        setContentView(R.layout.activity_player);
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        String chId = getIntent() != null ? getIntent().getStringExtra(EXTRA_CHANNEL_ID) : null;
        channel = Data.byId(chId);
        if (channel == null) { finish(); return; }
        Store.setLastChannel(chId);

        engine = new PlayerEngine(this);
        engine.create(this, findViewById(R.id.videoSurface));

        overlay = findViewById(R.id.overlay);
        drawer = findViewById(R.id.drawer);
        errorBox = findViewById(R.id.errorBox);
        pTitle = findViewById(R.id.pTitle);
        pSource = findViewById(R.id.pSource);
        pLive = findViewById(R.id.pLive);
        pQuality = findViewById(R.id.pQuality);
        pError = findViewById(R.id.pError);
        btnPlay = findViewById(R.id.btnPlay);
        btnMute = findViewById(R.id.btnMute);
        pLoading = findViewById(R.id.pLoading);
        btnAspect = findViewById(R.id.btnAspect);
        drawerList = findViewById(R.id.drawerList);

        pTitle.setText(channel.name);
        pTitle.setEllipsize(TextUtils.TruncateAt.END);

        View tap = findViewById(R.id.tapCatch);
        tap.setOnClickListener(v -> toggleOverlay());
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnPlay).setOnClickListener(v -> playPauseClicked());
        findViewById(R.id.btnPrev).setOnClickListener(v -> zap(-1));
        findViewById(R.id.btnNext).setOnClickListener(v -> zap(1));
        findViewById(R.id.btnPip).setOnClickListener(v -> enterPip());
        findViewById(R.id.btnMute).setOnClickListener(v -> toggleMute());
        findViewById(R.id.btnAspect).setOnClickListener(v -> cycleAspect());
        findViewById(R.id.pQuality).setOnClickListener(v -> showQualityDialog());
        findViewById(R.id.btnSources).setOnClickListener(v -> showSourceDialog());
        findViewById(R.id.btnChannels).setOnClickListener(v -> openDrawer());
        findViewById(R.id.btnDrawerClose).setOnClickListener(v -> closeDrawer());
        findViewById(R.id.btnRetry).setOnClickListener(v -> {
            errorBox.setVisibility(View.GONE);
            retriedCurrent = false;
            loadSource(0);
        });

        EditText drawerSearch = findViewById(R.id.drawerSearch);
        drawerSearch.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void onText(String s) { refreshDrawer(s); }
        });

        requestFocus();

        drawerAdapter = new Adapters.DrawerAdapter(getLayoutInflater(),
                Data.CURRENT != null ? Data.CURRENT.channels : new ArrayList<>(), channel.id);
        drawerList.setAdapter(drawerAdapter);
        drawerList.setOnItemClickListener((parent, view, position, id) -> {
            Models.Channel pick = drawerAdapter.getItem(position);
            closeDrawer();
            if (pick != null && channel != null && !pick.id.equals(channel.id)) switchChannel(pick);
        });

        videoSurface = findViewById(R.id.videoSurface);
        loadSource(0);
    }

    // ---- Muat sumber ----

    private void loadSource(int index) {
        if (channel == null || channel.sources.isEmpty()) return;
        if (index < 0 || index >= channel.sources.size()) {
            showError("Semua sumber gagal diputar. Periksa koneksi atau coba lagi.");
            return;
        }
        sourceIndex = index;
        retriedCurrent = false;
        startSource(channel.sources.get(index), index);
    }

    /** Mulai satu sumber + pasang watchdog (sumber macet → lompat berikutnya). */
    private void startSource(Models.Source s, int index) {
        errorBox.setVisibility(View.GONE);
        pLoading.setVisibility(View.VISIBLE);
        pLive.setVisibility(View.GONE);
        sourceReady = false;
        String drmLabel = s.drm != null ? (" • DRM " + s.drm.type) : "";
        pSource.setText(String.format(Locale.US, "Sumber %d/%d • %s%s",
                index + 1, channel.sources.size(),
                s.manifestType == null ? "" : s.manifestType, drmLabel));
        engine.play(s);
        updateMuteIcon();
        ui.removeCallbacks(watchdog);
        ui.postDelayed(watchdog, 15000);
    }

    /** Ulangi sumber aktif tanpa me-reset penanda retry. */
    private void replayCurrent() {
        if (channel == null || sourceIndex < 0 || sourceIndex >= channel.sources.size()) return;
        startSource(channel.sources.get(sourceIndex), sourceIndex);
    }

    /** Lanjut ke sumber berikutnya; habis → kotak error. */
    private void nextSource() {
        ui.removeCallbacks(watchdog);
        if (channel == null) return;
        int next = sourceIndex + 1;
        if (next >= channel.sources.size()) {
            String extra = lastError == null || lastError.isEmpty() ? "" : ("\n(" + lastError + ")");
            showError("Semua sumber tidak dapat diputar.\nPeriksa koneksi internet lalu coba lagi." + extra);
        } else {
            loadSource(next);
        }
    }

    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            if (sourceReady || engine.isPlaying()) return;
            nextSource();
        }
    };

    private void switchChannel(Models.Channel next) {
        channel = next;
        Store.setLastChannel(channel.id);
        pTitle.setText(channel.name);
        videoW = 16; videoH = 9;
        applyAspect();
        loadSource(0);
        drawerAdapter = new Adapters.DrawerAdapter(getLayoutInflater(),
                Data.CURRENT != null ? Data.CURRENT.channels : new ArrayList<>(), channel.id);
        drawerList.setAdapter(drawerAdapter);
        showOverlayTemporarily();
    }

    private void zap(int dir) {
        if (Data.CURRENT == null || Data.CURRENT.channels.isEmpty() || channel == null) return;
        List<Models.Channel> all = Data.CURRENT.channels;
        int idx = -1;
        for (int i = 0; i < all.size(); i++) if (all.get(i).id.equals(channel.id)) { idx = i; break; }
        if (idx < 0) return;
        int next = (idx + dir + all.size()) % all.size();
        switchChannel(all.get(next));
        Toast.makeText(this, channel.name, Toast.LENGTH_SHORT).show();
    }

    // ---- Callback engine ----

    @Override public void onBuffering(boolean loading) {
        pLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) pLive.setVisibility(View.GONE);
    }

    @Override public void onPlaying(boolean playing) {
        btnPlay.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        if (playing) {
            wantPlay = true;
            ui.removeCallbacks(watchdog);
            pLive.setVisibility(engine.isLive() ? View.VISIBLE : View.GONE);
            showOverlayTemporarily();
        }
    }

    @Override public void onVideoSize(int width, int height) {
        if (width > 0 && height > 0) {
            videoW = width;
            videoH = height;
            applyAspect();
        }
    }

    @Override public void onReady() {
        sourceReady = true;
        ui.removeCallbacks(watchdog);
        pLive.setVisibility(engine.isLive() ? View.VISIBLE : View.GONE);
        showOverlayTemporarily();
    }

    /**
     * Fallback sumber: satu kali coba ulang sumber aktif, tetap gagal → sumber berikutnya.
     * (TANPA me-reset penanda retry — dulu ini membuat fallback tak pernah maju.)
     */
    @Override public void onError(String message) {
        if (errorBox.getVisibility() == View.VISIBLE) return;
        lastError = message == null ? "" : message;
        if (!retriedCurrent) {
            retriedCurrent = true;
            ui.postDelayed(this::replayCurrent, 700);
        } else {
            nextSource();
        }
    }

    private void showError(String msg) {
        ui.removeCallbacks(watchdog);
        pLoading.setVisibility(View.GONE);
        errorBox.setVisibility(View.VISIBLE);
        pError.setText(msg);
    }

    // ---- Overlay ----

    private void playPauseClicked() {
        if (engine.isPlaying()) wantPlay = false;
        else wantPlay = true;
        engine.playPause();
        showOverlayTemporarily();
    }

    private void toggleOverlay() {
        if (drawer.getVisibility() == View.VISIBLE) { closeDrawer(); return; }
        if (overlay.getVisibility() == View.VISIBLE) hideOverlayNow();
        else showOverlayTemporarily();
    }

    private void showOverlayTemporarily() {
        if (overlay.getVisibility() != View.VISIBLE) {
            overlay.setVisibility(View.VISIBLE);
            overlay.setAlpha(0f);
            overlay.animate().alpha(1f).setDuration(180).start();
        }
        ui.removeCallbacks(hideOverlay);
        ui.postDelayed(hideOverlay, 3200);
    }

    private void hideOverlayNow() {
        ui.removeCallbacks(hideOverlay);
        overlay.animate().alpha(0f).setDuration(220).setListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                overlay.setVisibility(View.GONE);
                overlay.setAlpha(1f);
                overlay.animate().setListener(null);
            }
        }).start();
    }

    // ---- Kontrol ----

    private void toggleMute() {
        engine.setVolume(engine.getVolume() == 0f ? 1f : 0f);
        updateMuteIcon();
    }

    private void updateMuteIcon() {
        btnMute.setImageResource(engine.getVolume() == 0f ? R.drawable.ic_volume_off : R.drawable.ic_volume);
    }

    private void cycleAspect() {
        aspectMode = (aspectMode + 1) % 3;
        btnAspect.setText(aspectMode == 0 ? "FIT" : aspectMode == 1 ? "CROP" : "STRETCH");
        applyAspect();
    }

    private void applyAspect() {
        if (videoSurface == null) return;
        FrameLayout root = (FrameLayout) videoSurface.getParent();
        int rw = root.getWidth(), rh = root.getHeight();
        if (rw == 0 || rh == 0) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) videoSurface.getLayoutParams();
        if (aspectMode == 2) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        } else if (aspectMode == 1) {
            float scale = Math.max(rw / (float) videoW, rh / (float) videoH);
            lp.width = Math.round(videoW * scale);
            lp.height = Math.round(videoH * scale);
        } else {
            float scale = Math.min(rw / (float) videoW, rh / (float) videoH);
            lp.width = Math.round(videoW * scale);
            lp.height = Math.round(videoH * scale);
        }
        lp.gravity = Gravity.CENTER;
        videoSurface.setLayoutParams(lp);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyAspect();
    }

    private void showQualityDialog() {
        List<PlayerEngine.Quality> qs = engine.qualities();
        List<String> labels = new ArrayList<>();
        labels.add("AUTO (Tertinggi)");
        for (PlayerEngine.Quality q : qs) labels.add(q.label());
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Kualitas Video")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    engine.applyQuality(which == 0 ? null : qs.get(which - 1));
                    pQuality.setText(which == 0 ? "AUTO" : qs.get(which - 1).label());
                })
                .show();
        showOverlayTemporarily();
    }

    private void showSourceDialog() {
        if (channel == null || channel.sources.size() < 2) {
            Toast.makeText(this, "Hanya ada satu sumber untuk saluran ini", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < channel.sources.size(); i++) {
            Models.Source s = channel.sources.get(i);
            labels.add(String.format(Locale.US, "Sumber %d • %s%s", i + 1,
                    s.manifestType == null ? "" : s.manifestType,
                    s.drm != null ? " • " + s.drm.type : ""));
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Pilih Sumber")
                .setItems(labels.toArray(new String[0]), (d, which) -> loadSource(which))
                .show();
        showOverlayTemporarily();
    }

    // ---- Drawer saluran ----

    private void openDrawer() {
        refreshDrawer("");
        drawer.setVisibility(View.VISIBLE);
        drawer.setAlpha(0f);
        drawer.setTranslationX(60);
        drawer.animate().alpha(1f).translationX(0).setDuration(200).start();
        ui.removeCallbacks(hideOverlay);
    }

    private void closeDrawer() {
        drawer.animate().alpha(0f).translationX(60).setDuration(160).withEndAction(() -> {
            drawer.setVisibility(View.GONE);
            drawer.setAlpha(1f);
            drawer.setTranslationX(0);
        }).start();
        showOverlayTemporarily();
    }

    private void refreshDrawer(String q) {
        if (Data.CURRENT == null || drawerAdapter == null) return;
        String query = q == null ? "" : q.trim().toLowerCase(Locale.US);
        List<Models.Channel> out = new ArrayList<>();
        for (Models.Channel c : Data.CURRENT.channels) {
            if (query.isEmpty() || c.name.toLowerCase(Locale.US).contains(query)) out.add(c);
        }
        drawerAdapter.setData(out);
    }

    // ---- PIP & fokus audio ----

    private void enterPip() {
        if (Build.VERSION.SDK_INT >= 26 && engine.isPlaying()) {
            try {
                float ratio = Math.max(videoW, 1) / (float) Math.max(videoH, 1);
                float clamped = Math.min(Math.max(ratio, 0.418410f), 2.390f);
                PictureInPictureParams p = new PictureInPictureParams.Builder()
                        .setAspectRatio(new Rational((int) Math.round(clamped * 100), 100))
                        .build();
                enterPictureInPictureMode(p);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= 26 && engine.isPlaying()) enterPip();
    }

    private void requestFocus() {
        if (audio == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                    .setOnAudioFocusChangeListener(focusListener)
                    .build();
            audio.requestAudioFocus(focusRequest);
        } else {
            audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    private void abandonFocus() {
        if (audio == null) return;
        if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) audio.abandonAudioFocusRequest(focusRequest);
        else audio.abandonAudioFocus(focusListener);
    }

    // ---- Siklus hidup ----

    @Override
    public void onPictureInPictureModeChanged(boolean isInPip) {
        if (isInPip) {
            overlay.setVisibility(View.GONE);
            drawer.setVisibility(View.GONE);
            errorBox.setVisibility(View.GONE);
            ui.removeCallbacks(hideOverlay);
        } else {
            showOverlayTemporarily();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (resumeOnStart) {
            engine.setPlayWhenReady(true);
            resumeOnStart = false;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode()) return;
        resumeOnStart = engine.isPlaying();
        if (!isFinishing()) engine.setPlayWhenReady(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
        abandonFocus();
        engine.release();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                playPauseClicked();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (drawer.getVisibility() == View.VISIBLE) { closeDrawer(); return true; }
                return super.onKeyDown(keyCode, event);
            case KeyEvent.KEYCODE_CHANNEL_UP:
                zap(1);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                zap(-1);
                return true;
            default:
                showOverlayTemporarily();
                return super.onKeyDown(keyCode, event);
        }
    }

    // ---- Util ----

    public static String fmtDur(long ms) {
        long s = Math.max(0, ms / 1000);
        long d = s / 86400; s %= 86400;
        long h = s / 3600; s %= 3600;
        long m = s / 60; s %= 60;
        if (d > 0) return String.format(Locale.US, "%d hari %d jam", d, h);
        if (h > 0) return String.format(Locale.US, "%d jam %02d menit", h, m);
        if (m > 0) return String.format(Locale.US, "%d menit %02d detik", m, s);
        return String.format(Locale.US, "%d detik", s);
    }

    /** TextWatcher ringkas. */
    public static abstract class SimpleTextWatcher implements android.text.TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void afterTextChanged(android.text.Editable s) {}
        public abstract void onText(String s);
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) { onText(s.toString()); }
    }
}
