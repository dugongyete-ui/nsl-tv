package com.nsl.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * NSL TV — MainActivity.
 * Alur: boot (server → saluran) → beranda/acara/favorit/setelan.
 * Tanpa sistem lisensi — aplikasi pribadi, akses penuh tanpa batas.
 */
public class MainActivity extends Activity {

    private View bootView, mainView, blockedView;
    private TextView bootStatus, blockedMsg;
    private LinearLayout chipsRow;

    // main
    private GridView gridHome, gridFav;
    private ListView listEvents;
    private TextView emptyState;
    private View searchRow, settingsView;
    private LinearLayout settingsBox;
    private LinearLayout navHome, navEvents, navFav, navSettings;
    private ImageView navHomeIcon, navEventsIcon, navFavIcon, navSettingsIcon;
    private EditText searchInput;

    private Adapters.ChannelAdapter homeAdapter, favAdapter;
    private Adapters.EventAdapter eventAdapter;
    private List<Models.Channel> homeItems = new ArrayList<>();
    private List<Models.Channel> eventItems = new ArrayList<>();
    private List<Models.Channel> favItems = new ArrayList<>();

    private String category = "SEMUA";
    private String query = "";
    private int tab = 0; // 0 home, 1 events, 2 fav, 3 settings

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final AtomicBoolean booted = new AtomicBoolean(false);
    private Runnable eventTicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Api.init(getApplicationContext());
        Store.init(getApplicationContext());
        setContentView(R.layout.activity_main);

        bootView = findViewById(R.id.bootView);
        mainView = findViewById(R.id.mainView);
        blockedView = findViewById(R.id.blockedView);
        bootStatus = findViewById(R.id.bootStatus);
        blockedMsg = findViewById(R.id.blockedMsg);

        findViewById(R.id.btnRetryBlocked).setOnClickListener(v -> {
            blockedView.setVisibility(View.GONE);
            showOnly(bootView);
            boot();
        });

        wireMain();

        showOnly(bootView);
        boot();
    }

    // =========================================================
    // BOOT
    // =========================================================

    private void setBoot(String msg) {
        ui.post(() -> bootStatus.setText(msg));
    }

    private void boot() {
        booted.set(false);
        new Thread(() -> {
            try {
                // 1. server
                setBoot(getString(R.string.boot_checking));
                Data.parseChannels(Api.get("/api/v1/channels", Store.deviceId(this)));
                setBoot(getString(R.string.boot_ready));
                ui.postDelayed(this::enterMain, 350);
            } catch (final Api.HttpException he) {
                // Server menolak device ini → putar device id sekali (akses tanpa batas)
                if (he.code == 403 && rotateAndRetry()) return;
                ui.post(() -> {
                    blockedMsg.setText("Gagal terhubung ke server.\n" + shortMsg(he.getMessage()));
                    showOnly(blockedView);
                });
            } catch (final Exception e) {
                ui.post(() -> {
                    blockedMsg.setText("Terjadi kesalahan.\n" + shortMsg(e.getMessage()));
                    showOnly(blockedView);
                });
            }
        }, "boot").start();
    }

    /** Ganti identitas perangkat lalu coba muat saluran sekali lagi. */
    private boolean rotateAndRetry() {
        try {
            Store.rotateDeviceId(this);
            setBoot(getString(R.string.boot_channels));
            Data.parseChannels(Api.get("/api/v1/channels", Store.deviceId(this)));
            setBoot(getString(R.string.boot_ready));
            ui.postDelayed(this::enterMain, 350);
            return true;
        } catch (final Exception e) {
            ui.post(() -> {
                blockedMsg.setText("Gagal terhubung ke server.\n" + shortMsg(e.getMessage()));
                showOnly(blockedView);
            });
            return true;
        }
    }

    private void enterMain() {
        if (Data.CURRENT == null) return;
        booted.set(true);
        showOnly(mainView);
        rebuildChips();
        applyFilter();
        buildSettings();
        if (Store.autoPlayLast()) {
            String lastId = Store.lastChannelId();
            if (lastId != null && Data.byId(lastId) != null) {
                openPlayer(lastId);
            }
        }
    }

    private void openPlayer(String channelId) {
        android.content.Intent it = new android.content.Intent(this, PlayerActivity.class);
        it.putExtra(PlayerActivity.EXTRA_CHANNEL_ID, channelId);
        startActivity(it);
    }

    // =========================================================
    // MAIN UI
    // =========================================================

    private void wireMain() {
        gridHome = findViewById(R.id.gridHome);
        gridFav = findViewById(R.id.gridFav);
        listEvents = findViewById(R.id.listEvents);
        emptyState = findViewById(R.id.emptyState);
        searchRow = findViewById(R.id.searchRow);
        settingsView = findViewById(R.id.settingsView);
        settingsBox = findViewById(R.id.settingsBox);
        chipsRow = findViewById(R.id.chipsRow);
        navHome = findViewById(R.id.navHome);
        navEvents = findViewById(R.id.navEvents);
        navFav = findViewById(R.id.navFav);
        navSettings = findViewById(R.id.navSettings);
        navHomeIcon = findViewById(R.id.navHomeIcon);
        navEventsIcon = findViewById(R.id.navEventsIcon);
        navFavIcon = findViewById(R.id.navFavIcon);
        navSettingsIcon = findViewById(R.id.navSettingsIcon);
        searchInput = findViewById(R.id.searchInput);

        homeAdapter = new Adapters.ChannelAdapter(this, homeItems, Store.favorites(), this::toggleFav);
        gridHome.setAdapter(homeAdapter);
        favAdapter = new Adapters.ChannelAdapter(this, favItems, Store.favorites(), this::toggleFav);
        gridFav.setAdapter(favAdapter);
        eventAdapter = new Adapters.EventAdapter(this, eventItems);
        listEvents.setAdapter(eventAdapter);

        android.widget.AdapterView.OnItemClickListener open = (parent, view, position, id) -> {
            Models.Channel ch = (Models.Channel) parent.getItemAtPosition(position);
            if (ch != null) openPlayer(ch.id);
        };
        gridHome.setOnItemClickListener(open);
        gridFav.setOnItemClickListener(open);
        listEvents.setOnItemClickListener(open);

        navHome.setOnClickListener(v -> setTab(0));
        navEvents.setOnClickListener(v -> setTab(1));
        navFav.setOnClickListener(v -> setTab(2));
        navSettings.setOnClickListener(v -> setTab(3));

        findViewById(R.id.btnSearch).setOnClickListener(v -> {
            boolean show = searchRow.getVisibility() != View.VISIBLE;
            searchRow.setVisibility(show ? View.VISIBLE : View.GONE);
            findViewById(R.id.chipsScroll).setVisibility(show ? View.GONE : View.VISIBLE);
            if (show) searchInput.requestFocus();
            else { searchInput.setText(""); }
        });
        findViewById(R.id.btnSearchClose).setOnClickListener(v -> {
            searchInput.setText("");
            searchRow.setVisibility(View.GONE);
            findViewById(R.id.chipsScroll).setVisibility(View.VISIBLE);
        });
        searchInput.addTextChangedListener(new PlayerActivity.SimpleTextWatcher() {
            @Override public void onText(String s) { query = s; applyFilter(); }
        });
    }

    private void toggleFav(Models.Channel ch) {
        boolean now = !Store.isFavorite(ch.id);
        Store.setFavorite(ch.id, now);
        Set<String> favs = Store.favorites();
        homeAdapter.setData(homeItems, favs);
        favAdapter.setData(favItems, favs);
        Toast.makeText(this, now ? "Ditambahkan ke Favorit" : "Dihapus dari Favorit", Toast.LENGTH_SHORT).show();
        if (tab == 2) { favItems = Data.favorites(Data.CURRENT.channels, favs, query); favAdapter.setData(favItems, favs); }
        updateEmptyState();
    }

    private void rebuildChips() {
        chipsRow.removeAllViews();
        List<String> cats = new ArrayList<>();
        cats.add("SEMUA");
        if (Data.CURRENT != null) cats.addAll(Data.CURRENT.categories);
        for (String c : cats) {
            TextView chip = new TextView(this);
            chip.setText(c.equals("SEMUA") ? "SEMUA" : c);
            chip.setTextSize(11.5f);
            chip.setTypeface(null, android.graphics.Typeface.BOLD);
            chip.setPadding(dp(14), dp(7), dp(14), dp(7));
            chip.setGravity(Gravity.CENTER);
            chip.setAllCaps(true);
            chip.setLetterSpacing(0.05f);
            chip.setOnClickListener(v -> {
                category = c.toString();
                styleChips();
                applyFilter();
            });
            chip.setTag(c);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            chipsRow.addView(chip, lp);
        }
        styleChips();
    }

    private void styleChips() {
        for (int i = 0; i < chipsRow.getChildCount(); i++) {
            TextView chip = (TextView) chipsRow.getChildAt(i);
            boolean active = chip.getTag() != null && category.equals(chip.getTag().toString());
            chip.setBackgroundResource(active ? R.drawable.chip_active : R.drawable.chip_normal);
            chip.setTextColor(active ? getColor(R.color.sun_ink) : getColor(R.color.text_mid));
            if (active) chipsScroll().post(() -> chipsScroll().smoothScrollTo(chip.getLeft() - dp(40), 0));
        }
    }

    private android.widget.HorizontalScrollView chipsScroll() {
        return (android.widget.HorizontalScrollView) findViewById(R.id.chipsScroll);
    }

    private void applyFilter() {
        if (Data.CURRENT == null) return;
        Set<String> favs = Store.favorites();
        homeItems = Data.byCategory(Data.CURRENT.channels, category, query);
        favItems = Data.favorites(Data.CURRENT.channels, favs, query);
        eventItems = Data.events(Data.CURRENT.channels);
        homeAdapter.setData(homeItems, favs);
        favAdapter.setData(favItems, favs);
        eventAdapter.setData(eventItems);
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean empty;
        if (tab == 0) empty = homeItems.isEmpty();
        else if (tab == 1) empty = eventItems.isEmpty();
        else if (tab == 2) empty = favItems.isEmpty();
        else empty = false;
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            if (tab == 2) emptyState.setText("Belum ada favorit\nTekan ikon hati pada saluran");
            else if (tab == 1) emptyState.setText("Belum ada jadwal acara");
            else emptyState.setText("Saluran tidak ditemukan");
        }
    }

    private void setTab(int t) {
        tab = t;
        gridHome.setVisibility(t == 0 ? View.VISIBLE : View.GONE);
        listEvents.setVisibility(t == 1 ? View.VISIBLE : View.GONE);
        gridFav.setVisibility(t == 2 ? View.VISIBLE : View.GONE);
        settingsView.setVisibility(t == 3 ? View.VISIBLE : View.GONE);
        findViewById(R.id.chipsScroll).setVisibility(t == 0 && searchRow.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE);
        styleNav();
        updateEmptyState();
        if (t == 1) startEventTicker(); else stopEventTicker();
        if (t == 2) {
            favItems = Data.favorites(Data.CURRENT.channels, Store.favorites(), query);
            favAdapter.setData(favItems, Store.favorites());
            updateEmptyState();
        }
    }

    private void styleNav() {
        int active = getColor(R.color.sun);
        int idle = getColor(R.color.text_lo);
        navHomeIcon.setColorFilter(tab == 0 ? active : idle);
        navEventsIcon.setColorFilter(tab == 1 ? active : idle);
        navFavIcon.setImageResource(tab == 2 ? R.drawable.ic_heart : R.drawable.ic_heart_o);
        navFavIcon.setColorFilter(tab == 2 ? getColor(R.color.live) : idle);
        navSettingsIcon.setColorFilter(tab == 3 ? active : idle);
        navHomeText().setTextColor(tab == 0 ? active : idle);
        navEventsText().setTextColor(tab == 1 ? active : idle);
        navFavText().setTextColor(tab == 2 ? (tab == 2 ? getColor(R.color.live) : idle) : idle);
        navSettingsText().setTextColor(tab == 3 ? active : idle);
    }

    private TextView navHomeText() { return findViewById(R.id.navHomeText); }
    private TextView navEventsText() { return findViewById(R.id.navEventsText); }
    private TextView navFavText() { return findViewById(R.id.navFavText); }
    private TextView navSettingsText() { return findViewById(R.id.navSettingsText); }

    private void startEventTicker() {
        stopEventTicker();
        eventTicker = new Runnable() {
            @Override public void run() {
                if (tab == 1 && eventAdapter != null && listEvents.getVisibility() == View.VISIBLE) {
                    eventAdapter.notifyDataSetChanged();
                    ui.postDelayed(this, 1000);
                }
            }
        };
        ui.postDelayed(eventTicker, 1000);
    }

    private void stopEventTicker() {
        if (eventTicker != null) { ui.removeCallbacks(eventTicker); eventTicker = null; }
    }

    // =========================================================
    // SETELAN
    // =========================================================

    private void buildSettings() {
        settingsBox.removeAllViews();

        // Kartu perangkat
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_soft);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.addView(label("AKSES", 10, R.color.text_lo, true));
        card.addView(label("Penuh • Tanpa Batas", 14, R.color.sun, true));

        View gap = new View(this);
        gap.setBackgroundResource(R.drawable.divider);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        glp.topMargin = dp(10);
        glp.bottomMargin = dp(10);
        card.addView(gap, glp);

        card.addView(label("Perangkat", 11, R.color.text_lo, false));
        card.addView(label(Api.deviceModel() + " • " + Api.deviceType(), 12.5f, R.color.text_hi, false));
        card.addView(label("ID: " + Store.deviceId(this), 11, R.color.text_lo, false));

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(10);
        settingsBox.addView(card, clp);

        // Baris switch
        LinearLayout rowSwitch = new LinearLayout(this);
        rowSwitch.setOrientation(LinearLayout.HORIZONTAL);
        rowSwitch.setGravity(Gravity.CENTER_VERTICAL);
        rowSwitch.setBackgroundResource(R.drawable.card_soft);
        rowSwitch.setPadding(dp(16), dp(6), dp(12), dp(6));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.bottomMargin = dp(10);
        LinearLayout colTxt = new LinearLayout(this);
        colTxt.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = label("Lanjutkan saluran terakhir", 13.5f, R.color.text_hi, true);
        TextView t2 = label("Otomatis membuka saluran yang terakhir ditonton", 10.5f, R.color.text_lo, false);
        colTxt.addView(t1);
        colTxt.addView(t2);
        rowSwitch.addView(colTxt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(Store.autoPlayLast());
        sw.setOnCheckedChangeListener((b, v) -> Store.setAutoPlayLast(v));
        rowSwitch.addView(sw);
        settingsBox.addView(rowSwitch, slp);

        // Tombol aksi
        settingsBox.addView(ghostButton("Segarkan Daftar Saluran", v -> {
            showOnly(bootView);
            boot();
        }), clp2());

        settingsBox.addView(ghostButton("Hapus Semua Favorit", v -> {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Hapus favorit?")
                    .setMessage("Semua saluran favorit akan dihapus.")
                    .setPositiveButton("Hapus", (d, w) -> {
                        Store.clearFavorites();
                        applyFilter();
                        Toast.makeText(this, "Favorit dikosongkan", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Batal", null)
                    .show();
        }), clp2());

        TextView ver = label("NSL TV v1.0.2", 11, R.color.text_lo, false);
        ver.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = dp(16);
        settingsBox.addView(ver, vlp);
    }

    private LinearLayout.LayoutParams clp2() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        return lp;
    }

    private android.widget.Button ghostButton(String text, View.OnClickListener onClick) {
        android.widget.Button b = new android.widget.Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13.5f);
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        b.setTextColor(getColor(R.color.text_hi));
        b.setBackgroundResource(R.drawable.btn_ghost);
        b.setElevation(0);
        b.setStateListAnimator(null);
        b.setOnClickListener(onClick);
        return b;
    }

    private TextView label(String text, float sizeSp, int colorRes, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sizeSp);
        t.setTextColor(getColor(colorRes));
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    // =========================================================
    // UTIL
    // =========================================================

    private void showOnly(View v) {
        bootView.setVisibility(v == bootView ? View.VISIBLE : View.GONE);
        mainView.setVisibility(v == mainView ? View.VISIBLE : View.GONE);
        blockedView.setVisibility(v == blockedView ? View.VISIBLE : View.GONE);
    }

    private static String shortMsg(String m) {
        if (m == null || m.trim().isEmpty()) return "Tidak diketahui";
        return m.length() > 160 ? m.substring(0, 160) + "…" : m;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopEventTicker();
        ui.removeCallbacksAndMessages(null);
    }

}
