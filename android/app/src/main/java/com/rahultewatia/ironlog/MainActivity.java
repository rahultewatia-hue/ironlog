package com.rahultewatia.ironlog;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

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

    private WebView web;
    private ValueCallback<Uri[]> pendingPick;
    private String pendingSave;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        web.setBackgroundColor(0xFF1C2129);
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
    }

    /** Called from index.html's exportData() so backups can be saved from inside the app. */
    private class Bridge {
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    private static final String OFFLINE_HTML =
        "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
        + "<body style='margin:0;background:#1c2129;color:#eef1f5;font-family:sans-serif;display:flex;"
        + "align-items:center;justify-content:center;height:100vh;text-align:center;padding:24px;box-sizing:border-box'>"
        + "<div><h2>Connect to the internet once</h2>"
        + "<p style='color:#8f98a8'>Aesthetic Body needs to download itself the first time. After that it works offline.</p>"
        + "<button onclick=\"location.href='" + APP_URL + "'\" style='background:#f2c230;color:#1c2129;border:0;"
        + "border-radius:14px;padding:13px 22px;font-weight:800;font-size:16px'>Try again</button></div></body></html>";
}
