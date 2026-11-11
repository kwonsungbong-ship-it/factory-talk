package kr.sbkwon.decolock;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;

/** Wallpaper generator: gradients, mesh blobs, patterns, Wonderland stills. */
public class WallpaperMakerActivity extends Activity {
    private static final String[] KINDS = {"메시 그라데이션", "선형 그라데이션", "기하학 패턴", "물방울 도트", "웨이브 라인", "Wonderland 장면"};

    private int kind = 0;
    private long seed = System.nanoTime();
    private Bitmap bmp;
    private ImageView preview;
    private TextView kindLabel;
    private int W, H;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        W = Math.min(dm.widthPixels, 1440);
        H = Math.round(W * (float) dm.heightPixels / dm.widthPixels);

        Ui ui = new Ui(this);
        ui.header("배경화면 만들기", "버튼 한 번으로 나만의 배경화면을 만들어 바로 적용해요");

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setClipToOutline(true);
        preview.setBackground(Ui.round(0xFFDDDDDD, ui.dp(24)));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ui.dp(220), ui.dp(220 * H / (float) W));
        plp.gravity = Gravity.CENTER_HORIZONTAL;
        plp.topMargin = ui.dp(12);
        ui.root.addView(preview, plp);

        ui.section("스타일");
        kindLabel = ui.navRow("종류", KINDS[kind], v -> new AlertDialog.Builder(this).setTitle("종류")
                .setSingleChoiceItems(KINDS, kind, (d, w) -> { kind = w; d.dismiss(); render(); }).show())
                .findViewWithTag("desc");
        ui.navRow("🎲 다시 만들기", "색과 모양을 무작위로 바꿔요", v -> { seed = System.nanoTime(); render(); });

        ui.button("홈 화면에 적용", v -> apply(WallpaperManager.FLAG_SYSTEM));
        ui.button("잠금 화면에 적용", v -> apply(WallpaperManager.FLAG_LOCK));
        ui.button("둘 다 적용", v -> apply(WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK));
        ui.button("움직이는 배경화면(Wonderland) 설정", v -> {
            Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, new ComponentName(this, ParallaxWallpaper.class));
            try { startActivity(i); } catch (RuntimeException e) {
                try { startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)); } catch (RuntimeException ignored) {}
            }
        });
        render();
    }

    private void render() {
        bmp = generate(kind, seed, W, H, Prefs.accent(this));
        preview.setImageBitmap(bmp);
        if (kindLabel != null) kindLabel.setText(KINDS[kind]);
    }

    private void apply(final int which) {
        final Bitmap b = bmp;
        new Thread(() -> {
            try {
                WallpaperManager.getInstance(this).setBitmap(b, null, true, which);
                Actions.toast(this, "배경화면을 적용했어요");
            } catch (Exception e) {
                Actions.toast(this, "배경화면을 바꾸지 못했어요");
            }
        }).start();
    }

    static int hsv(float h, float s, float v) { return Color.HSVToColor(new float[]{(h % 360 + 360) % 360, s, v}); }

    public static Bitmap generate(int kind, long seed, int w, int h, int accent) {
        Random r = new Random(seed);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float[] a = new float[3];
        Color.colorToHSV(accent, a);
        float base = r.nextBoolean() ? a[0] : r.nextFloat() * 360;
        int c1 = hsv(base, 0.55f + r.nextFloat() * 0.35f, 0.85f + r.nextFloat() * 0.15f);
        int c2 = hsv(base + 40 + r.nextFloat() * 100, 0.5f + r.nextFloat() * 0.4f, 0.75f + r.nextFloat() * 0.25f);
        int c3 = hsv(base - 50 - r.nextFloat() * 80, 0.45f + r.nextFloat() * 0.4f, 0.6f + r.nextFloat() * 0.35f);
        int dark = hsv(base + 200, 0.6f, 0.12f);
        switch (kind) {
            case 0: {
                c.drawColor(c3);
                int[] cols = {c1, c2, c3, hsv(base + 180, 0.4f, 0.95f), c1};
                for (int i = 0; i < 6; i++) {
                    float x = r.nextFloat() * w, y = r.nextFloat() * h, rad = w * (0.5f + r.nextFloat() * 0.6f);
                    int col = cols[i % cols.length];
                    p.setShader(new RadialGradient(x, y, rad, (col & 0x00FFFFFF) | 0xCC000000, col & 0x00FFFFFF, Shader.TileMode.CLAMP));
                    c.drawCircle(x, y, rad, p);
                }
                break;
            }
            case 1: {
                float ang = r.nextFloat() * (float) Math.PI;
                float dx = (float) Math.cos(ang) * h, dy = (float) Math.sin(ang) * h;
                p.setShader(new LinearGradient(w / 2f - dx / 2, h / 2f - dy / 2, w / 2f + dx / 2, h / 2f + dy / 2,
                        new int[]{c1, c2, c3}, null, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
                break;
            }
            case 2: {
                c.drawColor(dark);
                float cell = w / (4f + r.nextInt(4));
                int[] cols = {c1, c2, c3};
                for (float y = 0; y < h; y += cell) for (float x = 0; x < w; x += cell) {
                    p.setColor(cols[r.nextInt(3)]);
                    p.setAlpha(150 + r.nextInt(105));
                    Path path = new Path();
                    switch (r.nextInt(4)) {
                        case 0: c.drawCircle(x + cell / 2, y + cell / 2, cell * 0.42f, p); break;
                        case 1: path.moveTo(x, y); path.lineTo(x + cell, y); path.lineTo(x, y + cell); path.close(); c.drawPath(path, p); break;
                        case 2: c.drawArc(x - cell, y - cell, x + cell, y + cell, 0, 90, true, p); break;
                        default: c.drawRoundRect(x + cell * 0.12f, y + cell * 0.12f, x + cell * 0.88f, y + cell * 0.88f, cell * 0.2f, cell * 0.2f, p);
                    }
                }
                break;
            }
            case 3: {
                p.setShader(new LinearGradient(0, 0, 0, h, c1, c2, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
                p.setShader(null);
                for (int i = 0; i < 70; i++) {
                    p.setColor(Color.argb(30 + r.nextInt(80), 255, 255, 255));
                    c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, w * (0.01f + r.nextFloat() * 0.08f), p);
                }
                break;
            }
            case 4: {
                c.drawColor(dark);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(w * 0.006f);
                int lines = 26;
                float ph = r.nextFloat() * 10, fr = 2 + r.nextFloat() * 3;
                for (int i = 0; i < lines; i++) {
                    float f = i / (float) lines;
                    p.setShader(new LinearGradient(0, 0, w, 0, c1, c2, Shader.TileMode.CLAMP));
                    p.setAlpha((int) (80 + 175 * f));
                    Path path = new Path();
                    for (int k = 0; k <= 60; k++) {
                        float x = w * k / 60f;
                        float y = h * (0.2f + 0.6f * f) + h * 0.06f * (float) Math.sin(x / w * fr * Math.PI + ph + f * 3);
                        if (k == 0) path.moveTo(x, y); else path.lineTo(x, y);
                    }
                    c.drawPath(path, p);
                }
                break;
            }
            default: {
                ParallaxWallpaper.Painter painter = new ParallaxWallpaper.Painter(r.nextInt(ParallaxWallpaper.SCENES.length), c1);
                painter.draw(c, w, h, 0, 0, 0);
            }
        }
        return b;
    }
}
