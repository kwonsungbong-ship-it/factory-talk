package kr.sbkwon.decolock;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Hub screen: permission checklist + module cards (like the Good Lock home). */
public class MainActivity extends Activity {
    private Ui ui;

    static final String[][] MODULES = {
            // id, emoji, title, good lock name, desc
            {SettingsActivity.M_EDGE_LIGHT, "🌈", "엣지 라이팅", "Edge Lighting+", "알림이 오면 화면 테두리가 빛나요"},
            {SettingsActivity.M_GESTURE, "👆", "엣지 제스처·패널", "One Hand Op+ · Edge Touch", "가장자리 스와이프로 뒤로·홈·패널"},
            {SettingsActivity.M_HOME, "🏠", "홈 화면", "Home Up", "아이콘 모양·격자·독·서랍"},
            {SettingsActivity.M_LOCK, "🔒", "잠금 화면·시계", "LockStar · ClockFace", "시계 스타일·배경·바로가기"},
            {SettingsActivity.M_WALL, "🏔", "움직이는 배경화면", "Wonderland", "폰을 기울이면 움직이는 배경"},
            {SettingsActivity.M_NAV, "◁○□", "내비게이션 바", "NavStar", "버튼 모양·순서·투명도"},
            {SettingsActivity.M_HISTORY, "🔔", "알림 기록", "NotiStar · Nice Catch", "지워진 알림까지 검색"},
            {SettingsActivity.M_SOUND, "🔊", "사운드", "Sound Assistant", "볼륨 패널·볼륨키=미디어"},
            {SettingsActivity.M_QUICK, "⚡", "빠른 설정 타일", "QuickStar", "손전등·잠금·꺼짐방지 타일"},
            {SettingsActivity.M_THEME, "🎨", "테마·위젯", "Theme Park", "테마색·시계 위젯·배경 생성"},
    };

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        ui = new Ui(this);
        ui.header("폰 꾸미기", "Good Lock 스타일 꾸미기 · 삼성 아닌 폰에서도 동작");

        boolean a11y = SettingsActivity.a11yEnabled(this);
        boolean noti = SettingsActivity.listenerEnabled(this);
        boolean overlay = Settings.canDrawOverlays(this);
        if (!a11y || !noti || !overlay) {
            ui.section("먼저 권한을 켜 주세요");
            perm("접근성 서비스", "제스처·패널·내비바·화면 잠금", a11y, v -> SettingsActivity.openA11y(this));
            perm("알림 접근", "엣지 라이팅·알림 기록", noti, v -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
            perm("다른 앱 위에 표시", "잠금 화면·엣지 라이팅", overlay, v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
            if (Build.VERSION.SDK_INT >= 33) {
                ui.info("안드로이드 13 이상에서 접근성/알림 접근이 회색으로 막혀 있으면: 설정 > 애플리케이션 > 폰 꾸미기 > 오른쪽 위 ⋮ > '제한된 설정 허용'을 누른 뒤 다시 켜 주세요.");
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        TextView t = ui.text("꾸미기 모듈", 13, ui.accent, true);
        t.setPadding(ui.dp(8), ui.dp(18), 0, ui.dp(6));
        ui.root.addView(t);

        LinearLayout row = null;
        for (int i = 0; i < MODULES.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                ui.root.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            row.addView(card(MODULES[i]), cardLp(i % 2 == 0));
        }

        ui.section("모듈로 만들 수 없는 기능");
        ui.info("분할 화면 꾸미기(MultiStar), 카메라·디스플레이 어시스턴트, S펜(Pentastic), 시스템 알림창·상태바 디자인 변경, 앱별 볼륨은 삼성 시스템 권한이 있어야 해서 일반 안드로이드 앱으로는 만들 수 없어요. 분할 화면 전환·빠른 설정 열기는 제스처 동작으로 넣었어요.");
    }

    private LinearLayout.LayoutParams cardLp(boolean left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ui.dp(150), 1);
        lp.setMargins(left ? 0 : ui.dp(6), ui.dp(6), left ? ui.dp(6) : 0, ui.dp(6));
        return lp;
    }

    private LinearLayout card(final String[] m) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(ui.dp(16), ui.dp(14), ui.dp(14), ui.dp(14));
        Ui.ripple(c, Ui.CARD, ui.dp(22));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView em = ui.text(m[1], 24, ui.accent, true);
        top.addView(em, new LinearLayout.LayoutParams(0, -2, 1));
        Boolean on = SettingsActivity.moduleOn(this, m[0]);
        if (on != null) {
            TextView st = ui.text(on ? "켜짐" : "꺼짐", 11, on ? 0xFFFFFFFF : Ui.SUB, true);
            st.setPadding(ui.dp(8), ui.dp(3), ui.dp(8), ui.dp(3));
            st.setBackground(Ui.round(on ? ui.accent : 0xFFEDEEF3, ui.dp(10)));
            top.addView(st);
        }
        c.addView(top);
        TextView title = ui.text(m[2], 16, Ui.TEXT, true);
        title.setPadding(0, ui.dp(10), 0, 0);
        c.addView(title);
        c.addView(ui.text(m[3], 11, ui.accent, false));
        TextView d = ui.text(m[4], 12, Ui.SUB, false);
        d.setPadding(0, ui.dp(4), 0, 0);
        c.addView(d);
        c.setOnClickListener(v -> {
            if (SettingsActivity.M_HISTORY.equals(m[0])) startActivity(new Intent(this, HistoryActivity.class));
            else startActivity(new Intent(this, SettingsActivity.class).putExtra(SettingsActivity.EXTRA_MODULE, m[0]));
        });
        return c;
    }

    private void perm(String label, String desc, boolean ok, android.view.View.OnClickListener l) {
        LinearLayout r = ui.navRow((ok ? "✅ " : "⚠️ ") + label, ok ? "허용됨" : desc + " · 눌러서 켜기", l);
        if (ok) r.setOnClickListener(null);
    }
}
