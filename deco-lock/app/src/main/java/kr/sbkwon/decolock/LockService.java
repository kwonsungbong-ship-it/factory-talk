package kr.sbkwon.decolock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;

/** Keeps the custom lock screen (LockStar) ready: shows it each time the screen turns off. */
public class LockService extends Service {
    private static final String CH = "lock";

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (!Intent.ACTION_SCREEN_OFF.equals(i.getAction())) return;
            if (!Prefs.b(c, Prefs.LOCK_ON, false)) return;
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            int mode = am.getMode();
            if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_RINGTONE) return;
            Intent li = new Intent(c, LockActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try { startActivity(li); } catch (RuntimeException ignored) {}
        }
    };

    public static void sync(Context c) {
        Intent i = new Intent(c, LockService.class);
        if (Prefs.b(c, Prefs.LOCK_ON, false)) {
            try { c.startForegroundService(i); } catch (RuntimeException ignored) {}
        } else {
            c.stopService(i);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CH, "잠금 화면 유지", NotificationManager.IMPORTANCE_MIN));
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, SettingsActivity.class).putExtra(SettingsActivity.EXTRA_MODULE, SettingsActivity.M_LOCK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CH)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("커스텀 잠금 화면 사용 중")
                .setContentText("눌러서 잠금 화면 설정 열기")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(7, n);
        registerReceiver(screen, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Prefs.b(this, Prefs.LOCK_ON, false)) { stopSelf(); return START_NOT_STICKY; }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(screen); } catch (RuntimeException ignored) {}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
