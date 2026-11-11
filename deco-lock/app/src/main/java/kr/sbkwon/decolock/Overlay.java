package kr.sbkwon.decolock;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;

/**
 * Adds floating views. Prefers the accessibility overlay (draws over the status bar,
 * navigation bar and lock screen, no extra permission); falls back to the normal
 * "display over other apps" overlay.
 */
public final class Overlay {
    private Overlay() {}

    public static boolean available(Context c) {
        return DecoAccessibility.instance != null || Settings.canDrawOverlays(c);
    }

    public static WindowManager.LayoutParams params(int w, int h, int flags) {
        int type = DecoAccessibility.instance != null
                ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h, type, flags, PixelFormat.TRANSLUCENT);
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        return lp;
    }

    private static WindowManager wm(Context c) {
        Context host = DecoAccessibility.instance != null ? DecoAccessibility.instance : c.getApplicationContext();
        return (WindowManager) host.getSystemService(Context.WINDOW_SERVICE);
    }

    /** Returns true when the view was attached. */
    public static boolean add(Context c, View v, WindowManager.LayoutParams lp) {
        if (!available(c)) return false;
        try {
            WindowManager wm = wm(c);
            wm.addView(v, lp);
            v.setTag(R.id.overlay_wm, wm);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void update(View v, WindowManager.LayoutParams lp) {
        Object wm = v == null ? null : v.getTag(R.id.overlay_wm);
        if (wm instanceof WindowManager && v.isAttachedToWindow()) {
            try { ((WindowManager) wm).updateViewLayout(v, lp); } catch (RuntimeException ignored) {}
        }
    }

    public static void remove(View v) {
        if (v == null) return;
        Object wm = v.getTag(R.id.overlay_wm);
        if (wm instanceof WindowManager) {
            try { ((WindowManager) wm).removeViewImmediate(v); } catch (RuntimeException ignored) {}
            v.setTag(R.id.overlay_wm, null);
        }
    }
}
