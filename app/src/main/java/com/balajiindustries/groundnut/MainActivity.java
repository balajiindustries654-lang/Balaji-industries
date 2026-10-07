package com.balajiindustries.app;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final String APP_URL =
            "https://balajiindustries654-lang.github.io/Balaji-industries/";
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private SharedPreferences pushPreferences;
    private boolean tokenRequestRunning;

    private final SharedPreferences.OnSharedPreferenceChangeListener tokenListener =
            (preferences, key) -> {
                if ("native_fcm_token".equals(key)) {
                    runOnUiThread(this::publishNativeToken);
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        pushPreferences = getSharedPreferences("balaji_push", MODE_PRIVATE);
        pushPreferences.registerOnSharedPreferenceChangeListener(tokenListener);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(new NotificationChannel(
                    "balaji_notifications", "Balaji Industries Notifications",
                    NotificationManager.IMPORTANCE_HIGH));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2001);
        }

        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (isAppPage(url)) publishNativeToken();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                if (isAppPage(request.getUrl().toString())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl()));
                } catch (Exception ignored) { }
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view,
                    ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
            }
        });

        refreshNativeToken();
        webView.loadUrl(APP_URL);
    }

    private boolean isAppPage(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        return "https".equals(uri.getScheme()) &&
                "balajiindustries654-lang.github.io".equals(uri.getHost()) &&
                uri.getPort() == -1 && uri.getPath() != null &&
                uri.getPath().startsWith("/Balaji-industries/");
    }

    private void refreshNativeToken() {
        if (tokenRequestRunning) return;
        tokenRequestRunning = true;
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(this, task -> {
            tokenRequestRunning = false;
            if (task.isSuccessful() && task.getResult() != null) {
                pushPreferences.edit().putString("native_fcm_token", task.getResult()).apply();
                publishNativeToken();
            }
        });
    }

    private void publishNativeToken() {
        if (webView == null || !isAppPage(webView.getUrl())) return;
        String token = pushPreferences.getString("native_fcm_token", "");
        boolean allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED;
        String script = "if(location.origin==='https://balajiindustries654-lang.github.io'" +
                " && location.pathname.startsWith('/Balaji-industries/')){" +
                "window.BalajiNativeApp=true;" +
                "window.BalajiNativeFcmToken=" + JSONObject.quote(token) + ";" +
                "window.BalajiNativeNotificationsAllowed=" + allowed + ";" +
                "window.dispatchEvent(new Event('balaji-native-token'));}";
        webView.evaluateJavascript(script, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            publishNativeToken();
            refreshNativeToken();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2001) publishNativeToken();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            filePathCallback.onReceiveValue(
                    WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            filePathCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        pushPreferences.unregisterOnSharedPreferenceChangeListener(tokenListener);
        if (filePathCallback != null) filePathCallback.onReceiveValue(null);
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
