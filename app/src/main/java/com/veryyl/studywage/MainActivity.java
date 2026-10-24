package com.veryyl.studywage;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hosts the Study Wage page (assets/index.html) in a WebView and gives it two
 * native powers a plain web page lacks: saving its state on the phone, and
 * calling the Toggl Track API directly.
 */
public class MainActivity extends Activity {

    private static final String TOGGL_HOST = "https://api.track.toggl.com";

    private WebView web;
    private SharedPreferences prefs;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = State.prefs(this);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        web.addJavascriptInterface(new Bridge(), "Native");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception ignored) {
                    }
                    return true;
                }
                return false;
            }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.evaluateJavascript("window.onNativeResume && window.onNativeResume()", null);
        }
    }

    @Override
    protected void onDestroy() {
        pool.shutdownNow();
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    private void deliver(final String id, final int code, final String body) {
        final String js = "window.__native && window.__native(" + JSONObject.quote(id) + "," + code + ","
                + JSONObject.quote(body == null ? "" : body) + ")";
        runOnUiThread(() -> {
            if (web != null) web.evaluateJavascript(js, null);
        });
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    private class Bridge {
        @JavascriptInterface
        public String load() {
            return prefs.getString("state", "");
        }

        @JavascriptInterface
        public void save(String state) {
            prefs.edit().putString("state", state).commit();
            StudyWidget.renderAll(MainActivity.this, null);
        }

        /** GET a Toggl Track API path (must start with /api/v9/) and call window.__native(id, status, body). */
        @JavascriptInterface
        public void toggl(final String id, final String path, final String token) {
            if (path == null || !path.startsWith("/api/v9/") || token == null || token.isEmpty()) {
                deliver(id, 0, "bad request");
                return;
            }
            pool.execute(() -> {
                int code = 0;
                String body;
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(TOGGL_HOST + path).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(20000);
                    String auth = Base64.encodeToString(
                            (token.trim() + ":api_token").getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                    c.setRequestProperty("Authorization", "Basic " + auth);
                    c.setRequestProperty("Content-Type", "application/json");
                    code = c.getResponseCode();
                    body = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
                } catch (Exception e) {
                    body = String.valueOf(e.getMessage());
                } finally {
                    if (c != null) c.disconnect();
                }
                deliver(id, code, body);
            });
        }
    }
}
