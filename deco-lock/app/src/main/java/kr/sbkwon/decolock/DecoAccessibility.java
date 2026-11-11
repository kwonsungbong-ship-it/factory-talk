package kr.sbkwon.decolock;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;

/**
 * Hosts every always-on overlay: edge handles (Edge Touch / One Hand Operation+),
 * the edge panel, the floating navigation bar (NavStar), "keep screen on",
 * and volume-key remapping (Sound Assistant).
 */
public class DecoAccessibility extends AccessibilityService {
    public static volatile DecoAccessibility instance;

    private final Handler h = new Handler(Looper.getMainLooper());
    private View handleL, handleR, nav, caffeine;
    private EdgePanel panel;

    @Override
    protected void onServiceConnected() {
        instance = this;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            if (Prefs.b(this, Prefs.VOL_MEDIA_KEYS, false)) info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            else info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
        refresh();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {}

    @Override
    public boolean onUnbind(android.content.Intent i) {
        removeAll();
        instance = null;
        return super.onUnbind(i);
    }

    @Override
    public void onDestroy() {
        removeAll();
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        h.postDelayed(this::refresh, 200);
    }

    private void removeAll() {
        Overlay.remove(handleL); handleL = null;
        Overlay.remove(handleR); handleR = null;
        Overlay.remove(nav); nav = null;
        Overlay.remove(caffeine); caffeine = null;
        closePanel();
    }

    /** Re-reads settings and rebuilds overlays. Call after any settings change. */
    public void refresh() {
        Overlay.remove(handleL); handleL = null;
        Overlay.remove(handleR); handleR = null;
        Overlay.remove(nav); nav = null;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            boolean want = Prefs.b(this, Prefs.VOL_MEDIA_KEYS, false);
            boolean has = (info.flags & AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0;
            if (want != has) {
                if (want) info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
                else info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
                setServiceInfo(info);
            }
        }
        if (Prefs.b(this, Prefs.EH_ON, false)) {
            int side = Prefs.i(this, Prefs.EH_SIDE, 0);
            if (side == 0 || side == 2) handleR = addHandle(false);
            if (side == 1 || side == 2) handleL = addHandle(true);
        }
        if (Prefs.b(this, Prefs.NAV_ON, false)) addNav();
        applyCaffeine();
    }

    // ---------------------------------------------------------------- edge handles

    private View addHandle(boolean left) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = Ui.dp(this, Prefs.i(this, Prefs.EH_WIDTH, 14));
        int len = Ui.dp(this, Prefs.i(this, Prefs.EH_LEN, 160));
        int pos = Prefs.i(this, Prefs.EH_POS, 40);
        HandleView v = new HandleView(this, left);
        WindowManager.LayoutParams lp = Overlay.params(w, len,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        lp.gravity = Gravity.TOP | (left ? Gravity.LEFT : Gravity.RIGHT);
        lp.y = Math.max(0, dm.heightPixels * pos / 100 - len / 2);
        return Overlay.add(this, v, lp) ? v : null;
    }

    void vibrate() {
        if (!Prefs.b(this, Prefs.EH_VIB, true)) return;
        try {
            Vibrator vb = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vb != null) vb.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (RuntimeException ignored) {}
    }

    private class HandleView extends View {
        final boolean left;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float downX, downY;
        boolean fired, moved;
        final Runnable longPress = new Runnable() {
            public void run() {
                if (!moved) {
                    fired = true;
                    vibrate();
                    Actions.run(DecoAccessibility.this, Prefs.actLong(DecoAccessibility.this));
                }
            }
        };

        HandleView(Context c, boolean left) {
            super(c);
            this.left = left;
            p.setColor(Prefs.accent(c));
        }

        @Override
        protected void onDraw(Canvas c) {
            if (!Prefs.b(getContext(), Prefs.EH_VISIBLE, true)) return;
            float w = Ui.dp(getContext(), 4);
            float x = left ? Ui.dp(getContext(), 2) : getWidth() - w - Ui.dp(getContext(), 2);
            p.setAlpha(150);
            c.drawRoundRect(new RectF(x, 0, x + w, getHeight()), w, w, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float slop = Ui.dp(getContext(), 10);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX(); downY = e.getRawY();
                    fired = false; moved = false;
                    h.postDelayed(longPress, 550);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (Math.hypot(dx, dy) > slop) { moved = true; h.removeCallbacks(longPress); }
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    h.removeCallbacks(longPress);
                    if (fired) return true;
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    float inward = left ? dx : -dx;
                    float min = Ui.dp(getContext(), 28);
                    int act = Actions.NONE;
                    if (inward > min || Math.abs(dy) > min * 1.5f) {
                        if (dy < -Math.max(inward, 0) * 0.7f && Math.abs(dy) > min) act = Prefs.actUp(getContext());
                        else if (dy > Math.max(inward, 0) * 0.7f && Math.abs(dy) > min) act = Prefs.actDown(getContext());
                        else if (inward > min) act = Prefs.actIn(getContext());
                    }
                    if (act != Actions.NONE) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        vibrate();
                        if (act == Actions.PANEL) openPanel(left); else Actions.run(getContext(), act);
                    }
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    h.removeCallbacks(longPress);
                    return true;
            }
            return true;
        }
    }

    // ---------------------------------------------------------------- edge panel

    private boolean lastSideLeft = false;

    public void togglePanel() {
        if (panel != null) closePanel(); else openPanel(lastSideLeft);
    }

    public void openPanel(boolean fromLeft) {
        if (panel != null) return;
        lastSideLeft = fromLeft;
        panel = new EdgePanel(this, fromLeft, this::closePanel);
        WindowManager.LayoutParams lp = Overlay.params(-1, -1,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        if (!Overlay.add(this, panel, lp)) panel = null;
        else panel.animateIn();
    }

    public void closePanel() {
        final EdgePanel p = panel;
        if (p == null) return;
        panel = null;
        p.animateOut(() -> Overlay.remove(p));
    }

    // ---------------------------------------------------------------- floating nav bar

    private void addNav() {
        int hgt = Ui.dp(this, Prefs.i(this, Prefs.NAV_HEIGHT, 44));
        int alpha = Prefs.i(this, Prefs.NAV_ALPHA, 70);
        boolean rev = Prefs.b(this, Prefs.NAV_REVERSE, false);
        int style = Prefs.i(this, Prefs.NAV_STYLE, 0);
        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor((alpha * 255 / 100) << 24);
        bar.setGravity(Gravity.CENTER);
        int[] order = rev ? new int[]{Actions.RECENTS, Actions.HOME, Actions.BACK}
                : new int[]{Actions.BACK, Actions.HOME, Actions.RECENTS};
        for (final int a : order) {
            NavGlyph g = new NavGlyph(this, a, style);
            g.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); Actions.run(this, a); });
            g.setOnLongClickListener(v -> {
                vibrate();
                if (a == Actions.HOME) openPanel(false);
                else if (a == Actions.BACK) Actions.run(this, Actions.LAST_APP);
                else Actions.run(this, Actions.SPLIT);
                return true;
            });
            bar.addView(g, new LinearLayout.LayoutParams(0, -1, 1));
        }
        WindowManager.LayoutParams lp = Overlay.params(-1, hgt,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        lp.gravity = Gravity.BOTTOM;
        if (Overlay.add(this, bar, lp)) nav = bar;
    }

    static class NavGlyph extends View {
        public static final String[] STYLES = {"기본 도형", "점", "화살표"};
        final int action, style;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        NavGlyph(Context c, int action, int style) {
            super(c);
            this.action = action;
            this.style = style;
            p.setColor(0xFFFFFFFF);
            p.setStrokeWidth(Ui.dp(c, 2));
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeCap(Paint.Cap.ROUND);
            Ui.ripple(this, 0x00000000, 0);
        }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, s = Math.min(getHeight() * 0.22f, Ui.dp(getContext(), 9));
            if (style == 1) {
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy, action == Actions.HOME ? s * 0.7f : s * 0.45f, p);
                return;
            }
            p.setStyle(Paint.Style.STROKE);
            Path path = new Path();
            if (action == Actions.BACK) {
                if (style == 2) { path.moveTo(cx + s * 0.5f, cy - s); path.lineTo(cx - s * 0.5f, cy); path.lineTo(cx + s * 0.5f, cy + s); }
                else { path.moveTo(cx + s * 0.8f, cy - s); path.lineTo(cx - s * 0.8f, cy); path.lineTo(cx + s * 0.8f, cy + s); path.close(); }
                c.drawPath(path, p);
            } else if (action == Actions.HOME) {
                if (style == 2) c.drawRoundRect(new RectF(cx - s * 1.6f, cy - s * 0.25f, cx + s * 1.6f, cy + s * 0.25f), s, s, p);
                else c.drawCircle(cx, cy, s, p);
            } else {
                if (style == 2) { c.drawLine(cx - s, cy - s * 0.6f, cx + s, cy - s * 0.6f, p); c.drawLine(cx - s, cy, cx + s, cy, p); c.drawLine(cx - s, cy + s * 0.6f, cx + s, cy + s * 0.6f, p); }
                else c.drawRoundRect(new RectF(cx - s * 0.85f, cy - s * 0.85f, cx + s * 0.85f, cy + s * 0.85f), s * 0.2f, s * 0.2f, p);
            }
        }
    }

    // ---------------------------------------------------------------- keep screen on

    public void applyCaffeine() {
        boolean on = Prefs.b(this, Prefs.CAFFEINE, false);
        if (on && caffeine == null) {
            View v = new View(this);
            WindowManager.LayoutParams lp = Overlay.params(1, 1,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            if (Overlay.add(this, v, lp)) caffeine = v;
        } else if (!on && caffeine != null) {
            Overlay.remove(caffeine);
            caffeine = null;
        }
    }

    // ---------------------------------------------------------------- volume keys

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (!Prefs.b(this, Prefs.VOL_MEDIA_KEYS, false)) return false;
        int k = e.getKeyCode();
        if (k != KeyEvent.KEYCODE_VOLUME_UP && k != KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am.getMode() == AudioManager.MODE_IN_CALL || am.getMode() == AudioManager.MODE_IN_COMMUNICATION) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    k == KeyEvent.KEYCODE_VOLUME_UP ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI);
        }
        return true;
    }
}
