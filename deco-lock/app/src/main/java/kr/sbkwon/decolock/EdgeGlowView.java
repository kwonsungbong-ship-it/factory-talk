package kr.sbkwon.decolock;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.os.Build;
import android.view.RoundedCorner;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.LinearInterpolator;

/** Screen-edge light used by Edge Lighting (also previewed in settings). */
public class EdgeGlowView extends View {
    public static final String[] EFFECTS = {"회전 그라데이션", "숨쉬기(펄스)", "흐르는 빛", "네온 깜빡임"};
    public static final String[] STYLES = {"무지개", "선택한 색", "앱 아이콘 색"};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Matrix m = new Matrix();
    private int[] colors = {0xFFFF0080, 0xFFFFB000, 0xFF00E5FF, 0xFF7C4DFF, 0xFFFF0080};
    private float strokePx;
    private int effect;
    private float t; // 0..1 animation progress
    private float fade = 1f;
    private float radius;
    private ValueAnimator anim;
    private float perimeter;

    public EdgeGlowView(Context c) {
        super(c);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        radius = Ui.dp(c, 38);
    }

    public void configure(int effect, int widthDp, int[] colors) {
        this.effect = effect;
        this.strokePx = Ui.dp(getContext(), widthDp);
        if (colors != null && colors.length > 0) {
            if (colors.length == 1) {
                int c = colors[0];
                int light = blend(c, 0xFFFFFFFF, 0.45f);
                this.colors = new int[]{c, light, c, light, c};
            } else this.colors = colors;
        }
        invalidate();
    }

    private static int blend(int a, int b, float f) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f);
        int g = (int) (((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f);
        int bl = (int) ((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets in) {
        if (Build.VERSION.SDK_INT >= 31) {
            RoundedCorner rc = in.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT);
            if (rc != null && rc.getRadius() > 0) radius = rc.getRadius();
        }
        return super.onApplyWindowInsets(in);
    }

    /** Plays once for durationMs (loops of the effect inside), then calls done. */
    public void play(long durationMs, final Runnable done) {
        stop();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(durationMs);
        anim.setInterpolator(new LinearInterpolator());
        final long dur = durationMs;
        anim.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            float elapsed = f * dur;
            t = (elapsed % 1600f) / 1600f;
            float fadeIn = Math.min(1f, elapsed / 250f);
            float fadeOut = Math.min(1f, (dur - elapsed) / 400f);
            fade = Math.max(0f, Math.min(fadeIn, fadeOut));
            invalidate();
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) { if (done != null) done.run(); }
        });
        anim.start();
    }

    /** Endless loop for previews. */
    public void loop() {
        stop();
        fade = 1f;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1600);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> { t = (float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    public void stop() {
        if (anim != null) { anim.removeAllListeners(); anim.cancel(); anim = null; }
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        rebuild(w, h);
    }

    private void rebuild(int w, int h) {
        path.reset();
        float inset = strokePx / 2f;
        float r = Math.max(0, radius - inset);
        path.addRoundRect(new RectF(inset, inset, w - inset, h - inset), r, r, Path.Direction.CW);
        perimeter = new PathMeasure(path, true).getLength();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        if (perimeter == 0) rebuild(w, h);
        SweepGradient sg = new SweepGradient(w / 2f, h / 2f, colors, null);
        float alpha = fade;
        switch (effect) {
            case 0:
                m.setRotate(t * 360f, w / 2f, h / 2f);
                sg.setLocalMatrix(m);
                paint.setPathEffect(null);
                break;
            case 1:
                alpha *= 0.45f + 0.55f * (float) (0.5 + 0.5 * Math.sin(t * 2 * Math.PI));
                paint.setPathEffect(null);
                break;
            case 2: {
                float seg = perimeter * 0.28f;
                paint.setPathEffect(new DashPathEffect(new float[]{seg, perimeter - seg}, -t * perimeter * 1f));
                m.setRotate(t * 360f, w / 2f, h / 2f);
                sg.setLocalMatrix(m);
                // second comet on the opposite side
                break;
            }
            case 3: {
                float x = t * 8f % 1f;
                alpha *= (t > 0.05f && t < 0.1f) || (t > 0.18f && t < 0.22f) ? 0.25f : 1f;
                alpha *= 0.8f + 0.2f * x;
                paint.setPathEffect(null);
                break;
            }
        }
        paint.setShader(sg);
        float[] widths = {3.2f, 2.1f, 1.4f, 1f};
        float[] alphas = {0.12f, 0.22f, 0.45f, 1f};
        for (int i = 0; i < widths.length; i++) {
            paint.setStrokeWidth(strokePx * widths[i]);
            paint.setAlpha((int) (255 * alphas[i] * alpha));
            c.drawPath(path, paint);
        }
        if (effect == 2) {
            paint.setPathEffect(new DashPathEffect(new float[]{perimeter * 0.28f, perimeter * 0.72f}, -t * perimeter - perimeter / 2f));
            for (int i = 0; i < widths.length; i++) {
                paint.setStrokeWidth(strokePx * widths[i]);
                paint.setAlpha((int) (255 * alphas[i] * alpha));
                c.drawPath(path, paint);
            }
        }
    }
}
