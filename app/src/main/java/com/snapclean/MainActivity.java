package com.snapclean;

import android.Manifest;
import android.app.Activity;
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
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * SnapClean - a WebView wrapper for web.snapchat.com that blocks
 * Discover, Spotlight, and other addictive content feeds.
 */
public class MainActivity extends Activity {

    private static final String SNAPCHAT_WEB_URL = "https://web.snapchat.com/";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    /**
     * Allowed URL prefixes. Any navigation outside these is blocked.
     * This is a whitelist approach - safer than trying to blacklist every
     * bad URL Snapchat might add in the future.
     */
    private static final List<String> ALLOWED_PREFIXES = Arrays.asList(
        "https://web.snapchat.com",
        "https://accounts.snapchat.com",  // login/auth flow
        "https://auth.snapchat.com",      // SSO auth
        "https://snap.com/auth",          // auth redirects
        "https://cf-st.sc-cdn.net",       // static assets (CSS/JS/fonts)
        "https://bolt-gcdn.sc-cdn.net",   // CDN assets
        "https://s.sc-cdn.net",           // CDN assets
        "https://web-chat.snapchat.com"   // chat websocket/API
    );

    /**
     * Blocked path segments - even within allowed domains, block these paths.
     */
    private static final List<String> BLOCKED_PATHS = Arrays.asList(
        "/discover",
        "/spotlight",
        "/stories/discover",
        "/explore",
        "/subscriptions",
        "/snap-star"
    );

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full-screen, edge-to-edge
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );

        webView = new WebView(this);
        setContentView(webView);

        requestAppPermissions();
        configureWebView();

        // Enable cookies (needed for login session)
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.loadUrl(SNAPCHAT_WEB_URL);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Pretend to be Chrome so Snapchat serves the full web experience
        String chromeUA = "Mozilla/5.0 (Linux; Android 14) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/122.0.0.0 Mobile Safari/537.36";
        settings.setUserAgentString(chromeUA);

        webView.setWebViewClient(new SnapCleanWebViewClient());
        webView.setWebChromeClient(new SnapCleanChromeClient());
    }

    /**
     * URL filter - the heart of the content blocking.
     */
    private boolean isUrlAllowed(String url) {
        if (url == null) return false;

        // Check blocked paths first (these override allowed prefixes)
        String lowerUrl = url.toLowerCase();
        for (String blocked : BLOCKED_PATHS) {
            if (lowerUrl.contains(blocked)) {
                return false;
            }
        }

        // Check against whitelist
        for (String prefix : ALLOWED_PREFIXES) {
            if (lowerUrl.startsWith(prefix)) {
                return true;
            }
        }

        // Allow sc-cdn.net subdomains broadly (media/assets)
        if (lowerUrl.contains(".sc-cdn.net")) {
            return true;
        }

        return false;
    }

    private class SnapCleanWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (isUrlAllowed(url)) {
                return false; // let WebView handle it
            }
            // Blocked - silently ignore
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            // Inject CSS to hide any Discover/Spotlight UI elements that might
            // appear. This is a defense-in-depth layer on top of URL blocking.
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
            // Hide Discover/Spotlight navigation tabs and sections
            "[data-testid*='discover'],",
            "[data-testid*='spotlight'],",
            "[data-testid*='stories-discover'],",
            "[aria-label*='Discover'],",
            "[aria-label*='Spotlight'],",
            "[aria-label*='Subscribe'],",
            "[href*='/discover'],",
            "[href*='/spotlight']",
            "{ display: none !important; }"
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
        public void onPermissionRequest(PermissionRequest request) {
            // Grant camera/mic for video calls
            request.grant(request.getResources());
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
