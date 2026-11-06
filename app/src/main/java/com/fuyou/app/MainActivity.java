package com.fuyou.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * 一个极简 WebView 壳：全屏加载 DSH 的 Web UI，没有任何浏览器控件。
 *
 * 优先用局域网地址（不用开 Tailscale），连不上再退到 Tailscale 的 HTTPS 地址。
 */
public class MainActivity extends Activity {

    private static final String[] START_URLS = {
            "http://192.168.1.168:8080/",
            "https://nanopct4.taila6c8a.ts.net/",
    };

    private WebView web;
    private int attempt = 0;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        web.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.setBackgroundColor(Color.parseColor("#0f172a"));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (isInternal(url)) {
                    return false; // 站内链接留在 WebView 里
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                // 加载成功，重置回退计数
                attempt = 0;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                // 第一个地址连不上就换下一个（比如不在同一个 Wi-Fi）
                if (attempt + 1 < START_URLS.length && failingUrl != null
                        && failingUrl.startsWith(START_URLS[attempt])) {
                    attempt++;
                    view.loadUrl(START_URLS[attempt]);
                }
            }
        });

        setContentView(web);
        web.loadUrl(START_URLS[attempt]);
    }

    private boolean isInternal(String url) {
        if (url == null) return false;
        for (String base : START_URLS) {
            String host = Uri.parse(base).getHost();
            if (host != null && url.contains(host)) return true;
        }
        return url.startsWith("about:") || url.startsWith("blob:") || url.startsWith("data:");
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
