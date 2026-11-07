package com.sapremdonda.infotainme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.format.DateFormat;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Draws the whole dashboard (clock, metrics, calendar) onto a Canvas.
 * Shared by the phone screen and the Android Auto car screen so both look the same.
 * Every size is relative to the drawing area, so it scales to any screen.
 */
public final class DashboardRenderer {

    public static final int BG = 0xFF05070D;
    private static final int PANEL = 0xFF0B1220;
    private static final int GRID = 0x1200E5FF;
    private static final int BORDER = 0x4400E5FF;
    private static final int CYAN = 0xFF00E5FF;
    private static final int MAGENTA = 0xFFFF2E88;
    private static final int TEXT = 0xFFE6F1FF;
    private static final int DIM = 0xFF5C6B82;
    private static final int GREEN = 0xFF3DFFA8;
    private static final int AMBER = 0xFFFFB020;
    private static final int TRACK = 0xFF1A2438;

    private static final long GB = 1024L * 1024L * 1024L;
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    private final Context context;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface thin = Typeface.create("sans-serif-thin", Typeface.NORMAL);
    private final Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Calendar cal = Calendar.getInstance();

    public DashboardRenderer(Context context) {
        this.context = context;
        stroke.setStyle(Paint.Style.STROKE);
        text.setFontFeatureSettings("tnum");
    }

    /** Clears the canvas and draws the dashboard inside {@code area}. */
    public void draw(Canvas c, Rect area, DeviceMetrics.Snapshot metrics, long nowMillis) {
        c.drawColor(BG);
        float w = area.width();
        float h = area.height();
        if (w <= 0 || h <= 0) {
            return;
        }
        cal.setTimeInMillis(nowMillis);

        float pad = Math.min(w, h) * 0.045f;
        drawGrid(c, area, Math.min(w, h) / 12f);

        RectF inner = new RectF(area.left + pad, area.top + pad, area.right - pad, area.bottom - pad);
        RectF clockBox;
        RectF metricsBox;
        RectF calendarBox;
        if (w >= h * 1.25f) {
            // Landscape: clock and metrics on the left, calendar on the right.
            float split = inner.left + inner.width() * 0.58f;
            calendarBox = new RectF(split + pad / 2f, inner.top, inner.right, inner.bottom);
            float leftRight = split - pad / 2f;
            float clockBottom = inner.top + inner.height() * 0.6f;
            clockBox = new RectF(inner.left, inner.top, leftRight, clockBottom);
            metricsBox = new RectF(inner.left, clockBottom + pad / 2f, leftRight, inner.bottom);
        } else {
            // Narrow screen: stack clock, metrics and calendar.
            float clockBottom = inner.top + inner.height() * 0.32f;
            float metricsBottom = clockBottom + inner.height() * 0.24f;
            clockBox = new RectF(inner.left, inner.top, inner.right, clockBottom);
            metricsBox = new RectF(inner.left, clockBottom + pad / 2f, inner.right, metricsBottom);
            calendarBox = new RectF(inner.left, metricsBottom + pad, inner.right, inner.bottom);
        }

        drawClock(c, clockBox, nowMillis);
        drawMetrics(c, metricsBox, metrics, pad / 2f);
        drawCalendar(c, calendarBox);
    }

    private void drawGrid(Canvas c, Rect area, float step) {
        stroke.setColor(GRID);
        stroke.setStrokeWidth(1f);
        for (float x = area.left + step; x < area.right; x += step) {
            c.drawLine(x, area.top, x, area.bottom, stroke);
        }
        for (float y = area.top + step; y < area.bottom; y += step) {
            c.drawLine(area.left, y, area.right, y, stroke);
        }
    }

    // ---------------------------------------------------------------- clock

    private void drawClock(Canvas c, RectF box, long nowMillis) {
        boolean is24 = DateFormat.is24HourFormat(context);
        int hour = cal.get(is24 ? Calendar.HOUR_OF_DAY : Calendar.HOUR);
        if (!is24 && hour == 0) {
            hour = 12;
        }
        String hm = String.format(Locale.US, is24 ? "%02d:%02d" : "%d:%02d",
                hour, cal.get(Calendar.MINUTE));
        int seconds = cal.get(Calendar.SECOND);
        String sec = String.format(Locale.US, "%02d", seconds);

        // Size the time so "88:88" plus the seconds fits the box.
        text.setTypeface(thin);
        text.setLetterSpacing(0f);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(100f);
        float refWidth = text.measureText("88:88") + 0.32f * text.measureText("88") + 6f;
        float size = Math.min(box.height() * 0.55f, 100f * box.width() / refWidth);

        float labelSize = size * 0.1f;
        float baseline = box.top + box.height() * 0.64f;

        // Small label above the time.
        text.setTypeface(medium);
        text.setTextSize(labelSize);
        text.setLetterSpacing(0.3f);
        text.setColor(CYAN);
        c.drawText("LOCAL TIME", box.left, baseline - size * 0.82f, text);

        // Main time with a neon glow.
        text.setTypeface(thin);
        text.setLetterSpacing(0f);
        text.setTextSize(size);
        text.setColor(TEXT);
        text.setShadowLayer(size * 0.08f, 0f, 0f, 0x9900E5FF);
        c.drawText(hm, box.left, baseline, text);
        float hmEnd = box.left + text.measureText(hm);
        text.clearShadowLayer();

        // Seconds, plus AM/PM in 12-hour mode.
        float secSize = size * 0.32f;
        float secX = hmEnd + size * 0.06f;
        text.setTextSize(secSize);
        text.setColor(CYAN);
        c.drawText(sec, secX, baseline, text);
        float secWidth = text.measureText("88");
        if (!is24) {
            text.setTypeface(medium);
            text.setTextSize(size * 0.14f);
            text.setLetterSpacing(0.1f);
            text.setColor(DIM);
            c.drawText(cal.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM",
                    secX, baseline - secSize * 1.25f, text);
            text.setLetterSpacing(0f);
        }

        // Seconds sweep bar under the time.
        float barY = baseline + size * 0.14f;
        float barLeft = box.left;
        float barRight = Math.min(box.right, secX + secWidth);
        float progress = (seconds + (nowMillis % 1000L) / 1000f) / 60f;
        float barEnd = barLeft + (barRight - barLeft) * progress;
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeWidth(Math.max(2f, size * 0.025f));
        stroke.setColor(TRACK);
        c.drawLine(barLeft, barY, barRight, barY, stroke);
        stroke.setColor(CYAN);
        stroke.setShadowLayer(size * 0.05f, 0f, 0f, CYAN);
        c.drawLine(barLeft, barY, barEnd, barY, stroke);
        stroke.clearShadowLayer();
        stroke.setStrokeCap(Paint.Cap.BUTT);
        fill.setColor(TEXT);
        c.drawCircle(barEnd, barY, size * 0.03f, fill);

        // Date line.
        String date = new SimpleDateFormat("EEEE  ·  d MMMM yyyy", Locale.getDefault())
                .format(new Date(nowMillis)).toUpperCase(Locale.getDefault());
        text.setTypeface(light);
        text.setLetterSpacing(0.18f);
        text.setColor(TEXT);
        fitTextSize(date, size * 0.13f, box.width());
        c.drawText(date, box.left, barY + text.getTextSize() * 1.9f, text);
        text.setLetterSpacing(0f);
    }

    // -------------------------------------------------------------- metrics

    private void drawMetrics(Canvas c, RectF box, DeviceMetrics.Snapshot m, float gap) {
        String[] labels = {"BATTERY", "MEMORY", "STORAGE", "UPTIME"};
        String[] values = new String[4];
        String[] subs = new String[4];
        float[] ratios = new float[4];
        int[] colors = new int[4];

        if (m != null && m.batteryPercent >= 0) {
            values[0] = m.batteryPercent + "%";
            subs[0] = m.charging ? "CHARGING" : "ON BATTERY";
            ratios[0] = m.batteryPercent / 100f;
            colors[0] = m.charging ? GREEN : (m.batteryPercent <= 20 ? MAGENTA : CYAN);
        } else {
            values[0] = "--";
            subs[0] = "UNKNOWN";
            colors[0] = DIM;
        }

        if (m != null && m.ramTotal > 0) {
            float used = 1f - (float) m.ramAvailable / m.ramTotal;
            values[1] = Math.round(used * 100f) + "%";
            subs[1] = gb(m.ramTotal - m.ramAvailable) + " / " + gb(m.ramTotal) + " GB";
            ratios[1] = used;
            colors[1] = used >= 0.85f ? AMBER : CYAN;
        } else {
            values[1] = "--";
            subs[1] = "UNKNOWN";
            colors[1] = DIM;
        }

        if (m != null && m.storageTotal > 0) {
            float used = 1f - (float) m.storageAvailable / m.storageTotal;
            values[2] = gb(m.storageAvailable) + " GB";
            subs[2] = "FREE OF " + Math.round((double) m.storageTotal / GB) + " GB";
            ratios[2] = used;
            colors[2] = used >= 0.9f ? AMBER : CYAN;
        } else {
            values[2] = "--";
            subs[2] = "UNKNOWN";
            colors[2] = DIM;
        }

        long up = SystemClock.elapsedRealtime();
        long minutes = up / 60000L;
        values[3] = String.format(Locale.US, "%d:%02d", minutes / 60L, minutes % 60L);
        subs[3] = "HRS SINCE BOOT";
        ratios[3] = (up % DAY_MS) / (float) DAY_MS;
        colors[3] = MAGENTA;

        // One row of four cards when wide enough, otherwise a 2x2 grid.
        int cols = box.width() / box.height() >= 2.6f ? 4 : 2;
        int rows = 4 / cols;
        float cardW = (box.width() - gap * (cols - 1)) / cols;
        float cardH = (box.height() - gap * (rows - 1)) / rows;
        for (int i = 0; i < 4; i++) {
            float left = box.left + (i % cols) * (cardW + gap);
            float top = box.top + (i / cols) * (cardH + gap);
            drawCard(c, new RectF(left, top, left + cardW, top + cardH),
                    labels[i], values[i], subs[i], ratios[i], colors[i]);
        }
    }

    private void drawCard(Canvas c, RectF r, String label, String value, String sub,
                          float ratio, int color) {
        float unit = Math.min(r.height(), r.width() * 0.8f);
        float radius = unit * 0.1f;
        fill.setColor(PANEL);
        c.drawRoundRect(r, radius, radius, fill);
        stroke.setColor(BORDER);
        stroke.setStrokeWidth(Math.max(1f, unit * 0.01f));
        c.drawRoundRect(r, radius, radius, stroke);

        float px = r.left + r.width() * 0.09f;
        float maxW = r.width() * 0.82f;
        text.setTextAlign(Paint.Align.LEFT);

        text.setTypeface(medium);
        text.setLetterSpacing(0.2f);
        text.setColor(DIM);
        fitTextSize(label, unit * 0.12f, maxW);
        c.drawText(label, px, r.top + r.height() * 0.24f, text);

        text.setTypeface(light);
        text.setLetterSpacing(0f);
        text.setColor(TEXT);
        fitTextSize(value, unit * 0.32f, maxW);
        c.drawText(value, px, r.top + r.height() * 0.58f, text);

        text.setTypeface(medium);
        text.setLetterSpacing(0.1f);
        text.setColor(color);
        fitTextSize(sub, unit * 0.1f, maxW);
        c.drawText(sub, px, r.top + r.height() * 0.74f, text);
        text.setLetterSpacing(0f);

        // Progress bar along the bottom of the card.
        float barY = r.top + r.height() * 0.86f;
        float barRight = r.right - r.width() * 0.09f;
        float clamped = Math.max(0f, Math.min(1f, ratio));
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeWidth(Math.max(2f, unit * 0.035f));
        stroke.setColor(TRACK);
        c.drawLine(px, barY, barRight, barY, stroke);
        if (clamped > 0f) {
            stroke.setColor(color);
            c.drawLine(px, barY, px + (barRight - px) * clamped, barY, stroke);
        }
        stroke.setStrokeCap(Paint.Cap.BUTT);
    }

    // ------------------------------------------------------------- calendar

    private void drawCalendar(Canvas c, RectF box) {
        float unit = Math.min(box.width(), box.height());
        float radius = unit * 0.05f;
        fill.setColor(PANEL);
        c.drawRoundRect(box, radius, radius, fill);
        stroke.setColor(BORDER);
        stroke.setStrokeWidth(Math.max(1f, unit * 0.005f));
        c.drawRoundRect(box, radius, radius, stroke);

        float p = unit * 0.06f;
        RectF in = new RectF(box.left + p, box.top + p, box.right - p, box.bottom - p * 0.6f);

        // Header: month and year, week number on the right.
        float headerH = in.height() * 0.13f;
        String month = new SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                .format(cal.getTime()).toUpperCase(Locale.getDefault());
        String week = "WK " + cal.get(Calendar.WEEK_OF_YEAR);
        text.setTypeface(medium);
        text.setLetterSpacing(0.2f);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setColor(DIM);
        text.setTextSize(headerH * 0.4f);
        float weekWidth = text.measureText(week);
        drawCenteredY(c, week, in.right, in.top + headerH / 2f);

        // Month title gets whatever width the week number leaves.
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(CYAN);
        fitTextSize(month, headerH * 0.6f, in.width() - weekWidth - headerH * 0.5f);
        text.setShadowLayer(text.getTextSize() * 0.3f, 0f, 0f, 0x8800E5FF);
        drawCenteredY(c, month, in.left, in.top + headerH / 2f);
        text.clearShadowLayer();

        // Month layout.
        Calendar first = (Calendar) cal.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = cal.getFirstDayOfWeek();
        int offset = (first.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7;
        int daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        int today = cal.get(Calendar.DAY_OF_MONTH);
        int rows = (offset + daysInMonth + 6) / 7;

        float cellW = in.width() / 7f;
        float weekdayH = in.height() * 0.1f;
        float gridTop = in.top + headerH + weekdayH;
        float cellH = (in.bottom - gridTop) / rows;

        // Weekday names.
        String[] names = new DateFormatSymbols(Locale.getDefault()).getShortWeekdays();
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(medium);
        text.setLetterSpacing(0.1f);
        text.setTextSize(Math.min(weekdayH * 0.45f, cellW * 0.3f));
        for (int col = 0; col < 7; col++) {
            int dow = dayOfWeekForColumn(firstDayOfWeek, col);
            text.setColor(isWeekend(dow) ? MAGENTA : DIM);
            String name = names[dow].toUpperCase(Locale.getDefault());
            if (name.length() > 3) {
                name = name.substring(0, 3);
            }
            drawCenteredY(c, name, in.left + cellW * (col + 0.5f), in.top + headerH + weekdayH / 2f);
        }
        text.setLetterSpacing(0f);

        // Divider under the weekday row.
        stroke.setColor(BORDER);
        stroke.setStrokeWidth(1f);
        c.drawLine(in.left, gridTop - weekdayH * 0.15f, in.right, gridTop - weekdayH * 0.15f, stroke);

        // Day numbers.
        float daySize = Math.min(cellH, cellW) * 0.42f;
        for (int day = 1; day <= daysInMonth; day++) {
            int idx = offset + day - 1;
            int col = idx % 7;
            int row = idx / 7;
            float cx = in.left + cellW * (col + 0.5f);
            float cy = gridTop + cellH * (row + 0.5f);
            String label = Integer.toString(day);
            if (day == today) {
                float r = Math.min(cellW, cellH) * 0.4f;
                fill.setColor(CYAN);
                fill.setShadowLayer(r * 0.5f, 0f, 0f, CYAN);
                c.drawCircle(cx, cy, r, fill);
                fill.clearShadowLayer();
                text.setTypeface(medium);
                text.setColor(BG);
            } else {
                text.setTypeface(light);
                boolean weekend = isWeekend(dayOfWeekForColumn(firstDayOfWeek, col));
                text.setColor(day < today ? DIM : (weekend ? MAGENTA : TEXT));
            }
            text.setTextSize(daySize);
            drawCenteredY(c, label, cx, cy);
        }
        text.setTextAlign(Paint.Align.LEFT);
    }

    // -------------------------------------------------------------- helpers

    private static int dayOfWeekForColumn(int firstDayOfWeek, int col) {
        return ((firstDayOfWeek - 1 + col) % 7) + 1;
    }

    private static boolean isWeekend(int dayOfWeek) {
        return dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY;
    }

    /** Sets the text size to {@code maxSize}, shrinking it if the text is wider than {@code maxWidth}. */
    private void fitTextSize(String s, float maxSize, float maxWidth) {
        text.setTextSize(maxSize);
        float width = text.measureText(s);
        if (width > maxWidth && width > 0f) {
            text.setTextSize(maxSize * maxWidth / width);
        }
    }

    /** Draws text vertically centred on {@code cy}, using the paint's current alignment for x. */
    private void drawCenteredY(Canvas c, String s, float x, float cy) {
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(s, x, cy - (fm.ascent + fm.descent) / 2f, text);
    }

    private static String gb(long bytes) {
        return String.format(Locale.US, "%.1f", bytes / (double) GB);
    }
}
