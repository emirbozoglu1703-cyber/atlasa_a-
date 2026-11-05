package com.atlasai.app;

import android.os.Bundle;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

public class MainActivity extends BridgeActivity {

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
        super.onCreate(savedInstanceState);
        bridge.setWebViewClient(new BridgeWebViewClient(bridge) {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(HIDE_LOVABLE_BADGE_JS, null);
            }
        });
    }
}
