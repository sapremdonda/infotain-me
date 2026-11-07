package com.sapremdonda.infotainme;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Liquid-glass look: the wallpaper (with soft colour glows) is drawn once, and a blurred copy is
 * shown through each card, slightly magnified like a lens, then tinted with a sheen and a bright rim.
 * Bitmaps are rebuilt only when the wallpaper, theme or screen size changes.
 */
final class LiquidGlass {

    /** The blurred copy is this many times smaller than the screen. */
    private static final int BLUR_SCALE = 10;
    private static final float LENS_ZOOM = 1.06f;

    private final Paint plain = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();

    private Bitmap wallpaper;
    private Bitmap blurred;
    private BitmapShader blurShader;
    private int[] builtFor;
    private int builtW;
    private int builtH;
    private float shadowBlur = -1f;

    LiquidGlass() {
        rim.setStyle(Paint.Style.STROKE);
    }

    /** Draws the wallpaper over the whole canvas. */
    void drawWallpaper(Canvas c, int[] colors) {
        int w = c.getWidth();
        int h = c.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (wallpaper == null || colors != builtFor || w != builtW || h != builtH) {
            build(w, h, colors);
        }
        c.drawBitmap(wallpaper, 0f, 0f, plain);
    }

    /** Draws one glass card. {@code u} is the layout unit used for edge and shadow sizes. */
    void drawCard(Canvas c, RectF r, float radius, float u, Palette p) {
        // Soft shadow, outside the card only so it doesn't show through the glass.
        float blur = u * 0.045f;
        if (blur != shadowBlur && blur > 0f) {
            shadow.setMaskFilter(new BlurMaskFilter(blur, BlurMaskFilter.Blur.OUTER));
            shadowBlur = blur;
        }
        shadow.setColor(p.shadow);
        c.save();
        c.translate(0f, u * 0.003f);
        c.drawRoundRect(r, radius, radius, shadow);
        c.restore();

        // Blurred wallpaper, magnified a little around the card's centre.
        if (blurShader != null) {
            matrix.setScale(BLUR_SCALE, BLUR_SCALE);
            matrix.postScale(LENS_ZOOM, LENS_ZOOM, r.centerX(), r.centerY());
            blurShader.setLocalMatrix(matrix);
            glass.setShader(blurShader);
            c.drawRoundRect(r, radius, radius, glass);
        }

        // Tint, then a sheen fading down from the top edge.
        fill.setShader(null);
        fill.setColor(p.glassTint);
        c.drawRoundRect(r, radius, radius, fill);
        fill.setShader(new LinearGradient(0f, r.top, 0f, r.top + r.height() * 0.55f,
                p.glassSheen, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, radius, radius, fill);
        fill.setShader(new LinearGradient(0f, r.bottom, 0f, r.bottom - r.height() * 0.25f,
                p.innerGlow, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, radius, radius, fill);
        fill.setShader(null);

        // Rim: bright where light hits (top-left), fading, with a softer catch-light bottom-right.
        float rimW = Math.max(1f, u * 0.0035f);
        float half = rimW / 2f;
        RectF edge = new RectF(r.left + half, r.top + half, r.right - half, r.bottom - half);
        rim.setStrokeWidth(rimW);
        rim.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                new int[]{p.rimBright, p.rimFaint, Color.TRANSPARENT, p.rimFaint, p.rimBright & 0x80FFFFFF},
                new float[]{0f, 0.3f, 0.55f, 0.8f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(edge, radius - half, radius - half, rim);
        rim.setShader(null);
    }

    private void build(int w, int h, int[] colors) {
        if (wallpaper != null) {
            wallpaper.recycle();
        }
        if (blurred != null) {
            blurred.recycle();
        }
        wallpaper = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas wc = new Canvas(wallpaper);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, w, h, colors, null, Shader.TileMode.CLAMP));
        wc.drawRect(0, 0, w, h, p);

        // Soft colour glows give the glass something to refract.
        float m = Math.max(w, h);
        int last = colors[colors.length - 1];
        int mid = colors[colors.length / 2];
        orb(wc, p, w * 0.12f, h * 0.88f, m * 0.42f, colors[0], 0.85f);
        orb(wc, p, w * 0.86f, h * 0.12f, m * 0.38f, mid, 0.75f);
        orb(wc, p, w * 0.55f, h * 0.6f, m * 0.25f, last, 0.35f);
        orb(wc, p, w * 0.32f, h * 0.18f, m * 0.2f, mid, 0.4f);

        int bw = Math.max(1, w / BLUR_SCALE);
        int bh = Math.max(1, h / BLUR_SCALE);
        blurred = Bitmap.createScaledBitmap(wallpaper, bw, bh, true);
        boxBlur(blurred, 4, 3);
        blurShader = new BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);

        builtFor = colors;
        builtW = w;
        builtH = h;
    }

    private static void orb(Canvas c, Paint p, float x, float y, float radius, int color, float alpha) {
        int a = Math.round(alpha * 255f);
        int center = (color & 0x00FFFFFF) | (a << 24);
        p.setShader(new RadialGradient(x, y, radius, new int[]{center, color & 0x00FFFFFF},
                null, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, radius, p);
    }

    /** Repeated box blur on a small bitmap; three passes look close to a Gaussian blur. */
    private static void boxBlur(Bitmap b, int radius, int passes) {
        int w = b.getWidth();
        int h = b.getHeight();
        int[] px = new int[w * h];
        int[] tmp = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < passes; i++) {
            blurLine(px, tmp, w, h, radius, true);
            blurLine(tmp, px, w, h, radius, false);
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
    }

    private static void blurLine(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w;
        int len = horizontal ? w : h;
        int div = 2 * r + 1;
        for (int line = 0; line < lines; line++) {
            int sa = 0;
            int sr = 0;
            int sg = 0;
            int sb = 0;
            for (int k = -r; k <= r; k++) {
                int c = in[index(line, clamp(k, len), w, horizontal)];
                sa += c >>> 24;
                sr += (c >> 16) & 0xFF;
                sg += (c >> 8) & 0xFF;
                sb += c & 0xFF;
            }
            for (int i = 0; i < len; i++) {
                out[index(line, i, w, horizontal)] =
                        ((sa / div) << 24) | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int add = in[index(line, clamp(i + r + 1, len), w, horizontal)];
                int sub = in[index(line, clamp(i - r, len), w, horizontal)];
                sa += (add >>> 24) - (sub >>> 24);
                sr += ((add >> 16) & 0xFF) - ((sub >> 16) & 0xFF);
                sg += ((add >> 8) & 0xFF) - ((sub >> 8) & 0xFF);
                sb += (add & 0xFF) - (sub & 0xFF);
            }
        }
    }

    private static int index(int line, int i, int w, boolean horizontal) {
        return horizontal ? line * w + i : i * w + line;
    }

    private static int clamp(int i, int len) {
        return i < 0 ? 0 : (i >= len ? len - 1 : i);
    }
}
