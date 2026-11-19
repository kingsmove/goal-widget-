package com.jongjun.mdd;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.fingerprint.FingerprintManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START = "https://" + HOST + "/index.html";
    private static final int REQ_FILES = 11, REQ_NOTIF = 12;

    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private String pendingEntry;
    private boolean pageReady = false, started = false;

    private static final Map<String, String> MIME = new HashMap<>();
    static {
        MIME.put("html", "text/html");
        MIME.put("js", "application/javascript");
        MIME.put("css", "text/css");
        MIME.put("json", "application/json");
        MIME.put("otf", "font/otf");
        MIME.put("png", "image/png");
        MIME.put("svg", "image/svg+xml");
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        boolean night = isNightMode();
        web.setBackgroundColor(Color.parseColor(night ? "#101215" : "#F2F4F6"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        if (Build.VERSION.SDK_INT >= 29) s.setForceDark(WebSettings.FORCE_DARK_OFF);

        web.addJavascriptInterface(new Bridge(), "Android");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (!HOST.equals(u.getHost())) return null;
                String path = u.getPath();
                if (path == null || path.equals("/")) path = "/index.html";
                path = path.substring(1);
                try {
                    InputStream in = getAssets().open(path);
                    String ext = path.substring(path.lastIndexOf('.') + 1).toLowerCase();
                    String mime = MIME.containsKey(ext) ? MIME.get(ext) : "application/octet-stream";
                    WebResourceResponse r = new WebResourceResponse(mime, "UTF-8", in);
                    Map<String, String> h = new HashMap<>();
                    h.put("Cache-Control", "no-cache");
                    r.setResponseHeaders(h);
                    return r;
                } catch (Exception e) {
                    return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", new HashMap<>(), new java.io.ByteArrayInputStream(new byte[0]));
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                return !HOST.equals(req.getUrl().getHost());
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                pageReady = true;
                flushPendingEntry();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                if (p.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(Intent.createChooser(i, "사진 선택"), REQ_FILES);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });

        readEntryIntent(getIntent());
        web.loadUrl(START);
    }

    private boolean isNightMode() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private void js(String code) {
        if (web != null) web.evaluateJavascript(code, null);
    }

    private void readEntryIntent(Intent i) {
        if (i == null) return;
        String id = i.getStringExtra("entryId");
        if (id != null) {
            pendingEntry = id;
            i.removeExtra("entryId");
        }
    }

    private void flushPendingEntry() {
        if (pageReady && pendingEntry != null) {
            js("window.openFromNotif && openFromNotif(" + JSONObject.quote(pendingEntry) + ")");
            pendingEntry = null;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readEntryIntent(intent);
        flushPendingEntry();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (started) js("window.__native && __native('show')");
        started = true;
    }

    @Override
    protected void onStop() {
        js("window.__native && __native('hide')");
        super.onStop();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        js("window.__nightChanged && __nightChanged()");
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == REQ_NOTIF) {
            boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
            js("window.onNotifPerm && onNotifPerm(" + ok + ")");
        }
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != REQ_FILES || fileCb == null) return;
        Uri[] out = null;
        if (result == RESULT_OK && data != null) {
            List<Uri> list = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int k = 0; k < clip.getItemCount(); k++) list.add(clip.getItemAt(k).getUri());
            } else if (data.getData() != null) {
                list.add(data.getData());
            }
            out = list.toArray(new Uri[0]);
        }
        fileCb.onReceiveValue(out);
        fileCb = null;
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.onBack && window.onBack()) ? 'y' : 'n'", v -> {
            if (v == null || !v.contains("y")) MainActivity.super.onBackPressed();
        });
    }

    private boolean canBio() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                BiometricManager bm = getSystemService(BiometricManager.class);
                return bm != null && bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;
            } else if (Build.VERSION.SDK_INT == 29) {
                BiometricManager bm = getSystemService(BiometricManager.class);
                return bm != null && bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS;
            } else if (Build.VERSION.SDK_INT == 28) {
                FingerprintManager fm = getSystemService(FingerprintManager.class);
                return fm != null && fm.isHardwareDetected() && fm.hasEnrolledFingerprints();
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void bioResult(boolean ok) {
        js("window.onBio && onBio(" + ok + ")");
    }

    private void startBio() {
        if (Build.VERSION.SDK_INT < 28 || !canBio()) {
            bioResult(false);
            return;
        }
        try {
            BiometricPrompt.Builder bld = new BiometricPrompt.Builder(this)
                    .setTitle("MDD 잠금 해제")
                    .setSubtitle("지문 등으로 잠금을 풀어요")
                    .setNegativeButton("PIN 입력", getMainExecutor(), (d, w) -> bioResult(false));
            if (Build.VERSION.SDK_INT >= 29) bld.setConfirmationRequired(false);
            bld.build().authenticate(new CancellationSignal(), getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                    bioResult(true);
                }

                @Override
                public void onAuthenticationError(int code, CharSequence msg) {
                    bioResult(false);
                }
            });
        } catch (Exception e) {
            bioResult(false);
        }
    }

    private class Bridge {
        @JavascriptInterface
        public boolean isNight() {
            return isNightMode();
        }

        @JavascriptInterface
        public boolean bioAvailable() {
            return canBio();
        }

        @JavascriptInterface
        public void bioAuth() {
            runOnUiThread(MainActivity.this::startBio);
        }

        @JavascriptInterface
        public void requestNotif() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
                } else {
                    boolean ok = getSystemService(NotificationManager.class).areNotificationsEnabled();
                    js("window.onNotifPerm && onNotifPerm(" + ok + ")");
                }
            });
        }

        @JavascriptInterface
        public void setReminders(String json) {
            try {
                ReminderReceiver.replace(getApplicationContext(), json);
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void setBars(String color, boolean light) {
            runOnUiThread(() -> {
                try {
                    int c = Color.parseColor(color);
                    Window w = getWindow();
                    w.setStatusBarColor(c);
                    w.setNavigationBarColor(c);
                    web.setBackgroundColor(c);
                    View d = w.getDecorView();
                    int f = d.getSystemUiVisibility();
                    if (light) f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    else f &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
                    d.setSystemUiVisibility(f);
                } catch (Exception ignored) {
                }
            });
        }
    }
}
