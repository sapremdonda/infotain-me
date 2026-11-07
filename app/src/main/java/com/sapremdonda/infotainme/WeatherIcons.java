package com.sapremdonda.infotainme;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/** Simple flat weather icons drawn with shapes, so they stay sharp at any size. */
final class WeatherIcons {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path cut = new Path();

    WeatherIcons() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    /** Draws the icon centred on (cx, cy), fitting in a square of side {@code s}. */
    void draw(Canvas c, Weather.Icon icon, boolean isDay, float cx, float cy, float s, Palette p) {
        switch (icon) {
            case CLEAR:
                if (isDay) {
                    sun(c, cx, cy, s * 0.24f, p);
                } else {
                    moon(c, cx, cy, s * 0.34f, p);
                }
                break;
            case PARTLY:
                if (isDay) {
                    sun(c, cx - s * 0.14f, cy - s * 0.14f, s * 0.17f, p);
                } else {
                    moon(c, cx - s * 0.12f, cy - s * 0.14f, s * 0.24f, p);
                }
                cloud(c, cx + s * 0.06f, cy + s * 0.12f, s * 0.74f, p.cloud);
                break;
            case FOG:
                stroke.setColor(p.cloud);
                stroke.setStrokeWidth(s * 0.08f);
                for (int i = -1; i <= 1; i++) {
                    float y = cy + i * s * 0.2f;
                    float inset = i == 0 ? 0f : s * 0.08f;
                    c.drawLine(cx - s * 0.38f + inset, y, cx + s * 0.38f - inset * 0.5f, y, stroke);
                }
                break;
            case DRIZZLE:
            case RAIN:
                cloud(c, cx, cy - s * 0.1f, s * 0.86f, p.cloud);
                stroke.setColor(p.rain);
                stroke.setStrokeWidth(s * 0.07f);
                float len = icon == Weather.Icon.RAIN ? s * 0.18f : s * 0.08f;
                for (int i = -1; i <= 1; i++) {
                    float x = cx + i * s * 0.22f;
                    float y = cy + s * 0.24f;
                    c.drawLine(x, y, x - len * 0.35f, y + len, stroke);
                }
                break;
            case SNOW:
                cloud(c, cx, cy - s * 0.1f, s * 0.86f, p.cloud);
                fill.setColor(p.rain);
                for (int i = -1; i <= 1; i++) {
                    c.drawCircle(cx + i * s * 0.22f, cy + s * 0.32f + (i == 0 ? s * 0.06f : 0f),
                            s * 0.05f, fill);
                }
                break;
            case THUNDER:
                cloud(c, cx, cy - s * 0.12f, s * 0.86f, p.cloud);
                path.reset();
                path.moveTo(cx + s * 0.05f, cy + s * 0.06f);
                path.lineTo(cx - s * 0.11f, cy + s * 0.29f);
                path.lineTo(cx - s * 0.01f, cy + s * 0.29f);
                path.lineTo(cx - s * 0.07f, cy + s * 0.47f);
                path.lineTo(cx + s * 0.13f, cy + s * 0.2f);
                path.lineTo(cx + s * 0.03f, cy + s * 0.2f);
                path.lineTo(cx + s * 0.1f, cy + s * 0.06f);
                path.close();
                fill.setColor(p.sun);
                c.drawPath(path, fill);
                break;
            case CLOUDY:
            default:
                cloud(c, cx, cy + s * 0.04f, s * 0.92f, p.cloud);
                break;
        }
    }

    private void sun(Canvas c, float cx, float cy, float r, Palette p) {
        fill.setColor(p.sun);
        c.drawCircle(cx, cy, r, fill);
        stroke.setColor(p.sun);
        stroke.setStrokeWidth(r * 0.24f);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            c.drawLine(cx + cos * r * 1.45f, cy + sin * r * 1.45f,
                    cx + cos * r * 1.8f, cy + sin * r * 1.8f, stroke);
        }
    }

    private void moon(Canvas c, float cx, float cy, float r, Palette p) {
        path.reset();
        path.addCircle(cx, cy, r, Path.Direction.CW);
        cut.reset();
        cut.addCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.82f, Path.Direction.CW);
        path.op(cut, Path.Op.DIFFERENCE);
        fill.setColor(p.moon);
        c.drawPath(path, fill);
    }

    /** Cloud of width w, roughly centred on (x, y). */
    private void cloud(Canvas c, float x, float y, float w, int color) {
        path.reset();
        // reset() keeps the fill type, and the moon's Path.op() may have changed it.
        path.setFillType(Path.FillType.WINDING);
        float top = y - w * 0.02f;
        path.addRoundRect(x - w * 0.48f, top, x + w * 0.48f, top + w * 0.3f, w * 0.15f, w * 0.15f,
                Path.Direction.CW);
        path.addCircle(x - w * 0.2f, top + w * 0.04f, w * 0.19f, Path.Direction.CW);
        path.addCircle(x + w * 0.04f, top - w * 0.05f, w * 0.26f, Path.Direction.CW);
        path.addCircle(x + w * 0.28f, top + w * 0.08f, w * 0.17f, Path.Direction.CW);
        fill.setColor(color);
        c.drawPath(path, fill);
    }
}
