package com.atlasai.app;

import com.getcapacitor.Plugin;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Sayfa yüklenmeden önce window.AtlasNative köprüsünü WebView'e ekler.
 * (addJavascriptInterface yalnızca bir sonraki yüklemede görünür; eklentiler
 * Capacitor tarafından ilk yüklemeden önce başlatıldığı için burası doğru yer.)
 */
@CapacitorPlugin(name = "AtlasFocusShield")
public class FocusShieldPlugin extends Plugin {

    private FocusShield shield;

    @Override
    public void load() {
        shield = new FocusShield(getContext());
        FocusShield.restore(getContext());
        getBridge().getWebView().addJavascriptInterface(shield, "AtlasNative");
    }

    @Override
    protected void handleOnDestroy() {
        if (shield != null) shield.stop();
    }
}
