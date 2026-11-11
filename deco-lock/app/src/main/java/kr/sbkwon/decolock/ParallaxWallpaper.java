package kr.sbkwon.decolock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.util.Random;

/** Wonderland: layered live wallpaper that moves as the phone tilts. */
public class ParallaxWallpaper extends WallpaperService {
    public static final String[] SCENES = {"산과 노을", "떠오르는 버블", "밤하늘 별", "물결", "오로라"};

    @Override
    public Engine onCreateEngine() { return new Scene(); }

    /** Draws one frame of a scene; shared with the in-app preview. */
    public static final class Painter {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float[][] stars = new float[160][4];
        final float[][] bubbles = new float[40][5];
        int scene, accent;
        long start = System.currentTimeMillis();

        public Painter(int scene, int accent) {
            this.scene = scene;
            this.accent = accent;
            Random r = new Random(7);
            for (float[] s : stars) { s[0] = r.nextFloat(); s[1] = r.nextFloat() * 0.8f; s[2] = 0.5f + r.nextFloat() * 1.8f; s[3] = r.nextInt(3); }
            for (float[] b : bubbles) { b[0] = r.nextFloat(); b[1] = r.nextFloat(); b[2] = 0.02f + r.nextFloat() * 0.09f; b[3] = r.nextInt(3); b[4] = 0.2f + r.nextFloat(); }
        }

        static int mix(int a, int b, float f) {
            return Color.rgb((int) (Color.red(a) * (1 - f) + Color.red(b) * f),
                    (int) (Color.green(a) * (1 - f) + Color.green(b) * f),
                    (int) (Color.blue(a) * (1 - f) + Color.blue(b) * f));
        }

        /** tx, ty in -1..1 tilt; depth layers shift by up to maxShift px. */
        public void draw(Canvas c, int w, int h, float tx, float ty, float maxShift) {
            float t = (System.currentTimeMillis() - start) / 1000f;
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            switch (scene) {
                case 0: mountains(c, w, h, tx, ty, maxShift); break;
                case 1: bubbles(c, w, h, tx, ty, maxShift, t); break;
                case 2: night(c, w, h, tx, ty, maxShift, t); break;
                case 3: waves(c, w, h, tx, ty, maxShift, t); break;
                default: aurora(c, w, h, tx, ty, maxShift, t); break;
            }
        }

        private void sky(Canvas c, int w, int h, int top, int bottom) {
            p.setShader(new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
        }

        private void mountains(Canvas c, int w, int h, float tx, float ty, float s) {
            sky(c, w, h, mix(accent, 0xFF1A1033, 0.55f), mix(accent, 0xFFFFC37A, 0.55f));
            float sx = w * 0.7f - tx * s * 0.2f, sy = h * 0.42f - ty * s * 0.2f;
            p.setShader(new RadialGradient(sx, sy, w * 0.5f, 0x66FFE9B0, 0x00FFE9B0, Shader.TileMode.CLAMP));
            c.drawCircle(sx, sy, w * 0.5f, p);
            p.setShader(null);
            p.setColor(0xFFFFF1D0);
            c.drawCircle(sx, sy, w * 0.09f, p);
            int[] cols = {mix(accent, 0xFF6B4E8A, 0.5f), mix(accent, 0xFF3B2A5A, 0.6f), mix(accent, 0xFF22163A, 0.7f), 0xFF120A20};
            float[] base = {0.55f, 0.64f, 0.74f, 0.85f};
            float[] amp = {0.16f, 0.13f, 0.11f, 0.08f};
            for (int L = 0; L < 4; L++) {
                float depth = (L + 1) / 4f;
                float ox = -tx * s * depth, oy = -ty * s * depth * 0.6f;
                Path path = new Path();
                path.moveTo(-s, h + s);
                for (int i = 0; i <= 40; i++) {
                    float x = -s + (w + 2 * s) * i / 40f;
                    double nx = (x / w) * (3 + L) + L * 1.7;
                    float y = h * base[L] - h * amp[L] * (float) (0.55 * Math.sin(nx * 2.1) + 0.3 * Math.sin(nx * 4.3 + 1) + 0.15 * Math.sin(nx * 9.7));
                    path.lineTo(x + ox, y + oy);
                }
                path.lineTo(w + s, h + s);
                path.close();
                p.setColor(cols[L]);
                c.drawPath(path, p);
            }
        }

        private void bubbles(Canvas c, int w, int h, float tx, float ty, float s, float t) {
            sky(c, w, h, mix(accent, 0xFF0B1E3A, 0.4f), mix(accent, 0xFF02060F, 0.75f));
            for (int layer = 0; layer < 3; layer++) {
                float depth = (layer + 1) / 3f;
                for (float[] b : bubbles) {
                    if ((int) b[3] != layer) continue;
                    float y = ((b[1] - t * 0.015f * b[4] * depth) % 1f + 1f) % 1f;
                    float x = b[0] * w + (float) Math.sin(t * 0.5 + b[1] * 10) * w * 0.02f - tx * s * depth;
                    float yy = y * (h + 200) - 100 - ty * s * depth;
                    float r = b[2] * w * (0.5f + depth * 0.6f);
                    p.setShader(new RadialGradient(x - r * 0.3f, yy - r * 0.3f, r * 1.2f,
                            new int[]{0x55FFFFFF, (mix(accent, 0xFFFFFFFF, 0.3f) & 0x00FFFFFF) | ((int) (60 * depth) << 24), 0x00FFFFFF},
                            new float[]{0f, 0.7f, 1f}, Shader.TileMode.CLAMP));
                    c.drawCircle(x, yy, r, p);
                }
            }
            p.setShader(null);
        }

        private void night(Canvas c, int w, int h, float tx, float ty, float s, float t) {
            sky(c, w, h, 0xFF02030A, mix(accent, 0xFF0A0F2A, 0.7f));
            for (float[] st : stars) {
                float depth = (st[3] + 1) / 3f;
                float tw = 0.6f + 0.4f * (float) Math.sin(t * 2 + st[0] * 50);
                p.setColor(Color.argb((int) (255 * tw), 255, 255, 255));
                c.drawCircle(st[0] * w - tx * s * depth * 0.6f, st[1] * h - ty * s * depth * 0.6f, st[2] * depth * 1.6f, p);
            }
            float mx = w * 0.3f - tx * s * 0.3f, my = h * 0.22f - ty * s * 0.3f;
            p.setShader(new RadialGradient(mx, my, w * 0.3f, 0x44FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(mx, my, w * 0.3f, p);
            p.setShader(null);
            p.setColor(0xFFF5F3E6);
            c.drawCircle(mx, my, w * 0.08f, p);
            p.setColor(0xFF02030A & 0xE6FFFFFF);
            p.setColor(mix(accent, 0xFF02030A, 0.75f));
            c.drawCircle(mx + w * 0.035f, my - w * 0.02f, w * 0.07f, p);
            p.setColor(0xFF05060C);
            Path hill = new Path();
            hill.moveTo(-s, h);
            for (int i = 0; i <= 30; i++) {
                float x = -s + (w + 2 * s) * i / 30f;
                hill.lineTo(x - tx * s, h * 0.86f - h * 0.05f * (float) Math.sin(x / w * 5) - ty * s * 0.5f);
            }
            hill.lineTo(w + s, h + s);
            hill.close();
            c.drawPath(hill, p);
        }

        private void waves(Canvas c, int w, int h, float tx, float ty, float s, float t) {
            sky(c, w, h, mix(accent, 0xFFFFFFFF, 0.55f), mix(accent, 0xFFFFE0C0, 0.4f));
            for (int L = 0; L < 5; L++) {
                float depth = (L + 1) / 5f;
                Path path = new Path();
                float base = h * (0.45f + L * 0.11f) - ty * s * depth;
                path.moveTo(-s, h + s);
                for (int i = 0; i <= 50; i++) {
                    float x = -s + (w + 2 * s) * i / 50f;
                    float y = base + h * 0.025f * (float) Math.sin(x / w * (4 + L) + t * (0.6 + L * 0.25) + L);
                    path.lineTo(x - tx * s * depth, y);
                }
                path.lineTo(w + s, h + s);
                path.close();
                p.setColor(mix(accent, 0xFF0A1630, 0.15f + L * 0.17f));
                c.drawPath(path, p);
            }
        }

        private void aurora(Canvas c, int w, int h, float tx, float ty, float s, float t) {
            sky(c, w, h, 0xFF050814, 0xFF0B1426);
            int[] cols = {accent, 0xFF00E5A0, 0xFF7C4DFF, 0xFF00B8FF};
            for (int i = 0; i < 4; i++) {
                float depth = (i + 1) / 4f;
                float x = w * (0.2f + 0.6f * (float) (0.5 + 0.5 * Math.sin(t * 0.13 + i * 1.7))) - tx * s * depth;
                float y = h * (0.25f + 0.45f * (float) (0.5 + 0.5 * Math.cos(t * 0.11 + i * 2.3))) - ty * s * depth;
                float r = w * (0.55f + 0.15f * i);
                p.setShader(new RadialGradient(x, y, r, (cols[i] & 0x00FFFFFF) | 0x88000000, cols[i] & 0x00FFFFFF, Shader.TileMode.CLAMP));
                c.drawCircle(x, y, r, p);
            }
            p.setShader(null);
        }
    }

    class Scene extends Engine implements SensorEventListener {
        private final Handler h = new Handler(Looper.getMainLooper());
        private Painter painter;
        private boolean visible;
        private float gx, gy, tx, ty, pageX;
        private SensorManager sm;
        private final Runnable frame = this::drawFrame;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            setOffsetNotificationsEnabled(true);
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            visible = v;
            if (v) {
                Context c = ParallaxWallpaper.this;
                painter = new Painter(Prefs.i(c, Prefs.LW_STYLE, 0), Prefs.accent(c));
                Sensor s = sm.getDefaultSensor(Sensor.TYPE_GRAVITY);
                if (s == null) s = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                if (s != null) sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME);
                drawFrame();
            } else {
                sm.unregisterListener(this);
                h.removeCallbacks(frame);
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            visible = false;
            h.removeCallbacks(frame);
            sm.unregisterListener(this);
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onOffsetsChanged(float xOff, float yOff, float xStep, float yStep, int xPx, int yPx) {
            pageX = (xOff - 0.5f) * 0.6f;
        }

        @Override
        public void onSensorChanged(SensorEvent e) {
            gx = gx * 0.85f + (-e.values[0] / 9.81f) * 0.15f;
            gy = gy * 0.85f + ((e.values[1] / 9.81f) - 0.6f) * 0.15f;
        }

        @Override public void onAccuracyChanged(Sensor s, int a) {}

        private void drawFrame() {
            if (!visible || painter == null) return;
            tx += ((clamp(gx) + pageX) - tx) * 0.12f;
            ty += (clamp(gy) - ty) * 0.12f;
            SurfaceHolder sh = getSurfaceHolder();
            Canvas c = null;
            try {
                c = sh.lockCanvas();
                if (c != null) {
                    float motion = Prefs.i(ParallaxWallpaper.this, Prefs.LW_MOTION, 60) / 100f;
                    painter.draw(c, c.getWidth(), c.getHeight(), tx, ty, c.getWidth() * 0.08f * motion);
                }
            } catch (RuntimeException ignored) {
            } finally {
                if (c != null) try { sh.unlockCanvasAndPost(c); } catch (RuntimeException ignored) {}
            }
            h.removeCallbacks(frame);
            h.postDelayed(frame, 33);
        }

        private float clamp(float v) { return Math.max(-1f, Math.min(1f, v)); }
    }
}
