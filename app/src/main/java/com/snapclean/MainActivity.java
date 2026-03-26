package com.snapclean;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SnapClean - a WebView wrapper for web.snapchat.com that blocks
 * Discover, Spotlight, and other addictive content feeds.
 */
public class MainActivity extends Activity {

    private static final String LOGIN_URL = "https://accounts.snapchat.com/v2/login?continue=https%3A%2F%2Fwww.snapchat.com%2Fweb%2F";
    private static final String WEB_APP_URL = "https://www.snapchat.com/web/";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int FILE_CHOOSER_REQUEST = 1002;
    private ValueCallback<Uri[]> fileUploadCallback;

    /**
     * Domains allowed for navigation. Subresource loads (CSS, JS, images)
     * are not affected - only top-level navigations (link clicks, redirects).
     */
    private static final List<String> ALLOWED_DOMAINS = Arrays.asList(
        "snapchat.com",
        "snap.com",
        "sc-cdn.net",
        "sc-static.net",
        "accounts.snapchat.com"
    );

    /**
     * Blocked path segments - even within allowed domains.
     */
    private static final List<String> BLOCKED_PATHS = Arrays.asList(
        "/discover",
        "/spotlight",
        "/stories/discover",
        "/explore",
        "/subscriptions",
        "/snap-star",
        "/story/",
        "/stories",
        "/map",
        "/lens",
        "/plus"
    );

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        // Enable remote debugging via chrome://inspect on desktop Chrome
        WebView.setWebContentsDebuggingEnabled(true);

        requestAppPermissions();
        configureWebView();

        // Enable cookies (needed for login session)
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // Try the web app directly; if not logged in the WebViewClient
        // will detect the redirect to the homepage and send to login instead.
        webView.loadUrl(WEB_APP_URL);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Desktop Chrome UA - Snapchat web requires a desktop browser
        String desktopUA = "Mozilla/5.0 (X11; Linux x86_64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/122.0.0.0 Safari/537.36";
        settings.setUserAgentString(desktopUA);

        webView.setWebViewClient(new SnapCleanWebViewClient());
        webView.setWebChromeClient(new SnapCleanChromeClient());
    }

    private boolean isDomainAllowed(String url) {
        if (url == null) return false;
        String lowerUrl = url.toLowerCase();
        for (String domain : ALLOWED_DOMAINS) {
            if (lowerUrl.contains(domain)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPathBlocked(String url) {
        if (url == null) return false;
        String lowerUrl = url.toLowerCase();
        for (String blocked : BLOCKED_PATHS) {
            if (lowerUrl.contains(blocked)) {
                return true;
            }
        }
        return false;
    }

    /**
     * JS to inject before page scripts run, making this WebView look
     * like real desktop Chrome to Snapchat's detection.
     */
    private static final String CHROME_SPOOF_JS =
        "if (!window.chrome) {"
        + "  window.chrome = {"
        + "    app: { isInstalled: false, getIsInstalled: function(){return false;}, getDetails: function(){}, installState: function(){} },"
        + "    runtime: { connect: function(){}, sendMessage: function(){} },"
        + "    csi: function(){},"
        + "    loadTimes: function(){}"
        + "  };"
        + "}"
        + "Object.defineProperty(navigator, 'plugins', {"
        + "  get: function() { return [1,2,3]; }"
        + "});"
        // Override permissions.query to report camera/mic as granted
        + "const origQuery = navigator.permissions.query.bind(navigator.permissions);"
        + "navigator.permissions.query = function(desc) {"
        + "  if (desc.name === 'camera' || desc.name === 'microphone') {"
        + "    return Promise.resolve({state: 'granted', onchange: null});"
        + "  }"
        + "  return origQuery(desc);"
        + "};";

    private class SnapCleanWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();

            // If Snapchat's JS redirects to the homepage (not logged in),
            // intercept and go to login instead.
            if (url.startsWith("https://www.snapchat.com/?") || url.equals("https://www.snapchat.com/")) {
                view.loadUrl(LOGIN_URL);
                return true;
            }
            // After login, if redirected to download page, go to web app
            if (url.contains("snapchat.com/download")) {
                view.loadUrl(WEB_APP_URL);
                return true;
            }
            // Block paths we don't want (discover, stories, spotlight, etc.)
            if (isPathBlocked(url)) {
                return true;
            }
            // Block any external domain (YouTube, TikTok, etc. from chat links)
            if (!isDomainAllowed(url)) {
                return true;
            }
            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            view.evaluateJavascript(CHROME_SPOOF_JS, null);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            injectContentBlocker(view);
        }
    }

    /**
     * Hide known Discover/Spotlight UI elements via CSS injection.
     * This is brittle (Snapchat can change class names) but provides
     * an extra layer of defense.
     */
    private void injectContentBlocker(WebView view) {
        String css = String.join("",
            // Contain everything within viewport
            "html, body { overflow-x: hidden !important; max-width: 100vw !important; }",
            "div[role=dialog], div[role=menu], div[role=listbox], ",
            "div[role=presentation], div[style*=position] ",
            "{ max-width: 100vw !important; right: auto !important; ",
            "  overflow-x: auto !important; }",
            // Content blocking is handled by URL/domain filtering in
            // shouldOverrideUrlLoading - no CSS hiding needed
            ""
        );

        String js = "javascript:(function() {"
            + "var style = document.getElementById('snapclean-blocker');"
            + "if (!style) {"
            + "  style = document.createElement('style');"
            + "  style.id = 'snapclean-blocker';"
            + "  style.textContent = '" + css.replace("'", "\\'") + "';"
            + "  document.head.appendChild(style);"
            + "}"
            + "})()";
        view.evaluateJavascript(js, null);
    }

    private class SnapCleanChromeClient extends WebChromeClient {
        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            // Must grant on UI thread
            runOnUiThread(() -> request.grant(request.getResources()));
        }

        @Override
        public boolean onShowFileChooser(WebView view,
                ValueCallback<Uri[]> filePathCallback,
                FileChooserParams fileChooserParams) {
            if (fileUploadCallback != null) {
                fileUploadCallback.onReceiveValue(null);
            }
            fileUploadCallback = filePathCallback;
            startActivityForResult(fileChooserParams.createIntent(), FILE_CHOOSER_REQUEST);
            return true;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (fileUploadCallback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK && data != null) {
                    String dataString = data.getDataString();
                    if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                }
                fileUploadCallback.onReceiveValue(results);
                fileUploadCallback = null;
            }
        }
    }

    private void requestAppPermissions() {
        List<String> needed = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA);
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO);
        }
        if (!needed.isEmpty()) {
            requestPermissions(needed.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Back button goes back in WebView history instead of closing app
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }
}
