package kr.sbkwon.decolock;

import android.app.Notification;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Feeds Edge Lighting, notification history (NotiStar / Nice Catch) and the lock screen. */
public class NotiListener extends NotificationListenerService {
    public static volatile NotiListener instance;
    public static volatile Runnable onChange;
    private final Map<String, Long> lastSeen = new HashMap<>();

    @Override public void onListenerConnected() { instance = this; }
    @Override public void onListenerDisconnected() { instance = null; }
    @Override public void onDestroy() { instance = null; super.onDestroy(); }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (sbn.getPackageName().equals(getPackageName())) return;
        if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) return;
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        if ((n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) return;

        Bundle ex = n.extras;
        CharSequence title = ex.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (text == null) text = ex.getCharSequence(Notification.EXTRA_TEXT);
        String t = title == null ? "" : title.toString();
        String b = text == null ? "" : text.toString();
        if (t.isEmpty() && b.isEmpty()) return;

        // ignore silent re-posts of the same content (progress updates etc.)
        String sig = sbn.getKey() + "|" + t + "|" + b;
        long now = System.currentTimeMillis();
        Long prev = lastSeen.get(sig);
        lastSeen.put(sig, now);
        if (lastSeen.size() > 300) lastSeen.clear();
        boolean repeat = prev != null && now - prev < 60_000;

        String label = sbn.getPackageName();
        Drawable icon = null;
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(sbn.getPackageName(), 0);
            label = pm.getApplicationLabel(ai).toString();
            icon = pm.getApplicationIcon(ai);
        } catch (PackageManager.NameNotFoundException ignored) {}

        if (!repeat) {
            HistoryDb.get(this).add(sbn.getPackageName(), label, t, b, sbn.getPostTime());
            if ((n.flags & Notification.FLAG_ONLY_ALERT_ONCE) == 0 || prev == null) {
                EdgeLighting.show(this, icon);
            }
        }
        Runnable r = onChange;
        if (r != null) r.run();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Runnable r = onChange;
        if (r != null) r.run();
    }

    /** Distinct apps with visible notifications right now (for the lock screen). */
    public static List<String> activePackages() {
        List<String> out = new ArrayList<>();
        NotiListener l = instance;
        if (l == null) return out;
        try {
            for (StatusBarNotification s : l.getActiveNotifications()) {
                if ((s.getNotification().flags & Notification.FLAG_ONGOING_EVENT) != 0) continue;
                if (!out.contains(s.getPackageName())) out.add(s.getPackageName());
            }
        } catch (RuntimeException ignored) {}
        return out;
    }
}
