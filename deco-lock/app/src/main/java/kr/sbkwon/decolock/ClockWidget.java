package kr.sbkwon.decolock;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;
import android.util.TypedValue;
import android.widget.RemoteViews;

/** Theme-coloured clock & date home-screen widget. */
public class ClockWidget extends AppWidgetProvider {
    public static final String[] STYLES = {"테마색 카드", "투명 (글자만)", "반투명 유리", "흰 카드"};

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, views(c));
    }

    public static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, ClockWidget.class));
        for (int id : ids) m.updateAppWidget(id, views(c));
    }

    static RemoteViews views(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_clock);
        int style = Prefs.i(c, Prefs.WG_STYLE, 0);
        int size = Prefs.i(c, Prefs.WG_SIZE, 56);
        int accent = Prefs.accent(c);
        int text = 0xFFFFFFFF;
        switch (style) {
            case 0: v.setInt(R.id.wg_bg, "setColorFilter", accent); v.setInt(R.id.wg_bg, "setImageAlpha", 255); break;
            case 1: v.setInt(R.id.wg_bg, "setImageAlpha", 0); break;
            case 2: v.setInt(R.id.wg_bg, "setColorFilter", 0xFF000000); v.setInt(R.id.wg_bg, "setImageAlpha", 90); break;
            case 3: v.setInt(R.id.wg_bg, "setColorFilter", 0xFFFFFFFF); v.setInt(R.id.wg_bg, "setImageAlpha", 240); text = accent; break;
        }
        v.setTextColor(R.id.wg_time, text);
        v.setTextColor(R.id.wg_date, style == 3 ? 0xFF555555 : text);
        v.setTextViewTextSize(R.id.wg_time, TypedValue.COMPLEX_UNIT_SP, size);
        v.setTextViewTextSize(R.id.wg_date, TypedValue.COMPLEX_UNIT_SP, Math.max(12, size / 3.6f));
        Intent open = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
        PendingIntent pi = PendingIntent.getActivity(c, 1, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        v.setOnClickPendingIntent(R.id.wg_root, pi);
        return v;
    }
}
