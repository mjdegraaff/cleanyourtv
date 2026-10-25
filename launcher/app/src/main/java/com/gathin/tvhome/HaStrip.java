package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
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
 *   {"label": "Persoon", "value": "Thuis", "sub": "sinds 18:02", "accent": "#7FD1AE"},
 *   {"label": "Zon vandaag", "value": "8,2 kWh", "open": "energy"}
 * ]}
 * </pre>
 *
 * A tile without {@code open} is a read-only glance and takes no focus. A tile with {@code open}
 * (energy, weather or map) is focusable: OK opens {@link HaDetailActivity} on that screen, Down goes
 * back to the dock, Up to the top bar. Values are updated in place, so a focused tile keeps focus
 * while the minute push replaces the file.
 */
final class HaStrip {
    private static final String TAG = "TvHome.HaStrip";
    static final String FILE_NAME = "ha_tiles.json";
    static final long REFRESH_MS = 30_000L;
    private static final long STALE_MS = 15 * 60_000L;
    private static final int MAX_BYTES = 16 * 1024;
    private static final int MAX_TILES = 8;

    /** What the strip needs from Home: where Down goes. */
    interface Host {
        void focusDockFromStrip();
    }

    private final Context ctx;
    private final Host host;
    private final boolean scaleOnFocus;
    private final LinearLayout strip;
    /** Signature of the shown tiles (count + open targets): same signature, update texts in place. */
    private String shownShape = "";
    private final float density;
    private final Typeface medium;
    private final Typeface regular;
    private long shownModified = -1;

    HaStrip(Context ctx, LinearLayout strip, Host host, boolean scaleOnFocus) {
        this.ctx = ctx;
        this.host = host;
        this.scaleOnFocus = scaleOnFocus;
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

    /** Focuses the first clickable tile; false when there is none (row hidden or read-only). */
    boolean focusFirst() {
        if (strip.getVisibility() != View.VISIBLE) return false;
        for (int i = 0; i < strip.getChildCount(); i++) {
            View c = strip.getChildAt(i);
            if (c.isFocusable()) return c.requestFocus();
        }
        return false;
    }

    private void hide() {
        shownModified = -1;
        shownShape = "";
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
        int count = Math.min(tiles.length(), MAX_TILES);
        java.util.List<JSONObject> list = new java.util.ArrayList<>();
        StringBuilder shape = new StringBuilder();
        for (int i = 0; i < count; i++) {
            JSONObject t = tiles.optJSONObject(i);
            if (t == null || clean(t.optString("value", ""), 28).isEmpty()) continue;
            list.add(t);
            shape.append(target(t)).append('|');
        }
        if (shape.toString().equals(shownShape) && strip.getChildCount() == list.size()) {
            for (int i = 0; i < list.size(); i++) fill((LinearLayout) strip.getChildAt(i), list.get(i));
            return;
        }
        View focused = strip.findFocus();
        int focusIndex = focused != null ? strip.indexOfChild(focused) : -1;
        strip.removeAllViews();
        for (int i = 0; i < list.size(); i++) {
            LinearLayout box = tile(target(list.get(i)), i == 0);
            fill(box, list.get(i));
            strip.addView(box);
        }
        shownShape = shape.toString();
        strip.setVisibility(strip.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        if (focusIndex >= 0) {
            if (focusIndex < strip.getChildCount() && strip.getChildAt(focusIndex).isFocusable()) {
                strip.getChildAt(focusIndex).requestFocus();
            } else if (!focusFirst()) {
                host.focusDockFromStrip();
            }
        }
    }

    /** The detail screen a tile opens, or "" for a read-only tile. Only known screens count. */
    private static String target(JSONObject t) {
        String open = t.optString("open", "");
        return HaDetailActivity.isScreen(open) ? open : "";
    }

    private void fill(LinearLayout box, JSONObject t) {
        String label = clean(t.optString("label", ""), 24);
        String value = clean(t.optString("value", ""), 28);
        String sub = clean(t.optString("sub", ""), 40);
        int accent = color(t.optString("accent", ""));
        boolean clickable = box.isFocusable();
        TextView l = (TextView) box.getChildAt(0), v = (TextView) box.getChildAt(1), s = (TextView) box.getChildAt(2);
        l.setText(label);
        l.setVisibility(label.isEmpty() ? View.GONE : View.VISIBLE);
        l.setTextColor(states(accent != 0 ? accent : 0x8CFFFFFF, 0xB3111111, clickable));
        v.setText(value);
        s.setText(sub);
        s.setVisibility(sub.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Text colour that turns dark on the white focused tile. */
    private static ColorStateList states(int normal, int focused, boolean clickable) {
        if (!clickable) return ColorStateList.valueOf(normal);
        return new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{focused, normal});
    }

    private LinearLayout tile(String open, boolean first) {
        boolean clickable = !open.isEmpty();
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackgroundResource(clickable ? R.drawable.ha_tile_bg_click : R.drawable.ha_tile_bg);
        box.setFocusable(clickable);
        box.setClickable(clickable);
        box.setImportantForAccessibility(clickable ? View.IMPORTANT_FOR_ACCESSIBILITY_YES
                : View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int padH = dp(16), padV = dp(10);
        box.setPadding(padH, padV, padH, padV);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(first ? dp(12) : dp(10));
        box.setLayoutParams(lp);

        TextView l = text(11, medium);
        l.setAllCaps(true);
        l.setLetterSpacing(0.18f);
        box.addView(l);
        TextView v = text(21, regular);
        v.setTextColor(states(0xFFFFFFFF, 0xFF111111, clickable));
        v.setPadding(0, dp(2), 0, 0);
        box.addView(v);
        TextView s = text(12, regular);
        s.setTextColor(states(0xA6FFFFFF, 0x99111111, clickable));
        s.setPadding(0, dp(1), 0, 0);
        box.addView(s);

        if (clickable) {
            box.setOnClickListener(x -> {
                Intent i = new Intent(ctx, HaDetailActivity.class).putExtra(HaDetailActivity.EXTRA_SCREEN, open);
                try {
                    ctx.startActivity(i);
                } catch (Exception e) {
                    Log.w(TAG, "open " + open + " failed: " + e.getMessage());
                }
            });
            box.setOnKeyListener((x, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    host.focusDockFromStrip();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    // Stay in the row at its ends; skip read-only tiles in between.
                    int dir = (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) != (strip.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) ? 1 : -1;
                    for (int j = strip.indexOfChild(x) + dir; j >= 0 && j < strip.getChildCount(); j += dir) {
                        if (strip.getChildAt(j).isFocusable()) {
                            strip.getChildAt(j).requestFocus();
                            x.playSoundEffect(dir > 0 ? android.view.SoundEffectConstants.NAVIGATION_RIGHT
                                    : android.view.SoundEffectConstants.NAVIGATION_LEFT);
                            return true;
                        }
                    }
                    return true;
                }
                return false;   // Up: the focus finder goes to the top bar
            });
            if (scaleOnFocus) {
                box.setOnFocusChangeListener((x, focused) -> x.animate()
                        .scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f).setDuration(160).start());
            }
        }
        return box;
    }

    private TextView text(float sp, Typeface face) {
        TextView tv = new TextView(ctx);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (face != null) tv.setTypeface(face);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setIncludeFontPadding(false);
        tv.setGravity(Gravity.LEFT);
        tv.setDuplicateParentStateEnabled(true);
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
