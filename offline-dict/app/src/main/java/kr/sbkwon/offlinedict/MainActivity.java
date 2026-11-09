package kr.sbkwon.offlinedict;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView web;
    private volatile DictDb dict;
    private volatile String state = "copy";
    private volatile int progress = 0;
    private volatile String error = "";
    private volatile String pendingQuery = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 21) w.setStatusBarColor(Color.parseColor("#1F4E79"));

        pendingQuery = textFromIntent(getIntent());

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Dict");
        setContentView(web);
        web.loadUrl("file:///android_asset/www/index.html");

        new Thread(new Runnable() {
            public void run() { prepare(); }
        }).start();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String q = textFromIntent(intent);
        if (q != null && web != null) {
            final String js = "window.externalSearch&&externalSearch(" + JSONObject.quote(q) + ")";
            web.evaluateJavascript(js, null);
        }
    }

    private static String textFromIntent(Intent intent) {
        if (intent == null) return null;
        CharSequence t = null;
        if (Build.VERSION.SDK_INT >= 23 && Intent.ACTION_PROCESS_TEXT.equals(intent.getAction())) {
            t = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        }
        if (t == null) return null;
        String q = t.toString().trim();
        if (q.length() > 60) q = q.substring(0, 60);
        return q.isEmpty() ? null : q;
    }

    private long expectedSize() {
        try {
            InputStream in = getAssets().open("dict.size");
            BufferedReader r = new BufferedReader(new InputStreamReader(in));
            String line = r.readLine();
            r.close();
            return Long.parseLong(line.trim());
        } catch (Exception e) {
            return 90L * 1024 * 1024;
        }
    }

    /** Copy the bundled database out of the APK once per installed version, then open it. */
    private void prepare() {
        try {
            File f = getDatabasePath("dict.db");
            SharedPreferences prefs = getSharedPreferences("dict", MODE_PRIVATE);
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            String stamp = pi.lastUpdateTime + "/" + pi.versionName;
            if (!f.exists() || !stamp.equals(prefs.getString("stamp", ""))) {
                state = "copy";
                File dir = f.getParentFile();
                if (dir != null) dir.mkdirs();
                File tmp = new File(f.getPath() + ".tmp");
                long total = expectedSize(), done = 0;
                InputStream in = getAssets().open("dict.db");
                OutputStream out = new FileOutputStream(tmp);
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    progress = (int) Math.min(99, done * 100 / Math.max(1, total));
                }
                out.close();
                in.close();
                if (f.exists()) f.delete();
                if (!tmp.renameTo(f)) throw new RuntimeException("rename failed");
                prefs.edit().putString("stamp", stamp).apply();
            }
            dict = new DictDb(f.getPath());
            progress = 100;
            state = "ready";
        } catch (Throwable t) {
            error = String.valueOf(t);
            state = "error";
        }
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.appBack?appBack():false", new ValueCallback<String>() {
            public void onReceiveValue(String v) {
                if (!"true".equals(v)) MainActivity.super.onBackPressed();
            }
        });
    }

    class Bridge {
        @JavascriptInterface
        public String status() {
            try {
                JSONObject o = new JSONObject();
                o.put("state", state);
                o.put("progress", progress);
                o.put("error", error);
                String q = pendingQuery;
                if (q != null && "ready".equals(state)) {
                    o.put("query", q);
                    pendingQuery = null;
                }
                if (dict != null) o.put("version", dict.meta("version"));
                return o.toString();
            } catch (Exception e) {
                return "{\"state\":\"error\",\"error\":\"" + e + "\"}";
            }
        }

        @JavascriptInterface
        public String dicts() {
            try { return dict == null ? "[]" : dict.dicts(); } catch (Exception e) { return "[]"; }
        }

        @JavascriptInterface
        public String search(String q, String dicts) {
            try { return dict == null ? "[]" : dict.search(q, dicts, 60); } catch (Exception e) { return "[]"; }
        }

        @JavascriptInterface
        public String detail(String k, String hw, String dicts) {
            try { return dict == null ? "[]" : dict.detail(k, hw, dicts); } catch (Exception e) { return "[]"; }
        }
    }
}
