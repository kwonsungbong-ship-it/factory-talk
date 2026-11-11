package kr.sbkwon.decolock;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** All settings live in one SharedPreferences file. */
public final class Prefs {
    private Prefs() {}

    public static final String FILE = "deco";

    // Theme
    public static final String ACCENT = "accent";
    public static final int DEF_ACCENT = 0xFF6C5CE7;

    // Edge lighting
    public static final String EL_ON = "el_on", EL_STYLE = "el_style", EL_WIDTH = "el_width",
            EL_DUR = "el_dur", EL_EFFECT = "el_effect", EL_COLOR = "el_color", EL_WAKE = "el_wake";

    // Edge handle / one-hand gestures / panel
    public static final String EH_ON = "eh_on", EH_SIDE = "eh_side", EH_POS = "eh_pos", EH_LEN = "eh_len",
            EH_WIDTH = "eh_width", EH_VISIBLE = "eh_visible", EH_VIB = "eh_vib";
    public static final String ACT_IN = "act_in", ACT_UP = "act_up", ACT_DOWN = "act_down", ACT_LONG = "act_long";
    public static final String PANEL_APPS = "panel_apps";

    // Floating navigation bar
    public static final String NAV_ON = "nav_on", NAV_HEIGHT = "nav_height", NAV_ALPHA = "nav_alpha",
            NAV_REVERSE = "nav_reverse", NAV_STYLE = "nav_style";

    // Sound
    public static final String VOL_MEDIA_KEYS = "vol_media_keys";

    // Launcher
    public static final String HOME_COLS = "home_cols", HOME_ICON = "home_icon", HOME_LABELS = "home_labels",
            HOME_SHAPE = "home_shape", HOME_APPS = "home_apps", DOCK_APPS = "dock_apps", HOME_CLOCK = "home_clock",
            HOME_DBL_LOCK = "home_dbl_lock", HOME_DIM = "home_dim", HOME_LABEL_DARK = "home_label_dark";

    // Lock screen
    public static final String LOCK_ON = "lock_on", LOCK_STYLE = "lock_style", LOCK_BG = "lock_bg",
            LOCK_24H = "lock_24h", LOCK_NOTI = "lock_noti", LOCK_SHORTCUTS = "lock_shortcuts";

    // Live wallpaper
    public static final String LW_STYLE = "lw_style", LW_MOTION = "lw_motion";

    // Widget
    public static final String WG_STYLE = "wg_style", WG_SIZE = "wg_size";

    // Caffeine state
    public static final String CAFFEINE = "caffeine";

    public static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean b(Context c, String k, boolean d) { return sp(c).getBoolean(k, d); }
    public static int i(Context c, String k, int d) { return sp(c).getInt(k, d); }
    public static String s(Context c, String k, String d) { return sp(c).getString(k, d); }
    public static void put(Context c, String k, boolean v) { sp(c).edit().putBoolean(k, v).apply(); }
    public static void put(Context c, String k, int v) { sp(c).edit().putInt(k, v).apply(); }
    public static void put(Context c, String k, String v) { sp(c).edit().putString(k, v).apply(); }

    public static int accent(Context c) { return i(c, ACCENT, DEF_ACCENT); }

    /** Component lists are stored as "pkg/cls|pkg/cls". */
    public static List<String> list(Context c, String k) {
        List<String> out = new ArrayList<>();
        String raw = s(c, k, "");
        if (raw.isEmpty()) return out;
        for (String p : raw.split("\\|")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    public static void putList(Context c, String k, List<String> v) {
        StringBuilder sb = new StringBuilder();
        for (String p : v) { if (sb.length() > 0) sb.append('|'); sb.append(p); }
        put(c, k, sb.toString());
    }

    // Action defaults for edge gestures
    public static int actIn(Context c) { return i(c, ACT_IN, Actions.PANEL); }
    public static int actUp(Context c) { return i(c, ACT_UP, Actions.HOME); }
    public static int actDown(Context c) { return i(c, ACT_DOWN, Actions.BACK); }
    public static int actLong(Context c) { return i(c, ACT_LONG, Actions.RECENTS); }
}
