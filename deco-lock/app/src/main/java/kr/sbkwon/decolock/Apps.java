package kr.sbkwon.decolock;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Launchable-app list with icon shaping, shared by launcher, edge panel and pickers. */
public final class Apps {
    private Apps() {}

    public static final class App {
        public final String key, label, pkg;
        public final ComponentName cn;
        final ResolveInfo ri;
        App(ResolveInfo ri, PackageManager pm) {
            this.ri = ri;
            pkg = ri.activityInfo.packageName;
            cn = new ComponentName(pkg, ri.activityInfo.name);
            key = pkg + "/" + ri.activityInfo.name;
            CharSequence l = ri.loadLabel(pm);
            label = l == null ? pkg : l.toString();
        }
    }

    private static List<App> cache;
    private static final Map<String, Drawable> ICONS = new HashMap<>();
    private static final Map<String, Bitmap> SHAPED = new HashMap<>();

    public static synchronized List<App> all(Context c) {
        if (cache != null) return cache;
        PackageManager pm = c.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(i, 0);
        List<App> out = new ArrayList<>();
        for (ResolveInfo ri : ris) {
            if (ri.activityInfo.packageName.equals(c.getPackageName())
                    && ri.activityInfo.name.endsWith("HomeActivity")) continue;
            out.add(new App(ri, pm));
        }
        final Collator col = Collator.getInstance(Locale.KOREAN);
        Collections.sort(out, (x, y) -> col.compare(x.label, y.label));
        cache = out;
        return out;
    }

    public static synchronized void invalidate() {
        cache = null;
        ICONS.clear();
        SHAPED.clear();
    }

    public static App find(Context c, String key) {
        for (App a : all(c)) if (a.key.equals(key)) return a;
        // tolerate activity renames: match by package
        String pkg = key.contains("/") ? key.substring(0, key.indexOf('/')) : key;
        for (App a : all(c)) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    public static List<App> resolve(Context c, List<String> keys) {
        List<App> out = new ArrayList<>();
        for (String k : keys) { App a = find(c, k); if (a != null) out.add(a); }
        return out;
    }

    public static synchronized Drawable icon(Context c, App a) {
        Drawable d = ICONS.get(a.key);
        if (d == null) {
            d = a.ri.loadIcon(c.getPackageManager());
            ICONS.put(a.key, d);
        }
        return d;
    }

    public static final String[] SHAPES = {"원본", "원형", "스쿼클", "둥근 사각형", "물방울"};

    /** Renders the icon into the chosen shape at sizePx. */
    public static synchronized Drawable shaped(Context c, App a, int shape, int sizePx) {
        if (shape == 0) return icon(c, a);
        String k = a.key + "#" + shape + "#" + sizePx;
        Bitmap b = SHAPED.get(k);
        if (b == null) {
            Bitmap src = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas sc = new Canvas(src);
            sc.drawColor(0xFFFFFFFF);
            Drawable ic = icon(c, a);
            // scale up a little so adaptive-icon padding doesn't leave a white rim
            int pad = -sizePx / 10;
            ic.setBounds(pad, pad, sizePx - pad, sizePx - pad);
            ic.draw(sc);
            b = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            cv.drawPath(shapePath(shape, sizePx), p);
            SHAPED.put(k, b);
        }
        return new BitmapDrawable(c.getResources(), b);
    }

    static Path shapePath(int shape, float s) {
        Path p = new Path();
        switch (shape) {
            case 1: p.addCircle(s / 2, s / 2, s / 2, Path.Direction.CW); break;
            case 2: { // superellipse-ish squircle
                float r = s / 2;
                int n = 64;
                for (int i = 0; i <= n; i++) {
                    double t = 2 * Math.PI * i / n;
                    double ct = Math.cos(t), st = Math.sin(t);
                    float x = (float) (r + r * Math.signum(ct) * Math.pow(Math.abs(ct), 0.5));
                    float y = (float) (r + r * Math.signum(st) * Math.pow(Math.abs(st), 0.5));
                    if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
                }
                p.close();
                break;
            }
            case 3: p.addRoundRect(new RectF(0, 0, s, s), s * 0.22f, s * 0.22f, Path.Direction.CW); break;
            case 4: {
                float r = s / 2;
                p.addRoundRect(new RectF(0, 0, s, s), new float[]{r, r, r, r, s * 0.12f, s * 0.12f, r, r}, Path.Direction.CW);
                break;
            }
            default: p.addRect(0, 0, s, s, Path.Direction.CW);
        }
        return p;
    }

    /** Average vivid color of the icon — used by edge lighting "app color". */
    public static int dominantColor(Drawable d) {
        try {
            Bitmap b = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, 24, 24);
            d.draw(c);
            long r = 0, g = 0, bl = 0, n = 0;
            float[] hsv = new float[3];
            for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) {
                int px = b.getPixel(x, y);
                if ((px >>> 24) < 128) continue;
                android.graphics.Color.colorToHSV(px, hsv);
                if (hsv[1] < 0.25f || hsv[2] < 0.25f) continue;
                r += (px >> 16) & 0xFF; g += (px >> 8) & 0xFF; bl += px & 0xFF; n++;
            }
            if (n == 0) return 0;
            int col = 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (bl / n);
            android.graphics.Color.colorToHSV(col, hsv);
            hsv[1] = Math.min(1f, hsv[1] * 1.3f);
            hsv[2] = Math.max(0.85f, hsv[2]);
            return android.graphics.Color.HSVToColor(hsv);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    public static void launch(Context c, App a) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(a.cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try { c.startActivity(i); } catch (RuntimeException e) { Actions.toast(c, "앱을 열 수 없어요"); }
    }
}
