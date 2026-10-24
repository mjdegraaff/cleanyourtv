package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * A row of read-only info tiles above the dock, filled from {@code ha_tiles.json} in the launcher's
 * external files directory ({@code /sdcard/Android/data/com.gathin.tvhome/files/}).
 *
 * The launcher never talks to the network for this: Home Assistant (or anything with ADB) writes the
 * file, and the launcher re-reads it while Home is in front. A missing, unreadable or stale file
 * (not written for {@link #STALE_MS}) hides the row, so a stopped automation never leaves old numbers
 * on screen.
 *
 * <pre>
 * {"tiles": [
 *   {"label": "Michiel", "value": "Thuis", "sub": "sinds 18:02", "accent": "#7FD1AE"},
 *   {"label": "Zon vandaag", "value": "8,2 kWh"}
 * ]}
 * </pre>
 *
 * Tiles are not focusable: the row is a glance, D-pad navigation stays exactly as it was.
 */
final class HaStrip {
    private static final String TAG = "TvHome.HaStrip";
    static final String FILE_NAME = "ha_tiles.json";
    static final long REFRESH_MS = 30_000L;
    private static final long STALE_MS = 15 * 60_000L;
    private static final int MAX_BYTES = 16 * 1024;
    private static final int MAX_TILES = 8;

    private final Context ctx;
    private final LinearLayout strip;
    private final float density;
    private final Typeface medium;
    private final Typeface regular;
    private long shownModified = -1;

    HaStrip(Context ctx, LinearLayout strip) {
        this.ctx = ctx;
        this.strip = strip;
        this.density = ctx.getResources().getDisplayMetrics().density;
        Typeface m = null, r = null;
        try {
            m = ctx.getResources().getFont(R.font.outfit_medium);
            r = ctx.getResources().getFont(R.font.outfit_regular);
        } catch (Exception ignored) {
            // Fonts are bundled; fall back to the default face if a vendor build cannot load them.
        }
        medium = m;
        regular = r;
    }

    /** Re-reads the file if it changed; hides the row when it is missing or stale. Never throws. */
    void refresh() {
        try {
            File dir = Config.dir(ctx);
            File file = dir != null ? new File(dir, FILE_NAME) : null;
            if (file == null || !file.isFile() || !file.canRead()) {
                hide();
                return;
            }
            long modified = file.lastModified();
            if (System.currentTimeMillis() - modified > STALE_MS) {
                hide();
                return;
            }
            if (modified == shownModified && strip.getVisibility() == View.VISIBLE) return;
            JSONArray tiles = read(file);
            if (tiles == null || tiles.length() == 0) {
                hide();
                return;
            }
            build(tiles);
            shownModified = modified;
        } catch (Exception e) {
            Log.w(TAG, "refresh failed: " + e.getMessage());
            hide();
        }
    }

    private void hide() {
        shownModified = -1;
        if (strip.getVisibility() != View.GONE) {
            strip.removeAllViews();
            strip.setVisibility(View.GONE);
        }
    }

    private static JSONArray read(File file) throws Exception {
        if (file.length() > MAX_BYTES) {
            Log.w(TAG, "ignoring " + file + ": larger than " + MAX_BYTES + " bytes");
            return null;
        }
        byte[] buf = new byte[(int) file.length()];
        int n = 0;
        try (InputStream in = new FileInputStream(file)) {
            int r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
        }
        String text = new String(buf, 0, n, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) text = text.substring(1);
        return new JSONObject(text).optJSONArray("tiles");
    }

    private void build(JSONArray tiles) {
        strip.removeAllViews();
        int count = Math.min(tiles.length(), MAX_TILES);
        for (int i = 0; i < count; i++) {
            JSONObject t = tiles.optJSONObject(i);
            if (t == null) continue;
            String label = clean(t.optString("label", ""), 24);
            String value = clean(t.optString("value", ""), 28);
            if (value.isEmpty()) continue;
            String sub = clean(t.optString("sub", ""), 40);
            int accent = color(t.optString("accent", ""));
            strip.addView(tile(label, value, sub, accent, i == 0));
        }
        strip.setVisibility(strip.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private View tile(String label, String value, String sub, int accent, boolean first) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackgroundResource(R.drawable.ha_tile_bg);
        box.setFocusable(false);
        box.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int padH = dp(16), padV = dp(10);
        box.setPadding(padH, padV, padH, padV);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(first ? dp(12) : dp(10));
        box.setLayoutParams(lp);

        if (!label.isEmpty()) {
            TextView l = text(label, 11, medium, accent != 0 ? accent : 0x8CFFFFFF);
            l.setAllCaps(true);
            l.setLetterSpacing(0.18f);
            box.addView(l);
        }
        TextView v = text(value, 21, regular, 0xFFFFFFFF);
        v.setPadding(0, dp(2), 0, 0);
        box.addView(v);
        if (!sub.isEmpty()) {
            TextView s = text(sub, 12, regular, 0xA6FFFFFF);
            s.setPadding(0, dp(1), 0, 0);
            box.addView(s);
        }
        return box;
    }

    private TextView text(String s, float sp, Typeface face, int color) {
        TextView tv = new TextView(ctx);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        if (face != null) tv.setTypeface(face);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setIncludeFontPadding(false);
        tv.setGravity(Gravity.LEFT);
        return tv;
    }

    /** Printable text only, trimmed and capped; strips bidi overrides and line breaks. */
    private static String clean(String s, int max) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length() && out.length() < max; i++) {
            char c = s.charAt(i);
            if (Character.isISOControl(c)) continue;
            if (c >= '‪' && c <= '‮') continue;
            if (c >= '⁦' && c <= '⁩') continue;
            if (c == ' ' || c == ' ') continue;
            out.append(c);
        }
        return out.toString().trim();
    }

    private static int color(String s) {
        if (s == null || s.length() != 7 || s.charAt(0) != '#') return 0;
        try {
            return Color.parseColor(s);
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    private int dp(int v) {
        return Math.round(v * density);
    }
}
