package kr.sbkwon.decolock;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.PowerManager;
import android.view.WindowManager;

/** Shows the edge light overlay for an incoming notification. */
public final class EdgeLighting {
    private EdgeLighting() {}

    private static EdgeGlowView current;

    public static int[] colorsFor(Context c, Drawable appIcon) {
        int style = Prefs.i(c, Prefs.EL_STYLE, 0);
        if (style == 1) return new int[]{Prefs.i(c, Prefs.EL_COLOR, Prefs.accent(c))};
        if (style == 2 && appIcon != null) {
            int col = Apps.dominantColor(appIcon);
            if (col != 0) return new int[]{col};
        }
        return new int[]{0xFFFF0080, 0xFFFFB000, 0xFF00E5FF, 0xFF7C4DFF, 0xFFFF0080};
    }

    public static void show(Context c, Drawable appIcon) {
        if (!Prefs.b(c, Prefs.EL_ON, false)) return;
        Context ctx = c.getApplicationContext();
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (!pm.isInteractive()) {
            if (!Prefs.b(c, Prefs.EL_WAKE, false)) return;
            wake(ctx, pm);
        }
        if (!Overlay.available(ctx)) return;
        hide();
        EdgeGlowView v = new EdgeGlowView(DecoAccessibility.instance != null ? DecoAccessibility.instance : ctx);
        v.configure(Prefs.i(c, Prefs.EL_EFFECT, 0), Prefs.i(c, Prefs.EL_WIDTH, 6), colorsFor(c, appIcon));
        WindowManager.LayoutParams lp = Overlay.params(-1, -1,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        if (!Overlay.add(ctx, v, lp)) return;
        current = v;
        final EdgeGlowView me = v;
        v.play(Prefs.i(c, Prefs.EL_DUR, 4) * 1000L, () -> {
            Overlay.remove(me);
            if (current == me) current = null;
        });
    }

    @SuppressWarnings("deprecation")
    private static void wake(Context c, PowerManager pm) {
        try {
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                    | PowerManager.ACQUIRE_CAUSES_WAKEUP, "decolock:edge");
            wl.acquire(3000);
        } catch (RuntimeException ignored) {}
    }

    public static void hide() {
        if (current != null) {
            current.stop();
            Overlay.remove(current);
            current = null;
        }
    }
}
