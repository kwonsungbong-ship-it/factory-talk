package kr.sbkwon.decolock;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;

/** ClockFace styles for the lock screen (and preview in settings). */
public class ClockView extends View {
    public static final String[] STYLES = {"디지털 크게", "세로 쌓기", "아날로그", "네온", "링 초침", "레트로 도트"};

    private int style;
    private int color = 0xFFFFFFFF;
    private int accent;
    private boolean h24;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable tick = new Runnable() {
        public void run() {
            invalidate();
            postDelayed(this, 1000 - System.currentTimeMillis() % 1000);
        }
    };

    public ClockView(Context c) {
        super(c);
        accent = Prefs.accent(c);
    }

    public void setStyle(int style, boolean h24) {
        this.style = style;
        this.h24 = h24;
        // neon/blur needs software rendering
        setLayerType(style == 3 ? LAYER_TYPE_SOFTWARE : LAYER_TYPE_HARDWARE, null);
        requestLayout();
        invalidate();
    }

    public void setColor(int c) { color = c; invalidate(); }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); post(tick); }
    @Override protected void onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow(); }

    @Override
    protected void onMeasure(int wms, int hms) {
        int w = MeasureSpec.getSize(wms);
        int h;
        switch (style) {
            case 1: h = Ui.dp(getContext(), 250); break;
            case 2: case 4: h = Math.min(w, Ui.dp(getContext(), 260)); break;
            default: h = Ui.dp(getContext(), 150);
        }
        setMeasuredDimension(w, h);
    }

    private String hh(Calendar c) {
        int h = c.get(h24 ? Calendar.HOUR_OF_DAY : Calendar.HOUR);
        if (!h24 && h == 0) h = 12;
        return h24 ? String.format(Locale.US, "%02d", h) : String.valueOf(h);
    }

    private String mm(Calendar c) { return String.format(Locale.US, "%02d", c.get(Calendar.MINUTE)); }

    @Override
    protected void onDraw(Canvas cv) {
        Calendar c = Calendar.getInstance();
        float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2;
        p.setMaskFilter(null);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(color);
        switch (style) {
            case 0: {
                p.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                p.setTextSize(h * 0.78f);
                drawCentered(cv, hh(c) + ":" + mm(c), cx, cy);
                break;
            }
            case 1: {
                p.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
                p.setTextSize(h * 0.46f);
                drawCentered(cv, hh(c), cx, h * 0.27f);
                p.setColor(accent);
                drawCentered(cv, mm(c), cx, h * 0.73f);
                break;
            }
            case 2: {
                float r = Math.min(w, h) / 2 - Ui.dp(getContext(), 8);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 2));
                p.setAlpha(90);
                cv.drawCircle(cx, cy, r, p);
                p.setAlpha(255);
                for (int i = 0; i < 12; i++) {
                    double a = Math.PI * 2 * i / 12;
                    float in = i % 3 == 0 ? r * 0.82f : r * 0.9f;
                    p.setStrokeWidth(Ui.dp(getContext(), i % 3 == 0 ? 4 : 2));
                    cv.drawLine(cx + (float) Math.sin(a) * in, cy - (float) Math.cos(a) * in,
                            cx + (float) Math.sin(a) * r * 0.97f, cy - (float) Math.cos(a) * r * 0.97f, p);
                }
                p.setStrokeCap(Paint.Cap.ROUND);
                float sec = c.get(Calendar.SECOND), min = c.get(Calendar.MINUTE) + sec / 60f, hr = c.get(Calendar.HOUR) + min / 60f;
                hand(cv, cx, cy, hr / 12f, r * 0.5f, Ui.dp(getContext(), 7), color);
                hand(cv, cx, cy, min / 60f, r * 0.75f, Ui.dp(getContext(), 4), color);
                hand(cv, cx, cy, sec / 60f, r * 0.85f, Ui.dp(getContext(), 2), accent);
                p.setStyle(Paint.Style.FILL);
                p.setColor(accent);
                cv.drawCircle(cx, cy, Ui.dp(getContext(), 6), p);
                break;
            }
            case 3: {
                p.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                p.setTextSize(h * 0.7f);
                String s = hh(c) + ":" + mm(c);
                p.setColor(accent);
                p.setMaskFilter(new BlurMaskFilter(Ui.dp(getContext(), 14), BlurMaskFilter.Blur.NORMAL));
                drawCentered(cv, s, cx, cy);
                p.setMaskFilter(new BlurMaskFilter(Ui.dp(getContext(), 4), BlurMaskFilter.Blur.NORMAL));
                drawCentered(cv, s, cx, cy);
                p.setMaskFilter(null);
                p.setColor(0xFFFFFFFF);
                drawCentered(cv, s, cx, cy);
                break;
            }
            case 4: {
                float r = Math.min(w, h) / 2 - Ui.dp(getContext(), 10);
                RectF o = new RectF(cx - r, cy - r, cx + r, cy + r);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeWidth(Ui.dp(getContext(), 8));
                p.setColor(color);
                p.setAlpha(50);
                cv.drawArc(o, 0, 360, false, p);
                p.setColor(accent);
                float sec = c.get(Calendar.SECOND) + (System.currentTimeMillis() % 1000) / 1000f;
                cv.drawArc(o, -90, Math.max(1, sec / 60f * 360f), false, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(color);
                p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                p.setTextSize(r * 0.62f);
                drawCentered(cv, hh(c) + ":" + mm(c), cx, cy);
                break;
            }
            case 5: {
                String s = hh(c) + ":" + mm(c);
                dotText(cv, s, cx, cy, h);
                break;
            }
        }
    }

    private void hand(Canvas cv, float cx, float cy, float frac, float len, float width, int col) {
        double a = Math.PI * 2 * frac;
        p.setStyle(Paint.Style.STROKE);
        p.setColor(col);
        p.setStrokeWidth(width);
        cv.drawLine(cx, cy, cx + (float) Math.sin(a) * len, cy - (float) Math.cos(a) * len, p);
    }

    private void drawCentered(Canvas cv, String s, float x, float y) {
        Paint.FontMetrics fm = p.getFontMetrics();
        cv.drawText(s, x, y - (fm.ascent + fm.descent) / 2, p);
    }

    // 5x7 dot-matrix digits
    private static final String[] DOTS = {
            "01110100011001110101110011000101110", "00100011000010000100001000010001110",
            "01110100010000100010001000100011111", "11111000100010000010000011000101110",
            "00010001100101010010111110001000010", "11111100001111000001000011000101110",
            "00110010001000011110100011000101110", "11111000010001000100010000100001000",
            "01110100011000101110100011000101110", "01110100011000101111000010001001100"
    };

    private void dotText(Canvas cv, String s, float cx, float cy, float h) {
        float cell = h / 9f;
        float totalW = 0;
        for (char ch : s.toCharArray()) totalW += (ch == ':' ? 2 : 6) * cell;
        float x = cx - totalW / 2;
        float top = cy - 3.5f * cell;
        for (char ch : s.toCharArray()) {
            if (ch == ':') {
                p.setColor(accent);
                cv.drawCircle(x + cell * 0.5f, top + cell * 2, cell * 0.38f, p);
                cv.drawCircle(x + cell * 0.5f, top + cell * 4, cell * 0.38f, p);
                x += 2 * cell;
                continue;
            }
            String d = DOTS[ch - '0'];
            for (int i = 0; i < 35; i++) {
                int col = i % 5, row = i / 5;
                boolean on = d.charAt(i) == '1';
                p.setColor(on ? color : (color & 0x00FFFFFF) | 0x1A000000);
                cv.drawCircle(x + col * cell + cell / 2, top + row * cell + cell / 2, cell * 0.38f, p);
            }
            x += 6 * cell;
        }
    }

    public static boolean is24(Context c) { return DateFormat.is24HourFormat(c); }
}
