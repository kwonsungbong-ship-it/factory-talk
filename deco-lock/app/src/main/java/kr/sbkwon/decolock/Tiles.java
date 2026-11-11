package kr.sbkwon.decolock;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** QuickStar: extra Quick Settings tiles. */
public final class Tiles {
    private Tiles() {}

    static void state(TileService s, boolean on) {
        Tile t = s.getQsTile();
        if (t == null) return;
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }

    static void collapseThen(Runnable r) {
        DecoAccessibility a = DecoAccessibility.instance;
        if (a != null) {
            a.performGlobalAction(Build.VERSION.SDK_INT >= 31
                    ? AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE
                    : AccessibilityService.GLOBAL_ACTION_BACK);
        }
        new Handler(Looper.getMainLooper()).postDelayed(r, 350);
    }

    public static class Flash extends TileService {
        @Override public void onStartListening() { state(this, Actions.torchOn()); }
        @Override public void onClick() { Actions.toggleTorch(this); state(this, Actions.torchOn()); }
    }

    public static class Caffeine extends TileService {
        @Override public void onStartListening() { state(this, Prefs.b(this, Prefs.CAFFEINE, false)); }
        @Override public void onClick() { Actions.run(this, Actions.CAFFEINE); state(this, Prefs.b(this, Prefs.CAFFEINE, false)); }
    }

    public static class Lock extends TileService {
        @Override public void onStartListening() { state(this, false); }
        @Override public void onClick() { Actions.run(this, Actions.LOCK); }
    }

    public static class EdgeLight extends TileService {
        @Override public void onStartListening() { state(this, Prefs.b(this, Prefs.EL_ON, false)); }
        @Override public void onClick() {
            boolean on = !Prefs.b(this, Prefs.EL_ON, false);
            Prefs.put(this, Prefs.EL_ON, on);
            state(this, on);
            if (on) EdgeLighting.show(this, null);
        }
    }

    public static class Panel extends TileService {
        @Override public void onStartListening() { state(this, false); }
        @Override public void onClick() {
            final TileService me = this;
            collapseThen(() -> Actions.run(me, Actions.PANEL));
        }
    }
}
