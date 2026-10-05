package com.rahultewatia.ironlog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thin wrapper around the live PWA. The page (and its service worker) handle offline use,
 * so updates pushed to GitHub Pages reach the app automatically. Saved data lives in this
 * WebView's localStorage under the same "ironlog-v1" key the web app uses.
 */
public class MainActivity extends Activity {
    private static final String APP_URL = "https://rahultewatia-hue.github.io/ironlog/";
    private static final String APP_HOST = "rahultewatia-hue.github.io";
    private static final int REQ_PICK_FILE = 1;
    private static final int REQ_SAVE_FILE = 2;

    private static final String RELEASE_API =
        "https://api.github.com/repos/rahultewatia-hue/ironlog/releases/tags/android-latest";
    private static final long CHECK_EVERY_MS = 6 * 60 * 60 * 1000L;

    private WebView web;
    private ValueCallback<Uri[]> pendingPick;
    private String pendingSave;
    private long downloadId = -1;
    private long pausedAt = 0;
    private Uri downloadedApk;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        web.setBackgroundColor(0xFF0A0D0F);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.addJavascriptInterface(new Bridge(), "AndroidApp");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (APP_HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (ActivityNotFoundException ignored) {}
                return true;
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                // Only reached when offline before the app was ever cached.
                if (req.isForMainFrame()) v.loadDataWithBaseURL(null, OFFLINE_HTML, "text/html", "utf-8", null);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (pendingPick != null) pendingPick.onReceiveValue(null);
                pendingPick = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(i, REQ_PICK_FILE);
                } catch (ActivityNotFoundException e) {
                    pendingPick = null;
                    return false;
                }
                return true;
            }
        });

        if (state != null) web.restoreState(state);
        else web.loadUrl(APP_URL);

        checkForUpdate(false);
    }

    /* ---------- APK self-update from the GitHub "android-latest" release ---------- */

    private long installedVersion() {
        try {
            if (Build.VERSION.SDK_INT >= 28) return getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode();
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    private void checkForUpdate(boolean force) {
        SharedPreferences prefs = getSharedPreferences("updates", MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!force && now - prefs.getLong("lastCheck", 0) < CHECK_EVERY_MS) return;
        prefs.edit().putLong("lastCheck", now).apply();
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(RELEASE_API).openConnection();
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                if (c.getResponseCode() != 200) return;
                InputStream in = c.getInputStream();
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                for (int n; (n = in.read(b)) > 0; ) buf.write(b, 0, n);
                JSONObject rel = new JSONObject(buf.toString("UTF-8"));
                Matcher m = Pattern.compile("versionCode=(\\d+)").matcher(rel.optString("body"));
                if (!m.find()) return;
                long remote = Long.parseLong(m.group(1));
                String apkUrl = null;
                JSONArray assets = rel.optJSONArray("assets");
                for (int i = 0; assets != null && i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    if (a.optString("name").endsWith(".apk")) apkUrl = a.optString("browser_download_url");
                }
                if (remote > installedVersion() && apkUrl != null) {
                    final String url = apkUrl;
                    runOnUiThread(() -> offerUpdate(url));
                }
            } catch (Exception ignored) {
                // Offline or rate-limited: try again next launch.
            }
        }).start();
    }

    private void offerUpdate(String url) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
            .setTitle("Update available")
            .setMessage("A new version of Aesthetic Body is ready. Your workouts and settings are kept.")
            .setPositiveButton("Update", (d, w) -> startDownload(url))
            .setNegativeButton("Later", null)
            .show();
    }

    private void startDownload(String url) {
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url))
            .setTitle("Aesthetic Body update")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "aesthetic-body-update.apk");
        new java.io.File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "aesthetic-body-update.apk").delete();
        downloadId = dm.enqueue(r);
        Toast.makeText(this, "Downloading update…", Toast.LENGTH_SHORT).show();
    }

    private final BroadcastReceiver downloadDone = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id != downloadId) return;
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            downloadedApk = dm.getUriForDownloadedFile(id);
            if (downloadedApk == null) {
                Toast.makeText(MainActivity.this, "Update download failed", Toast.LENGTH_LONG).show();
                return;
            }
            installDownloaded();
        }
    };

    private void installDownloaded() {
        if (downloadedApk == null) return;
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            // One-time permission: "Allow from this source". We retry in onResume.
            Toast.makeText(this, "Allow Aesthetic Body to install updates, then come back", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(downloadedApk, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            downloadedApk = null;
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Could not open the installer", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(downloadDone, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(downloadDone, f);
    }

    @Override
    protected void onStop() {
        super.onStop();
        try { unregisterReceiver(downloadDone); } catch (IllegalArgumentException ignored) {}
    }

    /** Called from index.html's exportData() so backups can be saved from inside the app. */
    private class Bridge {
        /** Private copy of the app data (written on every save) so it survives WebView storage being cleared. */
        @JavascriptInterface
        public void saveData(String json) {
            try {
                java.io.File dir = getFilesDir(), tmp = new java.io.File(dir, "data.json.tmp"), dst = new java.io.File(dir, "data.json");
                try (OutputStream out = new java.io.FileOutputStream(tmp)) { out.write(json.getBytes(StandardCharsets.UTF_8)); }
                if (!tmp.renameTo(dst)) { dst.delete(); tmp.renameTo(dst); }
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public String loadData() {
            java.io.File f = new java.io.File(getFilesDir(), "data.json");
            if (!f.exists()) return "";
            try (InputStream in = new java.io.FileInputStream(f)) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                for (int n; (n = in.read(b)) > 0; ) buf.write(b, 0, n);
                return buf.toString("UTF-8");
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void saveBackup(String name, String json) {
            runOnUiThread(() -> {
                pendingSave = json;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, name);
                try {
                    startActivityForResult(i, REQ_SAVE_FILE);
                } catch (ActivityNotFoundException e) {
                    pendingSave = null;
                    Toast.makeText(MainActivity.this, "No file app available to save the backup", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        Uri uri = (res == RESULT_OK && data != null) ? data.getData() : null;
        if (req == REQ_PICK_FILE && pendingPick != null) {
            pendingPick.onReceiveValue(uri == null ? null : new Uri[]{uri});
            pendingPick = null;
        } else if (req == REQ_SAVE_FILE && pendingSave != null) {
            if (uri != null) {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    out.write(pendingSave.getBytes(StandardCharsets.UTF_8));
                    Toast.makeText(this, "Backup saved", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Could not save the backup", Toast.LENGTH_LONG).show();
                }
            }
            pendingSave = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
        pausedAt = System.currentTimeMillis();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        // Left in the background for over an hour: reload so the latest version of the app shows.
        // Sets are saved as they're typed, so nothing is lost.
        if (pausedAt > 0 && System.currentTimeMillis() - pausedAt > 60 * 60 * 1000L) web.reload();
        // Back from the "install unknown apps" screen with a downloaded update waiting
        if (downloadedApk != null && (Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls())) installDownloaded();
        else checkForUpdate(false);
    }

    private static final String OFFLINE_HTML =
        "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
        + "<body style='margin:0;background:#0a0d0f;color:#f3f6f4;font-family:sans-serif;display:flex;"
        + "align-items:center;justify-content:center;height:100vh;text-align:center;padding:24px;box-sizing:border-box'>"
        + "<div><h2>Connect to the internet once</h2>"
        + "<p style='color:#8a949b'>Aesthetic Body needs to download itself the first time. After that it works offline.</p>"
        + "<button onclick=\"location.href='" + APP_URL + "'\" style='background:#c6f432;color:#0b0f05;border:0;"
        + "border-radius:14px;padding:13px 22px;font-weight:800;font-size:16px'>Try again</button></div></body></html>";
}
