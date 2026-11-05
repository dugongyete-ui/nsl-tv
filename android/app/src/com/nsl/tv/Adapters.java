package com.nsl.tv;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/** Adapter UI: grid saluran, daftar acara, daftar saluran player. */
public final class Adapters {

    private Adapters() {}

    public interface FavListener { void onToggle(Models.Channel ch); }

    /** Grid saluran (Beranda / Favorit). */
    public static class ChannelAdapter extends BaseAdapter {
        private final Context ctx;
        private final LayoutInflater inf;
        private final FavListener favListener;
        private List<Models.Channel> items;
        private java.util.Set<String> favIds;

        public ChannelAdapter(Context ctx, List<Models.Channel> items, java.util.Set<String> favIds, FavListener favListener) {
            this.ctx = ctx;
            this.inf = LayoutInflater.from(ctx);
            this.items = items;
            this.favIds = favIds;
            this.favListener = favListener;
        }

        public void setData(List<Models.Channel> items, java.util.Set<String> favIds) {
            this.items = items;
            this.favIds = favIds;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return items == null ? 0 : items.size(); }
        @Override public Models.Channel getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            Holder h;
            if (v == null) {
                v = inf.inflate(R.layout.item_channel, parent, false);
                h = new Holder();
                h.logo = v.findViewById(R.id.chLogo);
                h.name = v.findViewById(R.id.chName);
                h.group = v.findViewById(R.id.chGroup);
                h.live = v.findViewById(R.id.chLive);
                h.fav = v.findViewById(R.id.chFav);
                v.setTag(h);
            } else {
                h = (Holder) v.getTag();
            }
            final Models.Channel ch = getItem(position);
            h.name.setText(ch.name);
            h.group.setText(ch.group);
            h.live.setVisibility(ch.liveEvent ? View.VISIBLE : View.GONE);
            boolean fav = favIds != null && favIds.contains(ch.id);
            h.fav.setImageResource(fav ? R.drawable.ic_heart : R.drawable.ic_heart_o);
            h.fav.setOnClickListener(fv -> { if (favListener != null) favListener.onToggle(ch); });
            if (ch.logoUrl != null && !ch.logoUrl.isEmpty()) {
                h.logo.setImageAlpha(255);
                ImageLoader.load(h.logo, ch.logoUrl);
            } else {
                h.logo.setImageDrawable(null);
                h.logo.setImageAlpha(60);
                h.logo.setImageResource(R.drawable.ic_tv);
            }
            return v;
        }
    }

    /** Daftar acara (tab Acara) dengan hitung mundur. */
    public static class EventAdapter extends BaseAdapter {
        private final Context ctx;
        private final LayoutInflater inf;
        private List<Models.Channel> items;

        public EventAdapter(Context ctx, List<Models.Channel> items) {
            this.ctx = ctx;
            this.inf = LayoutInflater.from(ctx);
            this.items = items;
        }

        public void setData(List<Models.Channel> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return items == null ? 0 : items.size(); }
        @Override public Models.Channel getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            Holder h;
            if (v == null) {
                v = inf.inflate(R.layout.item_event, parent, false);
                h = new Holder();
                h.logo = v.findViewById(R.id.evLogo);
                h.title = v.findViewById(R.id.evTitle);
                h.league = v.findViewById(R.id.evLeague);
                h.badge = v.findViewById(R.id.evBadge);
                h.time = v.findViewById(R.id.evTime);
                v.setTag(h);
            } else {
                h = (Holder) v.getTag();
            }
            Models.Channel ch = getItem(position);
            String title = ch.name.startsWith("\uD83D\uDD34") ? ch.name.substring(6).trim() : ch.name;
            h.title.setText(title);
            StringBuilder lg = new StringBuilder();
            if (ch.league != null && !ch.league.isEmpty()) lg.append(ch.league);
            if (ch.eventSchedule != null && !ch.eventSchedule.isEmpty()) {
                if (lg.length() > 0) lg.append(" • ");
                lg.append(ch.eventSchedule);
            }
            h.league.setText(lg.length() > 0 ? lg.toString() : ch.group);
            if (ch.logoUrl != null && !ch.logoUrl.isEmpty()) {
                h.logo.setImageAlpha(255);
                ImageLoader.load(h.logo, ch.logoUrl);
            } else {
                h.logo.setImageDrawable(null);
                h.logo.setImageAlpha(60);
                h.logo.setImageResource(R.drawable.ic_tv);
            }

            long now = System.currentTimeMillis();
            String badgeText; int badgeBg; int badgeTextCol;
            if (ch.startMs > 0 && ch.startMs <= now && (ch.endMs == 0 || ch.endMs > now)) {
                badgeText = "LIVE SEKARANG"; badgeBg = R.drawable.badge_live; badgeTextCol = Color.WHITE;
                h.time.setText(ch.endMs > 0 ? "Berakhir " + PlayerActivity.fmtDur(ch.endMs - now) : "Sedang tayang");
            } else if (ch.startMs > now) {
                badgeText = "AKAN DATANG"; badgeBg = R.drawable.badge_sun; badgeTextCol = ctx.getColor(R.color.sun);
                h.time.setText("Mulai " + PlayerActivity.fmtDur(ch.startMs - now));
            } else {
                badgeText = "SELESAI"; badgeBg = R.drawable.badge_soft; badgeTextCol = ctx.getColor(R.color.text_lo);
                h.time.setText(ch.eventSchedule != null ? ch.eventSchedule : "");
            }
            h.badge.setText(badgeText);
            h.badge.setBackgroundResource(badgeBg);
            h.badge.setTextColor(badgeTextCol);
            return v;
        }
    }

    /** Daftar saluran drawer player. */
    public static class DrawerAdapter extends BaseAdapter {
        private final LayoutInflater inf;
        private List<Models.Channel> items;
        private final String activeId;

        public DrawerAdapter(LayoutInflater inf, List<Models.Channel> items, String activeId) {
            this.inf = inf;
            this.items = items;
            this.activeId = activeId;
        }

        public void setData(List<Models.Channel> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return items == null ? 0 : items.size(); }
        @Override public Models.Channel getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            Holder h;
            if (v == null) {
                v = inf.inflate(R.layout.item_drawer_channel, parent, false);
                h = new Holder();
                h.logo = v.findViewById(R.id.dwLogo);
                h.name = v.findViewById(R.id.dwName);
                h.group = v.findViewById(R.id.dwGroup);
                h.badge = v.findViewById(R.id.dwBadge);
                v.setTag(h);
            } else {
                h = (Holder) v.getTag();
            }
            Models.Channel ch = getItem(position);
            h.name.setText(ch.name);
            h.group.setText(ch.group);
            h.badge.setVisibility(ch.liveEvent ? View.VISIBLE : View.GONE);
            if (ch.id != null && ch.id.equals(activeId)) {
                v.setBackgroundColor(0x26FFC93C);
            } else {
                v.setBackgroundColor(Color.TRANSPARENT);
            }
            if (ch.logoUrl != null && !ch.logoUrl.isEmpty()) {
                h.logo.setImageAlpha(255);
                ImageLoader.load(h.logo, ch.logoUrl);
            } else {
                h.logo.setImageDrawable(null);
                h.logo.setImageAlpha(60);
                h.logo.setImageResource(R.drawable.ic_tv);
            }
            return v;
        }
    }

    private static final class Holder {
        ImageView logo, fav;
        TextView name, group, live, title, league, badge, time;
    }
}
