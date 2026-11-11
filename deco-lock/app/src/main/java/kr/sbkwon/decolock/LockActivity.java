package kr.sbkwon.decolock;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.List;

/** LockStar / ClockFace: decorative screen shown over the system lock screen. */
public class LockActivity extends Activity {
    public static final String[] BGS = {"배경화면 (어둡게)", "테마색 그라데이션", "완전 검정 (AMOLED)", "오로라 그라데이션"};
    public static final String EXTRA_PREVIEW = "preview";

    private FrameLayout root;
    private LinearLayout content;
    private LinearLayout notiRow;
    private TextView battery;
    private float downY;
    private boolean preview;

    private final BroadcastReceiver batt = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            int lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int st = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
            boolean ch = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
            if (lvl >= 0) battery.setText((ch ? "⚡ 충전 중 " : "🔋 ") + (lvl * 100 / sc) + "%");
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        preview = getIntent().getBooleanExtra(EXTRA_PREVIEW, false);
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true);
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        if (Build.VERSION.SDK_INT >= 28) w.getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        build();
    }

    private int dp(float v) { return Ui.dp(this, v); }

    private void build() {
        int accent = Prefs.accent(this);
        int bg = Prefs.i(this, Prefs.LOCK_BG, 0);
        root = new FrameLayout(this);
        switch (bg) {
            case 0: root.setBackgroundColor(0x55000000); break;
            case 1: {
                GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        new int[]{accent, darken(accent, 0.35f), 0xFF0B0B14});
                root.setBackground(g);
                break;
            }
            case 2: root.setBackgroundColor(0xFF000000); break;
            case 3: {
                GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{0xFF0F2027, 0xFF203A43, 0xFF2C5364, 0xFF1D976C});
                root.setBackground(g);
                break;
            }
        }
        if (bg != 0) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(20), dp(90), dp(20), dp(40));
        root.addView(content, Ui.match());

        ClockView clock = new ClockView(this);
        clock.setStyle(Prefs.i(this, Prefs.LOCK_STYLE, 0), Prefs.b(this, Prefs.LOCK_24H, ClockView.is24(this)));
        content.addView(clock, new LinearLayout.LayoutParams(-1, -2));

        TextClock date = new TextClock(this);
        date.setFormat12Hour("M월 d일 EEEE");
        date.setFormat24Hour("M월 d일 EEEE");
        date.setTextColor(0xFFFFFFFF);
        date.setTextSize(18);
        date.setGravity(Gravity.CENTER);
        date.setPadding(0, dp(8), 0, dp(4));
        content.addView(date);

        battery = new TextView(this);
        battery.setTextColor(0xCCFFFFFF);
        battery.setTextSize(14);
        battery.setGravity(Gravity.CENTER);
        content.addView(battery);

        notiRow = new LinearLayout(this);
        notiRow.setGravity(Gravity.CENTER);
        notiRow.setPadding(0, dp(24), 0, 0);
        content.addView(notiRow);

        View spacer = new View(this);
        content.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView hint = new TextView(this);
        hint.setText("︿\n위로 밀어서 잠금 해제");
        hint.setTextColor(0xB3FFFFFF);
        hint.setGravity(Gravity.CENTER);
        hint.setTextSize(13);
        content.addView(hint);
        hint.animate().translationY(-dp(6)).setDuration(900).withEndAction(new Runnable() {
            public void run() {
                if (hint.isAttachedToWindow())
                    hint.animate().translationY(hint.getTranslationY() == 0 ? -dp(6) : 0).setDuration(900).withEndAction(this).start();
            }
        }).start();

        if (Prefs.b(this, Prefs.LOCK_SHORTCUTS, true)) {
            FrameLayout bottom = new FrameLayout(this);
            TextView torch = shortcut("🔦");
            torch.setOnClickListener(v -> { Actions.toggleTorch(this); torch.setText(Actions.torchOn() ? "💡" : "🔦"); });
            bottom.addView(torch, new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.LEFT | Gravity.CENTER_VERTICAL));
            TextView cam = shortcut("📷");
            cam.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (RuntimeException e) {
                    try { startActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                    catch (RuntimeException ignored) {}
                }
                finish();
            });
            bottom.addView(cam, new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.RIGHT | Gravity.CENTER_VERTICAL));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(64));
            blp.topMargin = dp(16);
            content.addView(bottom, blp);
        }

        root.setOnTouchListener((v, e) -> swipe(e));
        setContentView(root);
    }

    private TextView shortcut(String glyph) {
        TextView t = new TextView(this);
        t.setText(glyph);
        t.setTextSize(22);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0x33FFFFFF);
        t.setBackground(g);
        return t;
    }

    private static int darken(int c, float f) {
        return 0xFF000000 | ((int) (((c >> 16) & 0xFF) * f)) << 16 | ((int) (((c >> 8) & 0xFF) * f)) << 8 | (int) ((c & 0xFF) * f);
    }

    private boolean swipe(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: downY = e.getRawY(); return true;
            case MotionEvent.ACTION_MOVE: {
                float dy = Math.min(0, e.getRawY() - downY);
                content.setTranslationY(dy);
                content.setAlpha(1f + dy / (root.getHeight() * 0.5f));
                return true;
            }
            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: {
                float dy = e.getRawY() - downY;
                if (dy < -root.getHeight() * 0.2f) unlock();
                else content.animate().translationY(0).alpha(1f).setDuration(180).start();
                return true;
            }
        }
        return false;
    }

    private void unlock() {
        content.animate().translationY(-root.getHeight()).alpha(0).setDuration(160).start();
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (!preview && km != null && km.isKeyguardLocked()) {
            km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override public void onDismissSucceeded() { finish(); }
                @Override public void onDismissCancelled() { content.animate().translationY(0).alpha(1f).setDuration(180).start(); }
                @Override public void onDismissError() { finish(); }
            });
        } else {
            finish();
            overridePendingTransition(0, android.R.anim.fade_out);
        }
    }

    private void refreshNoti() {
        if (notiRow == null) return;
        notiRow.removeAllViews();
        if (!Prefs.b(this, Prefs.LOCK_NOTI, true)) return;
        List<String> pkgs = NotiListener.activePackages();
        int n = 0;
        for (String p : pkgs) {
            if (n++ >= 8) break;
            try {
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(getPackageManager().getApplicationIcon(p));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
                lp.setMargins(dp(5), 0, dp(5), 0);
                iv.setPadding(dp(3), dp(3), dp(3), dp(3));
                iv.setBackground(Ui.round(0x33FFFFFF, dp(15)));
                notiRow.addView(iv, lp);
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(batt, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        NotiListener.onChange = () -> runOnUiThread(this::refreshNoti);
        refreshNoti();
        content.setTranslationY(0);
        content.setAlpha(1f);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(batt); } catch (RuntimeException ignored) {}
        NotiListener.onChange = null;
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (content != null) { content.setTranslationY(0); content.setAlpha(1f); }
    }

    @Override
    public void onBackPressed() {
        if (preview) super.onBackPressed();
    }
}
