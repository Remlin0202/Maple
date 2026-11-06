package com.fuyou.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 极简 WebView 壳：全屏加载 DSH 的 Web UI。
 *
 * dsh-web 用「?token=xxx 换签名 cookie」鉴权。首次打开没有 cookie 会 401，
 * 这里捕获 401 弹一个输入框让用户粘 token，存进 SharedPreferences 下次直接用。
 */
public class MainActivity extends Activity {

    private static final String[] HOSTS = {
            "http://192.168.1.168:8080",
            "https://nanopct4.taila6c8a.ts.net",
    };
    private static final String PREFS = "fuyou";
    private static final String KEY_TOKEN = "token";

    private WebView web;
    private FrameLayout root;
    private LinearLayout tokenPanel;
    private int hostIndex = 0;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        root = new FrameLayout(this);

        web = new WebView(this);
        web.setLayoutParams(new FrameLayout.LayoutParams(
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

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (isInternal(url)) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame() && error.getErrorCode() == 401) {
                    tokenPanel.setVisibility(View.VISIBLE);
                    tokenPanel.bringToFront();
                }
            }

            @Override
            public void onReceivedError(WebView view, int code, String desc, String failingUrl) {
                // 局域网地址连不上 → 换 Tailscale 地址再试一次
                if (hostIndex + 1 < HOSTS.length && failingUrl != null
                        && failingUrl.startsWith(HOSTS[hostIndex])) {
                    hostIndex++;
                    load();
                }
            }
        });

        root.addView(web);
        buildTokenPanel();
        setContentView(root);
        load();
    }

    private void buildTokenPanel() {
        tokenPanel = new LinearLayout(this);
        tokenPanel.setOrientation(LinearLayout.VERTICAL);
        tokenPanel.setGravity(Gravity.CENTER);
        tokenPanel.setBackgroundColor(Color.parseColor("#0f172a"));
        tokenPanel.setPadding(56, 56, 56, 56);
        tokenPanel.setVisibility(View.GONE);

        TextView title = new TextView(this);
        title.setText("粘贴 token");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        tokenPanel.addView(title);

        TextView hint = new TextView(this);
        hint.setText("在板子上执行：\nsudo journalctl -u dsh-web | grep -o 'token=[A-Za-z0-9_-]*' | tail -1");
        hint.setTextColor(Color.parseColor("#94a3b8"));
        hint.setTextSize(11);
        hint.setPadding(0, 18, 0, 18);
        tokenPanel.addView(hint);

        final EditText input = new EditText(this);
        input.setHint("token");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.parseColor("#64748b"));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setText(prefs().getString(KEY_TOKEN, ""));
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        tokenPanel.addView(input);

        Button go = new Button(this);
        go.setText("进入");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String t = input.getText().toString().trim();
                if (t.startsWith("token=")) t = t.substring(6);
                if (t.isEmpty()) return;
                prefs().edit().putString(KEY_TOKEN, t).apply();
                tokenPanel.setVisibility(View.GONE);
                load();
            }
        });
        tokenPanel.addView(go);

        root.addView(tokenPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void load() {
        String token = prefs().getString(KEY_TOKEN, "");
        String url = HOSTS[hostIndex] + "/" + (token.isEmpty() ? "" : "?token=" + token);
        web.loadUrl(url);
    }

    private boolean isInternal(String url) {
        if (url == null) return false;
        for (String base : HOSTS) {
            String host = Uri.parse(base).getHost();
            if (host != null && url.contains(host)) return true;
        }
        return url.startsWith("about:") || url.startsWith("blob:") || url.startsWith("data:");
    }

    @Override
    public void onBackPressed() {
        if (tokenPanel != null && tokenPanel.getVisibility() == View.VISIBLE) {
            tokenPanel.setVisibility(View.GONE);
            return;
        }
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
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
