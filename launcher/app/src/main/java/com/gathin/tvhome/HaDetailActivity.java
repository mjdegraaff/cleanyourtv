package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Full-screen Home Assistant detail screens (energy, weather map, location map), opened from a tile in
 * {@link HaStrip}.
 *
 * The screens are pages bundled in the APK (assets/ha/). Like the tiles, they never talk to Home
 * Assistant: HA writes {@code ha_detail.json} next to {@code ha_tiles.json} over ADB, and a page reads it
 * through the {@code Ha} JavaScript bridge. Only public map and radar tiles come from the network.
 *
 * Left/Right switch between the screens, Back returns to Home.
 */
public class HaDetailActivity extends Activity {
    private static final String TAG = "TvHome.HaDetail";
    static final String EXTRA_SCREEN = "screen";
    static final String DATA_FILE = "ha_detail.json";
    private static final List<String> SCREENS = Arrays.asList("energy", "weather", "map");
    private static final int MAX_BYTES = 512 * 1024;

    private WebView web;
    private int index;

    static boolean isScreen(String s) {
        return s != null && SCREENS.contains(s);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // No FLAG_KEEP_SCREEN_ON: a detail screen left open must still let the screensaver and sleep start.
        String screen = getIntent().getStringExtra(EXTRA_SCREEN);
        index = Math.max(0, SCREENS.indexOf(screen));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#070A14"));
        web.setFocusable(true);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);           // file:///android_asset only; no content or other file URLs
        ws.setAllowContentAccess(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ws.setMediaPlaybackRequiresUserGesture(true);
        ws.setSupportZoom(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;   // pages never navigate away; links do nothing on a TV
            }
        });
        web.addJavascriptInterface(new Bridge(), "Ha");
        setContentView(web);
        load();
        web.requestFocus();
    }

    private void load() {
        web.loadUrl("file:///android_asset/ha/" + SCREENS.get(index) + ".html");
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                int dir = code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
                index = (index + dir + SCREENS.size()) % SCREENS.size();
                load();
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (event.getAction() == KeyEvent.ACTION_UP) finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        web.removeJavascriptInterface("Ha");
        web.destroy();
        super.onDestroy();
    }

    /** Read-only access for the bundled pages. */
    private final class Bridge {
        /** The latest ha_detail.json as text, or "" when missing or too large. */
        @JavascriptInterface
        public String data() {
            try {
                File dir = Config.dir(HaDetailActivity.this);
                File f = dir != null ? new File(dir, DATA_FILE) : null;
                if (f == null || !f.isFile() || f.length() > MAX_BYTES) return "";
                byte[] buf = new byte[(int) f.length()];
                int n = 0;
                try (InputStream in = new FileInputStream(f)) {
                    int r;
                    while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
                }
                return new String(buf, 0, n, StandardCharsets.UTF_8);
            } catch (Exception e) {
                Log.w(TAG, "read failed: " + e.getMessage());
                return "";
            }
        }

        /** Milliseconds since HA last wrote the file, or -1. */
        @JavascriptInterface
        public long age() {
            File dir = Config.dir(HaDetailActivity.this);
            File f = dir != null ? new File(dir, DATA_FILE) : null;
            return f != null && f.isFile() ? System.currentTimeMillis() - f.lastModified() : -1;
        }

        @JavascriptInterface
        public String screens() {
            return String.join(",", SCREENS) + ";" + index;
        }
    }
}
