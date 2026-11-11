package kr.sbkwon.decolock;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/** Every action an edge gesture, panel button, nav bar or tile can trigger. */
public final class Actions {
    private Actions() {}

    public static final int NONE = 0, BACK = 1, HOME = 2, RECENTS = 3, NOTIFICATIONS = 4, QUICK_SETTINGS = 5,
            PANEL = 6, LOCK = 7, SCREENSHOT = 8, POWER = 9, SPLIT = 10, FLASHLIGHT = 11, VOLUME = 12,
            HISTORY = 13, CAFFEINE = 14, LAST_APP = 15;

    public static final String[] NAMES = {
            "없음", "뒤로 가기", "홈", "최근 앱", "알림창 열기", "빠른 설정 열기",
            "엣지 패널", "화면 잠금", "스크린샷", "전원 메뉴", "분할 화면", "손전등",
            "볼륨 패널", "알림 기록", "화면 꺼짐 방지", "이전 앱으로 전환"
    };

    private static boolean torchOn = false;
    private static final Handler H = new Handler(Looper.getMainLooper());

    public static void run(Context c, int action) {
        DecoAccessibility s = DecoAccessibility.instance;
        switch (action) {
            case NONE: return;
            case BACK: global(c, s, AccessibilityService.GLOBAL_ACTION_BACK); return;
            case HOME: global(c, s, AccessibilityService.GLOBAL_ACTION_HOME); return;
            case RECENTS: global(c, s, AccessibilityService.GLOBAL_ACTION_RECENTS); return;
            case NOTIFICATIONS: global(c, s, AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS); return;
            case QUICK_SETTINGS: global(c, s, AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS); return;
            case POWER: global(c, s, AccessibilityService.GLOBAL_ACTION_POWER_DIALOG); return;
            case SPLIT: global(c, s, AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN); return;
            case LAST_APP:
                if (global(c, s, AccessibilityService.GLOBAL_ACTION_RECENTS)) {
                    H.postDelayed(() -> { if (DecoAccessibility.instance != null) DecoAccessibility.instance.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS); }, 350);
                }
                return;
            case LOCK:
                if (Build.VERSION.SDK_INT >= 28) global(c, s, AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
                else toast(c, "화면 잠금은 안드로이드 9 이상에서 지원돼요");
                return;
            case SCREENSHOT:
                if (Build.VERSION.SDK_INT >= 28) {
                    if (s != null) s.closePanel();
                    H.postDelayed(() -> global(c, DecoAccessibility.instance, AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT), 300);
                } else toast(c, "스크린샷은 안드로이드 9 이상에서 지원돼요");
                return;
            case PANEL:
                if (s != null) s.togglePanel(); else needService(c);
                return;
            case FLASHLIGHT: toggleTorch(c); return;
            case VOLUME: start(c, VolumeActivity.class); return;
            case HISTORY: start(c, HistoryActivity.class); return;
            case CAFFEINE:
                boolean on = !Prefs.b(c, Prefs.CAFFEINE, false);
                Prefs.put(c, Prefs.CAFFEINE, on);
                if (s != null) s.applyCaffeine(); else needService(c);
                toast(c, on ? "화면 꺼짐 방지 켜짐" : "화면 꺼짐 방지 꺼짐");
                return;
        }
    }

    private static boolean global(Context c, DecoAccessibility s, int a) {
        if (s == null) { needService(c); return false; }
        return s.performGlobalAction(a);
    }

    private static void needService(Context c) {
        toast(c, "설정 > 접근성에서 '폰 꾸미기' 서비스를 켜 주세요");
    }

    private static void start(Context c, Class<?> cls) {
        DecoAccessibility s = DecoAccessibility.instance;
        if (s != null) s.closePanel();
        Intent i = new Intent(c, cls);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    public static void toast(Context c, String m) {
        H.post(() -> Toast.makeText(c.getApplicationContext(), m, Toast.LENGTH_SHORT).show());
    }

    public static boolean torchOn() { return torchOn; }

    public static void toggleTorch(Context c) { setTorch(c, !torchOn); }

    public static void setTorch(Context c, boolean on) {
        try {
            CameraManager cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                Boolean flash = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flash != null && flash) {
                    cm.setTorchMode(id, on);
                    torchOn = on;
                    return;
                }
            }
            toast(c, "이 폰에는 플래시가 없어요");
        } catch (CameraAccessException | RuntimeException e) {
            toast(c, "손전등을 켤 수 없어요 (카메라 사용 중)");
        }
    }
}
