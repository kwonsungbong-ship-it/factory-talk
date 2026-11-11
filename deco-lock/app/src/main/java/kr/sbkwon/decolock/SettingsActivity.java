package kr.sbkwon.decolock;

import android.app.Activity;
import android.app.StatusBarManager;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One settings screen per module. */
public class SettingsActivity extends Activity {
    public static final String EXTRA_MODULE = "module";
    public static final String M_EDGE_LIGHT = "edge_light", M_GESTURE = "gesture", M_NAV = "nav", M_HOME = "home",
            M_LOCK = "lock", M_WALL = "wall", M_HISTORY = "history", M_SOUND = "sound", M_QUICK = "quick", M_THEME = "theme";

    private Ui ui;
    private String module;

    // ---------------------------------------------------------------- permission helpers

    public static boolean a11yEnabled(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(c.getPackageName() + "/");
    }

    public static boolean listenerEnabled(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(c.getPackageName() + "/");
    }

    public static void openA11y(Context c) {
        try { c.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (RuntimeException ignored) {}
    }

    /** null = module has no on/off switch. */
    public static Boolean moduleOn(Context c, String m) {
        switch (m) {
            case M_EDGE_LIGHT: return Prefs.b(c, Prefs.EL_ON, false);
            case M_GESTURE: return Prefs.b(c, Prefs.EH_ON, false);
            case M_NAV: return Prefs.b(c, Prefs.NAV_ON, false);
            case M_LOCK: return Prefs.b(c, Prefs.LOCK_ON, false);
            case M_SOUND: return Prefs.b(c, Prefs.VOL_MEDIA_KEYS, false);
            case M_HOME: {
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
                android.content.pm.ResolveInfo ri = c.getPackageManager().resolveActivity(i, 0);
                return ri != null && ri.activityInfo != null && c.getPackageName().equals(ri.activityInfo.packageName);
            }
            case M_WALL: {
                android.app.WallpaperInfo wi = WallpaperManager.getInstance(c).getWallpaperInfo();
                return wi != null && c.getPackageName().equals(wi.getPackageName());
            }
            default: return null;
        }
    }

    public static void refreshAll(Context c) {
        if (DecoAccessibility.instance != null) DecoAccessibility.instance.refresh();
        ClockWidget.updateAll(c);
        LockService.sync(c);
    }


    // ---------------------------------------------------------------- default home

    public static final int REQ_HOME = 4201;

    /** Asks the system to make Home Up the default launcher, with fallbacks for OEM skins. */
    public static void requestDefaultHome(Activity a) {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                android.app.role.RoleManager rm = a.getSystemService(android.app.role.RoleManager.class);
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)
                        && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                    a.startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), REQ_HOME);
                    return;
                }
            } catch (RuntimeException ignored) {}
        }
        openHomeSettings(a);
    }

    /** Tries the default-apps screens that different phone makers actually ship. */
    public static void openHomeSettings(Activity a) {
        String[] actions = {Settings.ACTION_HOME_SETTINGS, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS};
        for (String act : actions) {
            Intent i = new Intent(act);
            if (i.resolveActivity(a.getPackageManager()) != null) {
                try {
                    a.startActivity(i);
                    Actions.toast(a, "목록에서 '꾸미기 홈'을 골라 주세요 (기본 앱 > 홈 앱)");
                    return;
                } catch (RuntimeException ignored) {}
            }
        }
        // last resort: show the system "which home?" chooser
        try {
            a.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Actions.toast(a, "'꾸미기 홈'을 고르고 '항상'을 눌러 주세요");
        } catch (RuntimeException e) {
            a.startActivity(new Intent(Settings.ACTION_SETTINGS));
            Actions.toast(a, "설정 > 앱 > 기본 앱 > 홈 앱에서 '꾸미기 홈'을 골라 주세요");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_HOME) return;
        Boolean on = moduleOn(this, M_HOME);
        if (on != null && on) Actions.toast(this, "기본 홈 앱으로 설정했어요");
        else openHomeSettings(this);
    }

    // ---------------------------------------------------------------- screens

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        module = getIntent().getStringExtra(EXTRA_MODULE);
        if (module == null) module = M_THEME;
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private final Runnable apply = () -> refreshAll(this);

    private void build() {
        ui = new Ui(this);
        switch (module) {
            case M_EDGE_LIGHT: edgeLight(); break;
            case M_GESTURE: gesture(); break;
            case M_NAV: nav(); break;
            case M_HOME: home(); break;
            case M_LOCK: lock(); break;
            case M_WALL: wall(); break;
            case M_SOUND: sound(); break;
            case M_QUICK: quick(); break;
            default: theme(); break;
        }
    }

    private void needA11y() {
        if (a11yEnabled(this)) return;
        ui.section(null);
        ui.navRow("⚠️ 접근성 서비스가 꺼져 있어요", "이 기능은 접근성 서비스가 있어야 동작해요 · 눌러서 켜기", v -> openA11y(this));
    }

    private void needNoti() {
        if (listenerEnabled(this)) return;
        ui.section(null);
        ui.navRow("⚠️ 알림 접근이 꺼져 있어요", "알림을 감지하려면 필요해요 · 눌러서 켜기",
                v -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
    }

    private void needOverlay() {
        if (Settings.canDrawOverlays(this)) return;
        ui.section(null);
        ui.navRow("⚠️ '다른 앱 위에 표시'가 꺼져 있어요", "화면 위에 띄우려면 필요해요 · 눌러서 켜기",
                v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
    }

    private FrameLayout phoneFrame(View inner, int heightDp, int bg) {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(Ui.round(bg, ui.dp(30)));
        f.setClipToOutline(true);
        f.addView(inner, Ui.match());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.dp(heightDp * 0.5f), ui.dp(heightDp));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = ui.dp(12);
        ui.root.addView(f, lp);
        return f;
    }

    // -- Edge Lighting+
    private EdgeGlowView glow;

    private void updateGlow() {
        if (glow == null) return;
        glow.configure(Prefs.i(this, Prefs.EL_EFFECT, 0), Prefs.i(this, Prefs.EL_WIDTH, 6), EdgeLighting.colorsFor(this, getApplicationInfo().loadIcon(getPackageManager())));
        glow.loop();
    }

    private void edgeLight() {
        ui.header("엣지 라이팅", "알림이 오면 화면 가장자리가 빛나요 (Edge Lighting+)");
        needNoti();
        if (!Overlay.available(this)) needOverlay();
        glow = new EdgeGlowView(this);
        FrameLayout f = phoneFrame(glow, 240, 0xFF111118);
        TextView sample = ui.text("💬 새 메시지", 12, 0xFFFFFFFF, false);
        sample.setGravity(Gravity.CENTER);
        f.addView(sample, new FrameLayout.LayoutParams(-1, -1));
        ui.section("기본");
        ui.switchRow("엣지 라이팅 사용", null, Prefs.EL_ON, false, apply);
        ui.switchRow("화면이 꺼져 있을 때도 켜서 보여주기", "배터리를 조금 더 써요", Prefs.EL_WAKE, false, null);
        ui.section("모양");
        ui.choiceRow("효과", Prefs.EL_EFFECT, EdgeGlowView.EFFECTS, 0, this::updateGlow);
        ui.choiceRow("색", Prefs.EL_STYLE, EdgeGlowView.STYLES, 0, this::updateGlow);
        ui.colorRow("선택한 색", Prefs.EL_COLOR, Prefs.accent(this), this::updateGlow);
        ui.sliderRow("두께", Prefs.EL_WIDTH, 2, 20, 6, "dp", this::updateGlow);
        ui.sliderRow("표시 시간", Prefs.EL_DUR, 1, 12, 4, "초", null);
        ui.button("실제 화면에서 테스트", v -> {
            if (!Overlay.available(this)) { Actions.toast(this, "접근성 서비스나 '다른 앱 위에 표시'를 먼저 켜 주세요"); return; }
            boolean was = Prefs.b(this, Prefs.EL_ON, false);
            Prefs.put(this, Prefs.EL_ON, true);
            EdgeLighting.show(this, getApplicationInfo().loadIcon(getPackageManager()));
            Prefs.put(this, Prefs.EL_ON, was);
        });
        updateGlow();
    }

    // -- One Hand Operation+ / Edge Touch / Edge Panel
    private void gesture() {
        ui.header("엣지 제스처·패널", "화면 가장자리의 핸들을 쓸어서 동작해요 (One Hand Operation+ · Edge panel)");
        needA11y();
        ui.section("핸들");
        ui.switchRow("엣지 핸들 사용", null, Prefs.EH_ON, false, apply);
        ui.choiceRow("위치", Prefs.EH_SIDE, new String[]{"오른쪽", "왼쪽", "양쪽"}, 0, apply);
        ui.sliderRow("세로 위치", Prefs.EH_POS, 10, 90, 40, "%", apply);
        ui.sliderRow("길이", Prefs.EH_LEN, 80, 420, 160, "dp", apply);
        ui.sliderRow("터치 폭", Prefs.EH_WIDTH, 8, 40, 14, "dp", apply);
        ui.switchRow("핸들 보이기", "끄면 투명하지만 계속 동작해요", Prefs.EH_VISIBLE, true, apply);
        ui.switchRow("진동", null, Prefs.EH_VIB, true, null);
        ui.section("동작 (핸들에서 안쪽으로 쓸기)");
        ui.choiceRow("→ 안쪽으로 쓸기", Prefs.ACT_IN, Actions.NAMES, Actions.PANEL, null);
        ui.choiceRow("↗ 위로 비스듬히", Prefs.ACT_UP, Actions.NAMES, Actions.HOME, null);
        ui.choiceRow("↘ 아래로 비스듬히", Prefs.ACT_DOWN, Actions.NAMES, Actions.BACK, null);
        ui.choiceRow("● 길게 누르기", Prefs.ACT_LONG, Actions.NAMES, Actions.RECENTS, null);
        ui.section("엣지 패널");
        ui.navRow("패널 앱 고르기", Prefs.list(this, Prefs.PANEL_APPS).size() + "개 선택됨 · 최대 15개", v ->
                startActivity(new Intent(this, AppPickerActivity.class)
                        .putExtra(AppPickerActivity.EXTRA_KEY, Prefs.PANEL_APPS)
                        .putExtra(AppPickerActivity.EXTRA_TITLE, "엣지 패널 앱 선택")
                        .putExtra(AppPickerActivity.EXTRA_MAX, 15)));
        ui.info("패널에는 손전등·캡처·잠금·볼륨·알림기록·화면 꺼짐 방지·분할 화면·빠른 설정 도구가 함께 들어가요.");
        ui.button("패널 열어 보기", v -> Actions.run(this, Actions.PANEL));
    }

    // -- NavStar
    private void nav() {
        ui.header("내비게이션 바", "화면 아래에 떠 있는 나만의 버튼 바 (NavStar)");
        needA11y();
        ui.section(null);
        ui.switchRow("떠 있는 내비게이션 바 사용", null, Prefs.NAV_ON, false, apply);
        ui.choiceRow("버튼 모양", Prefs.NAV_STYLE, DecoAccessibility.NavGlyph.STYLES, 0, apply);
        ui.switchRow("버튼 순서 바꾸기", "최근 앱 · 홈 · 뒤로", Prefs.NAV_REVERSE, false, apply);
        ui.sliderRow("높이", Prefs.NAV_HEIGHT, 28, 72, 44, "dp", apply);
        ui.sliderRow("배경 진하기", Prefs.NAV_ALPHA, 0, 100, 70, "%", apply);
        ui.info("길게 누르기: 홈 = 엣지 패널, 뒤로 = 이전 앱, 최근 앱 = 분할 화면.\n시스템 내비게이션 바는 일반 앱이 숨길 수 없어요. 설정 > 디스플레이(또는 시스템) > 내비게이션에서 '제스처 탐색'으로 바꾸면 이 바만 보여요.");
    }

    // -- Home Up
    private void home() {
        ui.header("홈 화면", "가볍고 꾸미기 쉬운 런처 (Home Up)");
        ui.section(null);
        Boolean def = moduleOn(this, M_HOME);
        ui.navRow(def != null && def ? "✅ 기본 홈 앱으로 사용 중" : "기본 홈 앱으로 설정", "홈 버튼을 누르면 이 화면이 나와요", v -> {
            requestDefaultHome(this);
        });
        ui.navRow("홈 화면 미리 보기", null, v -> startActivity(new Intent(this, HomeActivity.class)));
        ui.section("앱");
        ui.navRow("홈 화면 앱 고르기", Prefs.list(this, Prefs.HOME_APPS).size() + "개", v -> pick(Prefs.HOME_APPS, "홈 화면 앱 선택", 36));
        ui.navRow("독 앱 고르기", Prefs.list(this, Prefs.DOCK_APPS).size() + " / 5개", v -> pick(Prefs.DOCK_APPS, "독 앱 선택", 5));
        ui.section("모양");
        ui.choiceRow("아이콘 모양", Prefs.HOME_SHAPE, Apps.SHAPES, 2, null);
        ui.sliderRow("아이콘 크기", Prefs.HOME_ICON, 40, 72, 54, "dp", null);
        ui.sliderRow("가로 칸 수", Prefs.HOME_COLS, 3, 6, 4, "칸", null);
        ui.switchRow("앱 이름 표시", null, Prefs.HOME_LABELS, true, null);
        ui.switchRow("밝은 배경용 검은 글씨", null, Prefs.HOME_LABEL_DARK, false, null);
        ui.switchRow("시계·날짜 표시", null, Prefs.HOME_CLOCK, true, null);
        ui.sliderRow("배경화면 어둡게", Prefs.HOME_DIM, 0, 80, 15, "%", null);
        ui.section("제스처");
        ui.switchRow("두 번 탭해서 화면 끄기", "접근성 서비스 필요", Prefs.HOME_DBL_LOCK, true, null);
        ui.info("위로 쓸기: 전체 앱 · 아래로 쓸기: 알림창 · 빈 곳 길게 누르기: 편집 메뉴 · 앱 길게 누르기: 홈/독/패널 추가, 삭제");
    }

    private void pick(String key, String title, int max) {
        startActivity(new Intent(this, AppPickerActivity.class)
                .putExtra(AppPickerActivity.EXTRA_KEY, key)
                .putExtra(AppPickerActivity.EXTRA_TITLE, title)
                .putExtra(AppPickerActivity.EXTRA_MAX, max));
    }

    // -- LockStar / ClockFace
    private ClockView clockPrev;

    private void lock() {
        ui.header("잠금 화면·시계", "화면을 켜면 먼저 보이는 꾸민 잠금 화면 (LockStar · ClockFace)");
        needOverlay();
        clockPrev = new ClockView(this);
        clockPrev.setStyle(Prefs.i(this, Prefs.LOCK_STYLE, 0), Prefs.b(this, Prefs.LOCK_24H, ClockView.is24(this)));
        FrameLayout f = new FrameLayout(this);
        f.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{ui.accent, 0xFF0B0B14}));
        ((android.graphics.drawable.GradientDrawable) f.getBackground()).setCornerRadius(ui.dp(24));
        f.setPadding(ui.dp(10), ui.dp(16), ui.dp(10), ui.dp(16));
        f.addView(clockPrev, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, ui.dp(240));
        lp.topMargin = ui.dp(12);
        ui.root.addView(f, lp);
        Runnable upd = () -> clockPrev.setStyle(Prefs.i(this, Prefs.LOCK_STYLE, 0), Prefs.b(this, Prefs.LOCK_24H, ClockView.is24(this)));
        ui.section(null);
        ui.switchRow("꾸민 잠금 화면 사용", "PIN·지문 잠금은 그대로 유지돼요", Prefs.LOCK_ON, false, apply);
        ui.section("시계");
        ui.choiceRow("시계 스타일", Prefs.LOCK_STYLE, ClockView.STYLES, 0, upd);
        ui.switchRow("24시간 표시", null, Prefs.LOCK_24H, ClockView.is24(this), upd);
        ui.section("화면");
        ui.choiceRow("배경", Prefs.LOCK_BG, LockActivity.BGS, 0, null);
        ui.switchRow("알림 온 앱 아이콘 표시", "알림 접근 필요", Prefs.LOCK_NOTI, true, null);
        ui.switchRow("손전등·카메라 바로가기", null, Prefs.LOCK_SHORTCUTS, true, null);
        ui.button("잠금 화면 미리 보기", v -> startActivity(new Intent(this, LockActivity.class).putExtra(LockActivity.EXTRA_PREVIEW, true)));
        ui.info("홈 화면 시계·날짜 위젯은 '테마·위젯'에 있어요. 시스템 잠금 화면 자체의 시계는 삼성 외 폰에서 앱이 바꿀 수 없어서, 그 앞에 꾸민 화면을 한 장 띄우는 방식이에요.");
    }

    // -- Wonderland
    private void wall() {
        ui.header("움직이는 배경화면", "폰을 기울이면 층마다 다르게 움직여요 (Wonderland)");
        final ParallaxPreview pv = new ParallaxPreview(this);
        phoneFrame(pv, 300, 0xFF000000);
        ui.section(null);
        ui.choiceRow("장면", Prefs.LW_STYLE, ParallaxWallpaper.SCENES, 0, pv::reset);
        ui.sliderRow("움직임 크기", Prefs.LW_MOTION, 0, 100, 60, "%", null);
        ui.button("배경화면으로 설정", v -> {
            Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, new ComponentName(this, ParallaxWallpaper.class));
            try { startActivity(i); } catch (RuntimeException e) {
                try { startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)); } catch (RuntimeException ignored) {}
            }
        });
        ui.section("정지 배경화면");
        ui.navRow("배경화면 만들기", "그라데이션·패턴·도트·웨이브 생성 후 바로 적용", v -> startActivity(new Intent(this, WallpaperMakerActivity.class)));
    }

    static class ParallaxPreview extends View {
        ParallaxWallpaper.Painter painter;
        ParallaxPreview(Context c) { super(c); reset(); }
        void reset() { painter = new ParallaxWallpaper.Painter(Prefs.i(getContext(), Prefs.LW_STYLE, 0), Prefs.accent(getContext())); invalidate(); }
        @Override protected void onDraw(Canvas c) {
            double t = System.currentTimeMillis() / 1600.0;
            float motion = Prefs.i(getContext(), Prefs.LW_MOTION, 60) / 100f;
            painter.draw(c, getWidth(), getHeight(), (float) Math.sin(t), (float) Math.cos(t * 0.7) * 0.5f, getWidth() * 0.08f * motion);
            postInvalidateOnAnimation();
        }
    }

    // -- Sound Assistant
    private void sound() {
        ui.header("사운드", "볼륨을 더 편하게 (Sound Assistant)");
        needA11y();
        ui.section(null);
        ui.switchRow("볼륨 키로 항상 미디어 볼륨 조절", "벨소리 대신 음악·영상 소리가 바뀌어요 (통화 중 제외)", Prefs.VOL_MEDIA_KEYS, false, apply);
        ui.navRow("전체 볼륨 패널 열기", "미디어·벨소리·알림·알람·통화·시스템 + 소리/진동/무음", v -> startActivity(new Intent(this, VolumeActivity.class)));
        ui.info("앱마다 따로 볼륨을 정하는 기능은 삼성 시스템 권한이 필요해서 넣을 수 없어요. 엣지 제스처 동작에 '볼륨 패널'을 지정해 두면 한 번에 열 수 있어요.");
    }

    // -- QuickStar
    private void quick() {
        ui.header("빠른 설정 타일", "알림창을 내려 쓰는 빠른 설정에 버튼 추가 (QuickStar)");
        ui.section("타일");
        tile("손전등", Tiles.Flash.class, R.drawable.ic_flash);
        tile("화면 꺼짐 방지", Tiles.Caffeine.class, R.drawable.ic_coffee);
        tile("화면 잠금", Tiles.Lock.class, R.drawable.ic_lock);
        tile("엣지 라이팅 켜기/끄기", Tiles.EdgeLight.class, R.drawable.ic_edge);
        tile("엣지 패널 열기", Tiles.Panel.class, R.drawable.ic_panel);
        ui.info(Build.VERSION.SDK_INT >= 33
                ? "항목을 누르면 바로 추가할 수 있어요."
                : "알림창을 두 번 내려 연필(편집) 버튼을 누른 뒤, 아래쪽 '폰 꾸미기' 타일을 위로 끌어 올려 주세요.");
        ui.info("상태바 아이콘·알림창 색 자체를 바꾸는 기능은 삼성 전용이라 넣을 수 없어요.");
    }

    private void tile(final String label, final Class<?> cls, final int icon) {
        ui.navRow(label, Build.VERSION.SDK_INT >= 33 ? "눌러서 빠른 설정에 추가" : null, v -> {
            if (Build.VERSION.SDK_INT >= 33) {
                StatusBarManager sbm = getSystemService(StatusBarManager.class);
                sbm.requestAddTileService(new ComponentName(this, cls), label, Icon.createWithResource(this, icon),
                        getMainExecutor(), r -> {});
            } else {
                Actions.toast(this, "알림창 편집에서 추가해 주세요");
            }
        });
    }

    // -- Theme Park
    private void theme() {
        ui.header("테마·위젯", "앱 전체 색과 홈 화면 위젯 (Theme Park)");
        ui.section("테마색");
        ui.colorRow("테마색", Prefs.ACCENT, Prefs.DEF_ACCENT, () -> { refreshAll(this); recreate(); });
        ui.info("테마색은 이 앱 화면, 홈 화면, 엣지 핸들·패널, 잠금 화면 시계, 위젯, 움직이는 배경화면에 함께 적용돼요.");
        ui.section("시계·날짜 위젯");
        ui.choiceRow("위젯 스타일", Prefs.WG_STYLE, ClockWidget.STYLES, 0, apply);
        ui.sliderRow("시계 글자 크기", Prefs.WG_SIZE, 32, 96, 56, "sp", apply);
        ui.navRow("홈 화면에 위젯 추가", "지원하는 홈 앱에서 바로 추가돼요", v -> {
            AppWidgetManager m = AppWidgetManager.getInstance(this);
            if (m.isRequestPinAppWidgetSupported()) m.requestPinAppWidget(new ComponentName(this, ClockWidget.class), null, null);
            else Actions.toast(this, "홈 화면을 길게 눌러 위젯 > 폰 꾸미기에서 추가해 주세요");
        });
        ui.section("배경화면");
        ui.navRow("배경화면 만들기", null, v -> startActivity(new Intent(this, WallpaperMakerActivity.class)));
        ui.navRow("움직이는 배경화면", null, v -> startActivity(new Intent(this, SettingsActivity.class).putExtra(EXTRA_MODULE, M_WALL)));
        ui.section("아이콘");
        ui.choiceRow("아이콘 모양 (홈 화면·패널)", Prefs.HOME_SHAPE, Apps.SHAPES, 2, apply);
        ui.info("시스템 전체 테마·아이콘 팩 교체는 삼성 테마 엔진이 있어야 해서, 이 앱의 홈 화면과 패널에 적용돼요.");
    }
}
