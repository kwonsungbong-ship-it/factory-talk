package kr.sbkwon.decolock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Home Up: a lightweight customizable home screen. */
public class HomeActivity extends Activity {
    private FrameLayout root;
    private LinearLayout drawer;
    private GridView drawerGrid;
    private final List<Apps.App> drawerApps = new ArrayList<>();
    private BaseAdapter drawerAdapter;
    private GestureDetector gd;
    private int cols, iconPx, shape, labelColor;
    private boolean labels;

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { Apps.invalidate(); build(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(0x00000000);
        w.setNavigationBarColor(0x00000000);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        root = new FrameLayout(this);
        root.setFitsSystemWindows(false);
        setContentView(root);

        gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null) return false;
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dy) < Math.abs(e2.getX() - e1.getX())) return false;
                if (dy < -Ui.dp(HomeActivity.this, 60) && drawer.getVisibility() != View.VISIBLE) { openDrawer(); return true; }
                if (dy > Ui.dp(HomeActivity.this, 60) && drawer.getVisibility() != View.VISIBLE) { Actions.run(HomeActivity.this, Actions.NOTIFICATIONS); return true; }
                if (dy > Ui.dp(HomeActivity.this, 80) && drawer.getVisibility() == View.VISIBLE && drawerGrid.getFirstVisiblePosition() == 0
                        && (drawerGrid.getChildCount() == 0 || drawerGrid.getChildAt(0).getTop() >= 0)) { closeDrawer(); return true; }
                return false;
            }
            @Override public boolean onDoubleTap(MotionEvent e) {
                if (drawer.getVisibility() != View.VISIBLE && Prefs.b(HomeActivity.this, Prefs.HOME_DBL_LOCK, true)) {
                    Actions.run(HomeActivity.this, Actions.LOCK);
                    return true;
                }
                return false;
            }
        });

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(pkgReceiver); } catch (RuntimeException ignored) {}
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (drawer != null && drawer.getVisibility() == View.VISIBLE) closeDrawer();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        boolean handled = gd.onTouchEvent(e);
        if (handled && e.getActionMasked() == MotionEvent.ACTION_UP) {
            MotionEvent c = MotionEvent.obtain(e);
            c.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(c);
            c.recycle();
            return true;
        }
        return super.dispatchTouchEvent(e);
    }

    @Override
    public void onBackPressed() {
        if (drawer != null && drawer.getVisibility() == View.VISIBLE) closeDrawer();
    }

    private int dp(float v) { return Ui.dp(this, v); }

    private void build() {
        boolean drawerOpen = drawer != null && drawer.getVisibility() == View.VISIBLE;
        root.removeAllViews();
        cols = Prefs.i(this, Prefs.HOME_COLS, 4);
        iconPx = dp(Prefs.i(this, Prefs.HOME_ICON, 54));
        shape = Prefs.i(this, Prefs.HOME_SHAPE, 2);
        labels = Prefs.b(this, Prefs.HOME_LABELS, true);
        labelColor = Prefs.b(this, Prefs.HOME_LABEL_DARK, false) ? 0xFF1C1C28 : 0xFFFFFFFF;
        int accent = Prefs.accent(this);

        View dim = new View(this);
        dim.setBackgroundColor((Prefs.i(this, Prefs.HOME_DIM, 15) * 255 / 100) << 24);
        root.addView(dim, Ui.match());

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(56), dp(14), dp(28));
        page.setOnLongClickListener(v -> { homeMenu(); return true; });
        root.addView(page, Ui.match());

        if (Prefs.b(this, Prefs.HOME_CLOCK, true)) {
            TextClock time = new TextClock(this);
            time.setFormat12Hour("h:mm");
            time.setFormat24Hour("HH:mm");
            time.setTextSize(64);
            time.setTextColor(labelColor);
            time.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
            time.setShadowLayer(8, 0, 2, labelColor == 0xFFFFFFFF ? 0x66000000 : 0);
            time.setPadding(dp(10), 0, 0, 0);
            page.addView(time);
            TextClock date = new TextClock(this);
            date.setFormat12Hour("M월 d일 EEEE");
            date.setFormat24Hour("M월 d일 EEEE");
            date.setTextSize(16);
            date.setTextColor(labelColor);
            date.setShadowLayer(6, 0, 1, labelColor == 0xFFFFFFFF ? 0x66000000 : 0);
            date.setPadding(dp(12), 0, 0, dp(18));
            page.addView(date);
            time.setOnClickListener(v -> openIntent(new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)));
            date.setOnClickListener(v -> openIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)));
        }

        // search pill
        TextView search = new TextView(this);
        search.setText("🔍  앱 검색");
        search.setTextColor(0xCCFFFFFF);
        search.setTextSize(15);
        search.setPadding(dp(18), dp(12), dp(18), dp(12));
        search.setBackground(Ui.round(0x55000000, dp(24)));
        search.setOnClickListener(v -> openDrawer(true));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.setMargins(dp(6), 0, dp(6), dp(16));
        page.addView(search, slp);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(cols);
        List<Apps.App> home = Apps.resolve(this, Prefs.list(this, Prefs.HOME_APPS));
        for (Apps.App a : home) grid.addView(appCell(a, true, false));
        if (home.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText("빈 곳을 길게 누르면 홈 화면 앱을 고를 수 있어요\n위로 쓸어 올리면 전체 앱");
            hint.setTextColor(labelColor);
            hint.setShadowLayer(6, 0, 1, 0x66000000);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(dp(10), dp(30), dp(10), dp(30));
            page.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        }
        page.addView(grid, new LinearLayout.LayoutParams(-1, -2));

        View spacer = new View(this);
        page.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView up = new TextView(this);
        up.setText("︿");
        up.setTextColor(labelColor);
        up.setGravity(Gravity.CENTER);
        up.setTextSize(18);
        up.setOnClickListener(v -> openDrawer());
        page.addView(up, new LinearLayout.LayoutParams(-1, dp(28)));

        List<Apps.App> dockApps = Apps.resolve(this, Prefs.list(this, Prefs.DOCK_APPS));
        LinearLayout dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(8), dp(10), dp(8), dp(10));
        dock.setBackground(Ui.round(0x33FFFFFF, dp(30)));
        for (Apps.App a : dockApps) {
            View cell = appCell(a, false, true);
            dock.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
        }
        if (dockApps.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("독 앱 추가 +");
            t.setTextColor(labelColor);
            t.setPadding(0, dp(12), 0, dp(12));
            t.setOnClickListener(v -> pick(Prefs.DOCK_APPS, "독 앱 선택", 5));
            dock.addView(t);
        }
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
        dlp.bottomMargin = dp(navBarHeight() > 0 ? 8 : 0);
        page.addView(dock, dlp);

        buildDrawer(accent);
        if (drawerOpen) { drawer.setVisibility(View.VISIBLE); drawer.setTranslationY(0); }
    }

    private int navBarHeight() {
        int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    private View appCell(final Apps.App a, boolean onHome, boolean inDock) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(0, dp(8), 0, dp(8));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(Apps.shaped(this, a, shape, iconPx));
        col.addView(iv, new LinearLayout.LayoutParams(iconPx, iconPx));
        if (labels && !inDock) {
            TextView t = new TextView(this);
            t.setText(a.label);
            t.setTextSize(12);
            t.setTextColor(labelColor);
            if (labelColor == 0xFFFFFFFF) t.setShadowLayer(5, 0, 1, 0x99000000);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(2), dp(5), dp(2), 0);
            col.addView(t, new LinearLayout.LayoutParams(-1, -2));
        }
        Ui.ripple(col, 0x00000000, dp(16));
        col.setOnClickListener(v -> {
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(70).withEndAction(() -> v.animate().scaleX(1).scaleY(1).setDuration(90).start()).start();
            Apps.launch(this, a);
        });
        col.setOnLongClickListener(v -> { appMenu(a); return true; });
        if (!inDock) {
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            glp.width = 0;
            col.setLayoutParams(glp);
        }
        return col;
    }

    private void appMenu(final Apps.App a) {
        final List<String> home = Prefs.list(this, Prefs.HOME_APPS);
        final List<String> dock = Prefs.list(this, Prefs.DOCK_APPS);
        final boolean onHome = home.contains(a.key), onDock = dock.contains(a.key);
        String[] items = {onHome ? "홈 화면에서 빼기" : "홈 화면에 추가", onDock ? "독에서 빼기" : "독에 추가",
                "엣지 패널에 추가", "앱 정보", "삭제"};
        new AlertDialog.Builder(this).setTitle(a.label).setItems(items, (d, which) -> {
            switch (which) {
                case 0:
                    if (onHome) home.remove(a.key); else home.add(a.key);
                    Prefs.putList(this, Prefs.HOME_APPS, home); build(); break;
                case 1:
                    if (onDock) dock.remove(a.key);
                    else if (dock.size() < 5) dock.add(a.key);
                    else Actions.toast(this, "독에는 5개까지 넣을 수 있어요");
                    Prefs.putList(this, Prefs.DOCK_APPS, dock); build(); break;
                case 2: {
                    List<String> p = Prefs.list(this, Prefs.PANEL_APPS);
                    if (!p.contains(a.key)) p.add(a.key);
                    Prefs.putList(this, Prefs.PANEL_APPS, p);
                    Actions.toast(this, "엣지 패널에 추가했어요");
                    break;
                }
                case 3:
                    openIntent(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)));
                    break;
                case 4:
                    openIntent(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg)));
                    break;
            }
        }).show();
    }

    private void homeMenu() {
        String[] items = {"홈 화면 앱 고르기", "독 앱 고르기", "배경화면 만들기", "시스템 배경화면 변경", "폰 꾸미기 설정", "기본 홈 앱으로 설정"};
        new AlertDialog.Builder(this).setItems(items, (d, w) -> {
            if (w == 0) pick(Prefs.HOME_APPS, "홈 화면 앱 선택", cols * 6);
            else if (w == 1) pick(Prefs.DOCK_APPS, "독 앱 선택", 5);
            else if (w == 2) openIntent(new Intent(this, WallpaperMakerActivity.class));
            else if (w == 3) openIntent(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), "배경화면"));
            else if (w == 4) openIntent(new Intent(this, SettingsActivity.class).putExtra(SettingsActivity.EXTRA_MODULE, SettingsActivity.M_HOME));
            else SettingsActivity.requestDefaultHome(this);
        }).show();
    }

    private void pick(String key, String title, int max) {
        openIntent(new Intent(this, AppPickerActivity.class)
                .putExtra(AppPickerActivity.EXTRA_KEY, key)
                .putExtra(AppPickerActivity.EXTRA_TITLE, title)
                .putExtra(AppPickerActivity.EXTRA_MAX, max));
    }

    private void openIntent(Intent i) {
        try { startActivity(i); } catch (RuntimeException e) { Actions.toast(this, "열 수 있는 앱이 없어요"); }
    }

    // ---------------------------------------------------------------- app drawer

    private EditText drawerSearch;

    private void buildDrawer(int accent) {
        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setBackgroundColor(0xE6101018);
        drawer.setPadding(dp(14), dp(48), dp(14), dp(16));
        drawer.setVisibility(View.GONE);
        drawer.setClickable(true);

        drawerSearch = new EditText(this);
        drawerSearch.setHint("앱 검색");
        drawerSearch.setHintTextColor(0x88FFFFFF);
        drawerSearch.setTextColor(0xFFFFFFFF);
        drawerSearch.setSingleLine(true);
        drawerSearch.setBackground(Ui.round(0x22FFFFFF, dp(24)));
        drawerSearch.setPadding(dp(18), dp(12), dp(18), dp(12));
        drawer.addView(drawerSearch, new LinearLayout.LayoutParams(-1, -2));

        drawerApps.clear();
        drawerApps.addAll(Apps.all(this));
        drawerGrid = new GridView(this);
        drawerGrid.setNumColumns(cols);
        drawerGrid.setVerticalSpacing(dp(6));
        drawerGrid.setSelector(new android.graphics.drawable.ColorDrawable(0));
        drawerAdapter = new BaseAdapter() {
            public int getCount() { return drawerApps.size(); }
            public Object getItem(int p) { return drawerApps.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int p, View cv, ViewGroup parent) {
                LinearLayout col = (LinearLayout) cv;
                if (col == null) {
                    col = new LinearLayout(HomeActivity.this);
                    col.setOrientation(LinearLayout.VERTICAL);
                    col.setGravity(Gravity.CENTER_HORIZONTAL);
                    col.setPadding(0, dp(8), 0, dp(8));
                    col.addView(new ImageView(HomeActivity.this), new LinearLayout.LayoutParams(iconPx, iconPx));
                    TextView t = new TextView(HomeActivity.this);
                    t.setTextSize(12);
                    t.setTextColor(0xFFFFFFFF);
                    t.setSingleLine(true);
                    t.setEllipsize(TextUtils.TruncateAt.END);
                    t.setGravity(Gravity.CENTER);
                    t.setPadding(dp(2), dp(5), dp(2), 0);
                    col.addView(t, new LinearLayout.LayoutParams(-1, -2));
                }
                Apps.App a = drawerApps.get(p);
                ((ImageView) col.getChildAt(0)).setImageDrawable(Apps.shaped(HomeActivity.this, a, shape, iconPx));
                ((TextView) col.getChildAt(1)).setText(a.label);
                return col;
            }
        };
        drawerGrid.setAdapter(drawerAdapter);
        drawerGrid.setOnItemClickListener((parent, view, pos, id) -> { Apps.launch(this, drawerApps.get(pos)); closeDrawer(); });
        drawerGrid.setOnItemLongClickListener((parent, view, pos, id) -> { appMenu(drawerApps.get(pos)); return true; });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, 0, 1);
        glp.topMargin = dp(12);
        drawer.addView(drawerGrid, glp);

        drawerSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable e) {
                String q = e.toString().trim().toLowerCase();
                drawerApps.clear();
                for (Apps.App a : Apps.all(HomeActivity.this)) if (q.isEmpty() || a.label.toLowerCase().contains(q)) drawerApps.add(a);
                drawerAdapter.notifyDataSetChanged();
            }
        });
        drawerSearch.setOnEditorActionListener((v, actionId, ev) -> {
            if (!drawerApps.isEmpty()) { Apps.launch(this, drawerApps.get(0)); closeDrawer(); }
            return true;
        });
        root.addView(drawer, Ui.match());
    }

    private void openDrawer() { openDrawer(false); }

    private void openDrawer(boolean focusSearch) {
        drawer.setVisibility(View.VISIBLE);
        drawer.setTranslationY(root.getHeight());
        drawer.animate().translationY(0).setDuration(240).start();
        if (focusSearch) {
            drawerSearch.requestFocus();
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(drawerSearch, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void closeDrawer() {
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(drawerSearch.getWindowToken(), 0);
        drawerSearch.setText("");
        drawer.animate().translationY(root.getHeight()).setDuration(200).withEndAction(() -> drawer.setVisibility(View.GONE)).start();
    }
}
