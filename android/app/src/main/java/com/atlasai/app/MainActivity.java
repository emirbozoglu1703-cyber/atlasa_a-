package com.atlasai.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.widget.Toast;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

public class MainActivity extends BridgeActivity {

    private static final String SITE = "https://intuitive-web-tools.lovable.app";
    private static final String CALLBACK_SCHEME = "com.atlasai.atlasai";
    private static final String CALLBACK_HOST = "login-callback";

    // Lovable'ın yayındaki sitelere eklediği "Edit with Lovable" rozetini gizler.
    private static final String HIDE_LOVABLE_BADGE_JS =
        "(function(){" +
        "  if (window.__atlasBadgeHidden) return; window.__atlasBadgeHidden = true;" +
        "  var css = '#lovable-badge,[id^=\"lovable-badge\"]{display:none!important;visibility:hidden!important;}';" +
        "  function addStyle(){ if (!document.head || document.getElementById('atlas-hide-badge')) return;" +
        "    var s = document.createElement('style'); s.id = 'atlas-hide-badge'; s.textContent = css; document.head.appendChild(s); }" +
        "  function sweep(){ addStyle();" +
        "    var els = document.querySelectorAll('a,div,button');" +
        "    for (var i = 0; i < els.length; i++) { var e = els[i];" +
        "      if (e.children.length < 4 && /(Edit|Edited) with\\s*Lovable/i.test(e.textContent || '')) {" +
        "        var st = getComputedStyle(e); if (st.position === 'fixed' || (e.parentElement && getComputedStyle(e.parentElement).position === 'fixed')) {" +
        "          e.style.setProperty('display','none','important'); } } } }" +
        "  sweep();" +
        "  new MutationObserver(sweep).observe(document.documentElement, {childList: true, subtree: true});" +
        "})();";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Odak modu köprüsü (window.AtlasNative) ilk sayfa yüklenmeden önce eklensin.
        registerPlugin(FocusShieldPlugin.class);
        super.onCreate(savedInstanceState);
        bridge.setWebViewClient(new BridgeWebViewClient(bridge) {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (isGoogleLogin(request.getUrl())) {
                    openGoogleLoginInBrowser();
                    return true;
                }
                return super.shouldOverrideUrlLoading(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(HIDE_LOVABLE_BADGE_JS, null);
            }
        });
        handleLoginCallback(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleLoginCallback(intent);
    }

    // Google, uygulama içi tarayıcıda (WebView) girişe izin vermez; giriş gerçek tarayıcıda yapılır.
    private static boolean isGoogleLogin(Uri uri) {
        String host = uri.getHost();
        if (host == null) return false;
        return host.equals("oauth.lovable.app") || host.equals("accounts.google.com");
    }

    private void openGoogleLoginInBrowser() {
        try {
            Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(SITE + "/mobil-giris"));
            browser.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(browser);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Google girişi için bir tarayıcı bulunamadı.", Toast.LENGTH_LONG).show();
        }
    }

    // Tarayıcıdaki /mobil-giris sayfası oturumu com.atlasai.atlasai://login-callback#access_token=… ile geri yollar.
    private void handleLoginCallback(Intent intent) {
        if (intent == null || bridge == null) return;
        Uri data = intent.getData();
        if (data == null) return;
        if (!CALLBACK_SCHEME.equals(data.getScheme()) || !CALLBACK_HOST.equals(data.getHost())) return;

        String fragment = data.getEncodedFragment();
        if (fragment == null || !fragment.contains("access_token")) {
            String description = data.getQueryParameter("error_description");
            fragment = "error_description=" + Uri.encode(description != null ? description : "Google girişi tamamlanamadı.");
        }
        intent.setData(null);
        final String target = SITE + "/mobil-oturum#" + fragment;
        bridge.getWebView().post(() -> bridge.getWebView().loadUrl(target));
    }
}
