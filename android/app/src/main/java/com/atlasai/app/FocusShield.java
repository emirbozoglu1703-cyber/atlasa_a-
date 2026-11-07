package com.atlasai.app;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

/**
 * Odak modu sırasında telefonun "Rahatsız Etmeyin" modunu açar (yalnızca alarmlar çalar),
 * seans bitince önceki ayara döner. Web sayfası window.AtlasNative üzerinden çağırır.
 *
 * Güvenlik ağı: süre dolunca AlarmManager ile kendiliğinden kapanır; uygulama çökse bile
 * bir sonraki açılışta önceki ayar geri yüklenir.
 */
public class FocusShield {

    private static final String PREFS = "atlas_focus_shield";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_PREVIOUS = "previous_filter";
    private static final int ALARM_REQUEST = 7301;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoStop = () -> restore(FocusShield.this.context);

    public FocusShield(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Bu cihazda Rahatsız Etmeyin kontrolü mümkün mü (Android 6+). */
    @JavascriptInterface
    public boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    /** Kullanıcı "Rahatsız Etmeyin erişimi" iznini verdi mi. */
    @JavascriptInterface
    public boolean hasAccess() {
        if (!isSupported()) return false;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }

    /** İzin ekranını açar; kullanıcı ATLAS AI'ı listeden açmalı. */
    @JavascriptInterface
    public void openAccessSettings() {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @JavascriptInterface
    public boolean isActive() {
        return prefs(context).getBoolean(KEY_ACTIVE, false);
    }

    /** Bildirimleri engeller; en geç {@code minutes} dakika sonra kendiliğinden kapanır. */
    @JavascriptInterface
    public boolean start(int minutes) {
        if (!hasAccess()) return false;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        SharedPreferences p = prefs(context);
        if (!p.getBoolean(KEY_ACTIVE, false)) {
            p.edit()
                .putInt(KEY_PREVIOUS, nm.getCurrentInterruptionFilter())
                .putBoolean(KEY_ACTIVE, true)
                .apply();
        }
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS);

        long delayMs = Math.max(1, Math.min(minutes, 24 * 60)) * 60_000L + 30_000L;
        handler.removeCallbacks(autoStop);
        handler.postDelayed(autoStop, delayMs);
        scheduleAlarm(delayMs);
        return true;
    }

    /** Bildirimleri önceki hâline döndürür. */
    @JavascriptInterface
    public void stop() {
        handler.removeCallbacks(autoStop);
        cancelAlarm();
        restore(context);
    }

    /** Odak modu bizim açtığımız Rahatsız Etmeyin'i kapatır; başka bir şeye dokunmaz. */
    static void restore(Context context) {
        SharedPreferences p = prefs(context);
        if (!p.getBoolean(KEY_ACTIVE, false)) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null && nm.isNotificationPolicyAccessGranted()) {
                int previous = p.getInt(KEY_PREVIOUS, NotificationManager.INTERRUPTION_FILTER_ALL);
                if (previous == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
                    previous = NotificationManager.INTERRUPTION_FILTER_ALL;
                }
                nm.setInterruptionFilter(previous);
            }
        }
        p.edit().putBoolean(KEY_ACTIVE, false).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private PendingIntent alarmIntent() {
        Intent intent = new Intent(context, RestoreReceiver.class);
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void scheduleAlarm(long delayMs) {
        AlarmManager am = context.getSystemService(AlarmManager.class);
        if (am == null) return;
        long at = SystemClock.elapsedRealtime() + delayMs;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, alarmIntent());
        } else {
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, alarmIntent());
        }
    }

    private void cancelAlarm() {
        AlarmManager am = context.getSystemService(AlarmManager.class);
        if (am != null) am.cancel(alarmIntent());
    }

    /** Süre dolduğunda (uygulama arka planda ya da kapalıyken de) bildirimleri geri açar. */
    public static class RestoreReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            restore(context);
        }
    }
}
