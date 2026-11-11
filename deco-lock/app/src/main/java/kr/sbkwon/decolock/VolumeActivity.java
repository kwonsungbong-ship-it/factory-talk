package kr.sbkwon.decolock;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** Sound Assistant: one panel with every volume stream and the ringer mode. */
public class VolumeActivity extends Activity {
    private static final int[] STREAMS = {AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING, AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_ALARM, AudioManager.STREAM_VOICE_CALL, AudioManager.STREAM_SYSTEM};
    private static final String[] NAMES = {"🎵 미디어", "📞 벨소리", "🔔 알림", "⏰ 알람", "🗣 통화", "⚙️ 시스템"};

    private AudioManager am;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        int accent = Prefs.accent(this);
        int pad = Ui.dp(this, 20);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackground(Ui.round(0xFFFFFFFF, Ui.dp(this, 28)));

        TextView title = new TextView(this);
        title.setText("볼륨");
        title.setTextSize(20);
        title.setTextColor(Ui.TEXT);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title);

        for (int i = 0; i < STREAMS.length; i++) {
            final int st = STREAMS[i];
            TextView l = new TextView(this);
            l.setText(NAMES[i]);
            l.setTextColor(Ui.SUB);
            l.setPadding(0, Ui.dp(this, 14), 0, 0);
            root.addView(l);
            SeekBar sb = new SeekBar(this);
            sb.setMax(am.getStreamMaxVolume(st));
            sb.setProgress(am.getStreamVolume(st));
            sb.setProgressTintList(ColorStateList.valueOf(accent));
            sb.setThumbTintList(ColorStateList.valueOf(accent));
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int p, boolean user) {
                    if (!user) return;
                    try { am.setStreamVolume(st, p, 0); }
                    catch (SecurityException e) { askDnd(); }
                }
                public void onStartTrackingTouch(SeekBar s) {}
                public void onStopTrackingTouch(SeekBar s) {}
            });
            root.addView(sb);
        }

        LinearLayout modes = new LinearLayout(this);
        modes.setPadding(0, Ui.dp(this, 18), 0, 0);
        String[] ml = {"🔊 소리", "📳 진동", "🔇 무음"};
        int[] mv = {AudioManager.RINGER_MODE_NORMAL, AudioManager.RINGER_MODE_VIBRATE, AudioManager.RINGER_MODE_SILENT};
        for (int i = 0; i < 3; i++) {
            final int mode = mv[i];
            TextView t = new TextView(this);
            t.setText(ml[i]);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
            boolean cur = am.getRingerMode() == mode;
            t.setTextColor(cur ? 0xFFFFFFFF : Ui.TEXT);
            Ui.ripple(t, cur ? accent : 0xFFF0F0F5, Ui.dp(this, 20));
            t.setOnClickListener(v -> {
                try { am.setRingerMode(mode); recreate(); }
                catch (SecurityException e) { askDnd(); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            modes.addView(t, lp);
        }
        root.addView(modes);
        setContentView(root);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 32), Ui.dp(this, 420)), -2);
    }

    private void askDnd() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (!nm.isNotificationPolicyAccessGranted()) {
            Actions.toast(this, "무음/방해금지 조절 권한을 허용해 주세요");
            try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)); } catch (RuntimeException ignored) {}
        }
    }
}
