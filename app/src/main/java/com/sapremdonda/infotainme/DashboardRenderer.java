package com.sapremdonda.infotainme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Draws the car dashboard: a gradient wallpaper with rounded cards for the clock, weather,
 * calendar, phone status and notifications. Sizes are relative to the screen, so it scales
 * to any car display.
 */
public final class DashboardRenderer {

    private static final long GB = 1024L * 1024L * 1024L;

    private final Fonts fonts;
    private final WeatherIcons weatherIcons = new WeatherIcons();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wallpaperPaint = new Paint();
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Calendar cal = Calendar.getInstance();
    private final Matrix matrix = new Matrix();

    // Wallpaper gradient is rebuilt only when something changes.
    private Shader wallShader;
    private int wallW;
    private int wallH;
    private int[] wallColors;

    // Per-frame values.
    private Palette pal;
    private float u;
    private float pad;
    private float headerSize;
    private float radius;

    public DashboardRenderer(Context context) {
        fonts = Fonts.get(context);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        wallpaperPaint.setDither(true);
    }

    /** Draws the wallpaper over the whole canvas and the cards inside {@code area}. */
    public void draw(Canvas c, Rect area, DashboardState s) {
        pal = s.dark ? Palette.DARK : Palette.LIGHT;
        cal.setTimeInMillis(s.now);
        drawWallpaper(c, s.wallpaper.colors(s.dark));

        float w = area.width();
        float h = area.height();
        if (w <= 0 || h <= 0) {
            return;
        }
        u = Math.min(w, h);
        float gap = u * 0.035f;
        pad = u * 0.045f;
        headerSize = u * 0.042f;
        radius = u * 0.065f;

        RectF in = new RectF(area.left + gap, area.top + gap, area.right - gap, area.bottom - gap);
        RectF clock;
        RectF weather;
        RectF calendar;
        RectF status;
        RectF notes;
        if (w >= h * 1.3f) {
            // Landscape: three columns.
            float avail = in.width() - 2 * gap;
            float x1 = in.left + avail * 0.37f;
            float x2 = x1 + gap + avail * 0.31f;
            float clockBottom = in.top + in.height() * 0.5f;
            float calendarBottom = in.top + in.height() * 0.64f;
            clock = new RectF(in.left, in.top, x1, clockBottom);
            weather = new RectF(in.left, clockBottom + gap, x1, in.bottom);
            calendar = new RectF(x1 + gap, in.top, x2, calendarBottom);
            status = new RectF(x1 + gap, calendarBottom + gap, x2, in.bottom);
            notes = new RectF(x2 + gap, in.top, in.right, in.bottom);
        } else {
            // Narrow or portrait screen: two columns.
            float mid = in.left + (in.width() - gap) / 2f;
            float row1 = in.top + in.height() * 0.24f;
            float row2 = row1 + gap + in.height() * 0.24f;
            clock = new RectF(in.left, in.top, in.right, row1);
            weather = new RectF(in.left, row1 + gap, mid, row2);
            status = new RectF(mid + gap, row1 + gap, in.right, row2);
            calendar = new RectF(in.left, row2 + gap, mid, in.bottom);
            notes = new RectF(mid + gap, row2 + gap, in.right, in.bottom);
        }

        for (RectF card : new RectF[]{clock, weather, calendar, status, notes}) {
            drawCard(c, card);
        }
        drawClock(c, clock, s);
        drawWeather(c, weather, s);
        drawCalendar(c, calendar);
        drawStatus(c, status, s.metrics);
        drawNotifications(c, notes, s);
    }

    // ------------------------------------------------------------ background

    private void drawWallpaper(Canvas c, int[] colors) {
        int cw = c.getWidth();
        int ch = c.getHeight();
        if (wallShader == null || cw != wallW || ch != wallH || colors != wallColors) {
            wallShader = new LinearGradient(0, 0, cw, ch, colors, null, Shader.TileMode.CLAMP);
            wallW = cw;
            wallH = ch;
            wallColors = colors;
        }
        wallpaperPaint.setShader(wallShader);
        c.drawRect(0, 0, cw, ch, wallpaperPaint);
    }

    private void drawCard(Canvas c, RectF r) {
        fill.setColor(pal.card);
        if (pal.cardShadow != 0) {
            fill.setShadowLayer(u * 0.025f, 0f, u * 0.006f, pal.cardShadow);
        }
        c.drawRoundRect(r, radius, radius, fill);
        fill.clearShadowLayer();
        stroke.setColor(pal.cardEdge);
        stroke.setStrokeWidth(Math.max(1f, u * 0.002f));
        c.drawRoundRect(r, radius, radius, stroke);
    }

    /** Draws a card's small heading and returns the y where content can start. */
    private float drawHeader(Canvas c, RectF r, String left, int leftColor, String right) {
        float baseline = r.top + pad + headerSize * 0.75f;
        if (right != null) {
            setText(fonts.semibold, headerSize, pal.secondary, Paint.Align.RIGHT);
            c.drawText(right, r.right - pad, baseline, text);
        }
        float rightWidth = right == null ? 0f : text.measureText(right) + pad * 0.5f;
        setText(fonts.bold, headerSize, leftColor, Paint.Align.LEFT);
        c.drawText(ellipsize(left, r.width() - 2 * pad - rightWidth), r.left + pad, baseline, text);
        return baseline + headerSize * 0.75f;
    }

    // ----------------------------------------------------------------- clock

    private void drawClock(Canvas c, RectF r, DashboardState s) {
        int hour24 = cal.get(Calendar.HOUR_OF_DAY);
        String greeting = hour24 < 5 ? "Good night" : hour24 < 12 ? "Good morning"
                : hour24 < 17 ? "Good afternoon" : hour24 < 22 ? "Good evening" : "Good night";
        float top = drawHeader(c, r, greeting, pal.secondary, null);

        int hour = s.is24h ? hour24 : cal.get(Calendar.HOUR);
        if (!s.is24h && hour == 0) {
            hour = 12;
        }
        String hm = String.format(Locale.getDefault(), s.is24h ? "%02d:%02d" : "%d:%02d",
                hour, cal.get(Calendar.MINUTE));
        String ampm = s.is24h ? null : (cal.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM");
        String date = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
                .format(new Date(s.now));

        float maxW = r.width() - 2 * pad;
        float dateSize = Math.min(u * 0.052f, r.height() * 0.13f);
        setText(fonts.bold, dateSize, pal.primary, Paint.Align.LEFT);
        fitTextSize(date, dateSize, maxW);
        dateSize = text.getTextSize();

        // Fit the time to the space left, in width and height.
        setText(fonts.display, 100f, pal.primary, Paint.Align.LEFT);
        float refW = text.measureText(hm);
        if (ampm != null) {
            text.setTextSize(28f);
            refW += 6f + text.measureText(ampm);
        }
        float availH = r.bottom - pad - top;
        float dateBlock = dateSize * 1.75f;
        float size = Math.min(100f * maxW / refW, (availH - dateBlock) / 0.74f);

        float groupH = size * 0.72f + dateBlock;
        float timeBaseline = top + (availH - groupH) / 2f + size * 0.72f;
        setText(fonts.display, size, pal.primary, Paint.Align.LEFT);
        c.drawText(hm, r.left + pad, timeBaseline, text);
        if (ampm != null) {
            float x = r.left + pad + text.measureText(hm) + size * 0.06f;
            setText(fonts.bold, size * 0.28f, pal.secondary, Paint.Align.LEFT);
            c.drawText(ampm, x, timeBaseline, text);
        }

        setText(fonts.bold, dateSize, pal.primary, Paint.Align.LEFT);
        c.drawText(date, r.left + pad, timeBaseline + dateBlock - dateSize * 0.2f, text);
    }

    // --------------------------------------------------------------- weather

    private void drawWeather(Canvas c, RectF r, DashboardState s) {
        Weather w = s.weather;
        if (s.weatherStatus != WeatherRepository.Status.READY || w == null) {
            float top = drawHeader(c, r, "Weather", pal.secondary, null);
            String title;
            String detail;
            switch (s.weatherStatus) {
                case NO_PERMISSION:
                    title = "Location is off";
                    detail = "Allow it in the phone app";
                    break;
                case ERROR:
                    title = "Weather unavailable";
                    detail = "Will try again soon";
                    break;
                default:
                    title = "Loading weather…";
                    detail = null;
                    break;
            }
            drawMessage(c, r, top, title, detail);
            return;
        }

        String place = w.place == null || w.place.isEmpty() ? "Weather" : w.place;
        String hiLo = "H " + Weather.formatTemp(w.highC, s.fahrenheit)
                + "  L " + Weather.formatTemp(w.lowC, s.fahrenheit);
        float top = drawHeader(c, r, place, pal.secondary, null);
        float bottom = r.bottom - pad;

        boolean showHours = !w.hours.isEmpty() && bottom - top > u * 0.22f;
        float hoursH = showHours ? (bottom - top) * 0.44f : 0f;
        RectF now = new RectF(r.left + pad, top, r.right - pad, bottom - hoursH - pad * 0.3f);

        // Now: icon, big temperature, then condition and high/low stacked beside it.
        float iconSize = Math.min(now.height() * 0.95f, now.width() * 0.22f);
        float cy = now.centerY();
        weatherIcons.draw(c, Weather.icon(w.code), w.isDay, now.left + iconSize / 2f, cy, iconSize, pal);

        String temp = Weather.formatTemp(w.tempC, s.fahrenheit);
        float tempSize = Math.min(now.height() * 0.8f, now.width() * 0.22f);
        setText(fonts.display, tempSize, pal.primary, Paint.Align.LEFT);
        float tempX = now.left + iconSize + pad * 0.4f;
        c.drawText(temp, tempX, cy + tempSize * 0.36f, text);
        float infoLeft = tempX + text.measureText(temp) + pad * 0.5f;

        float infoW = now.right - infoLeft;
        if (infoW > u * 0.08f) {
            String condition = Weather.describe(w.code);
            setText(fonts.bold, u * 0.042f, pal.primary, Paint.Align.LEFT);
            fitTextSize(condition, u * 0.042f, infoW);
            c.drawText(condition, infoLeft, cy - u * 0.004f, text);
            setText(fonts.semibold, u * 0.036f, pal.secondary, Paint.Align.LEFT);
            fitTextSize(hiLo, u * 0.036f, infoW);
            c.drawText(hiLo, infoLeft, cy + u * 0.046f, text);
        }

        if (!showHours) {
            return;
        }
        float hoursTop = bottom - hoursH;
        stroke.setColor(pal.separator);
        stroke.setStrokeWidth(Math.max(1f, u * 0.002f));
        c.drawLine(r.left + pad, hoursTop, r.right - pad, hoursTop, stroke);

        int n = w.hours.size();
        float colW = (r.width() - 2 * pad) / n;
        float labelSize = Math.min(hoursH * 0.2f, u * 0.034f);
        float hourTempSize = Math.min(hoursH * 0.22f, u * 0.04f);
        float hourIcon = hoursH * 0.32f;
        for (int i = 0; i < n; i++) {
            Weather.Hour hr = w.hours.get(i);
            float x = r.left + pad + colW * (i + 0.5f);
            String label = s.is24h ? String.format(Locale.getDefault(), "%02d", hr.hour)
                    : (hr.hour % 12 == 0 ? 12 : hr.hour % 12) + (hr.hour < 12 ? " AM" : " PM");
            setText(fonts.semibold, labelSize, pal.secondary, Paint.Align.CENTER);
            c.drawText(label, x, hoursTop + hoursH * 0.3f, text);
            weatherIcons.draw(c, Weather.icon(hr.code), hr.isDay, x, hoursTop + hoursH * 0.58f,
                    hourIcon, pal);
            setText(fonts.bold, hourTempSize, pal.primary, Paint.Align.CENTER);
            c.drawText(Weather.formatTemp(hr.tempC, s.fahrenheit), x, bottom - hoursH * 0.02f, text);
        }
    }

    // -------------------------------------------------------------- calendar

    private void drawCalendar(Canvas c, RectF r) {
        String month = new SimpleDateFormat("LLLL", Locale.getDefault()).format(cal.getTime());
        float top = drawHeader(c, r, month, pal.red, String.valueOf(cal.get(Calendar.YEAR)));

        Calendar first = (Calendar) cal.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = cal.getFirstDayOfWeek();
        int offset = (first.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7;
        int days = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        int today = cal.get(Calendar.DAY_OF_MONTH);
        int rows = (offset + days + 6) / 7;

        float left = r.left + pad * 0.6f;
        float colW = (r.width() - pad * 1.2f) / 7f;
        float bottom = r.bottom - pad * 0.6f;
        float weekdayH = (bottom - top) * 0.15f;
        float rowH = (bottom - top - weekdayH) / rows;
        float cell = Math.min(colW, rowH);

        String[] names = new DateFormatSymbols(Locale.getDefault()).getShortWeekdays();
        setText(fonts.bold, Math.min(weekdayH * 0.55f, u * 0.032f), pal.secondary, Paint.Align.CENTER);
        for (int col = 0; col < 7; col++) {
            int dow = dayOfWeek(firstDayOfWeek, col);
            String name = names[dow].isEmpty() ? "" : names[dow].substring(0, 1).toUpperCase(Locale.getDefault());
            drawCenteredY(c, name, left + colW * (col + 0.5f), top + weekdayH / 2f);
        }

        float numberSize = cell * 0.44f;
        for (int day = 1; day <= days; day++) {
            int idx = offset + day - 1;
            int col = idx % 7;
            float cx = left + colW * (col + 0.5f);
            float cy = top + weekdayH + rowH * (idx / 7 + 0.5f);
            if (day == today) {
                fill.setColor(pal.red);
                c.drawCircle(cx, cy, cell * 0.42f, fill);
                setText(fonts.bold, numberSize, 0xFFFFFFFF, Paint.Align.CENTER);
            } else {
                boolean weekend = isWeekend(dayOfWeek(firstDayOfWeek, col));
                setText(fonts.semibold, numberSize, weekend ? pal.secondary : pal.primary,
                        Paint.Align.CENTER);
            }
            drawCenteredY(c, Integer.toString(day), cx, cy);
        }
    }

    // ----------------------------------------------------------------- status

    private void drawStatus(Canvas c, RectF r, DeviceMetrics.Snapshot m) {
        float top = drawHeader(c, r, "Phone", pal.secondary, null);
        float bottom = r.bottom - pad * 0.8f;
        float labelSize = u * 0.032f;
        float ringAreaH = bottom - top - labelSize * 1.5f;
        float colW = (r.width() - 2 * pad) / 3f;
        float d = Math.min(colW * 0.74f, ringAreaH * 0.92f);
        float cy = top + ringAreaH / 2f;

        boolean battery = m != null && m.batteryPercent >= 0;
        float batteryRatio = battery ? m.batteryPercent / 100f : 0f;
        int batteryColor = battery && m.batteryPercent <= 20 && !m.charging ? pal.red : pal.green;
        String batteryLabel = battery && m.charging ? "Charging" : "Battery";

        boolean memory = m != null && m.ramTotal > 0;
        float memoryRatio = memory ? 1f - (float) m.ramAvailable / m.ramTotal : 0f;
        boolean storage = m != null && m.storageTotal > 0;
        float storageRatio = storage ? 1f - (float) m.storageAvailable / m.storageTotal : 0f;
        String storageValue = storage ? Math.round((double) m.storageAvailable / GB) + "G" : "–";

        ring(c, r.left + pad + colW * 0.5f, cy, d, batteryRatio, batteryColor,
                battery ? m.batteryPercent + "%" : "–", batteryLabel, labelSize, colW * 0.9f);
        ring(c, r.left + pad + colW * 1.5f, cy, d, memoryRatio, pal.accent,
                memory ? Math.round(memoryRatio * 100f) + "%" : "–", "Memory", labelSize, colW * 0.9f);
        ring(c, r.left + pad + colW * 2.5f, cy, d, storageRatio, pal.indigo,
                storageValue, "Free", labelSize, colW * 0.9f);
    }

    private void ring(Canvas c, float cx, float cy, float d, float ratio, int color, String value,
                      String label, float labelSize, float labelWidth) {
        float sw = d * 0.11f;
        float rad = (d - sw) / 2f;
        RectF oval = new RectF(cx - rad, cy - rad, cx + rad, cy + rad);
        stroke.setStrokeWidth(sw);
        stroke.setColor(pal.track);
        c.drawOval(oval, stroke);
        float clamped = Math.max(0f, Math.min(1f, ratio));
        if (clamped > 0f) {
            stroke.setColor(color);
            c.drawArc(oval, -90f, 360f * clamped, false, stroke);
        }
        setText(fonts.bold, d * 0.25f, pal.primary, Paint.Align.CENTER);
        fitTextSize(value, d * 0.25f, d - sw * 3f);
        drawCenteredY(c, value, cx, cy);
        setText(fonts.semibold, labelSize, pal.secondary, Paint.Align.CENTER);
        fitTextSize(label, labelSize, labelWidth);
        c.drawText(label, cx, cy + d / 2f + labelSize * 1.3f, text);
    }

    // --------------------------------------------------------- notifications

    private void drawNotifications(Canvas c, RectF r, DashboardState s) {
        List<NotificationStore.Item> items = s.notifications;
        String count = s.notificationAccess && !items.isEmpty() ? String.valueOf(items.size()) : null;
        float top = drawHeader(c, r, "Notifications", pal.secondary, count);

        if (!s.notificationAccess) {
            drawMessage(c, r, top, "Notification access is off", "Turn it on in the phone app");
            return;
        }
        if (items.isEmpty()) {
            float cy = top + (r.bottom - pad - top) / 2f - u * 0.04f;
            float rr = u * 0.045f;
            stroke.setColor(pal.green);
            stroke.setStrokeWidth(rr * 0.22f);
            c.drawCircle(r.centerX(), cy, rr, stroke);
            c.drawLine(r.centerX() - rr * 0.42f, cy + rr * 0.02f, r.centerX() - rr * 0.1f, cy + rr * 0.34f, stroke);
            c.drawLine(r.centerX() - rr * 0.1f, cy + rr * 0.34f, r.centerX() + rr * 0.45f, cy - rr * 0.3f, stroke);
            setText(fonts.bold, u * 0.04f, pal.secondary, Paint.Align.CENTER);
            c.drawText("All caught up", r.centerX(), cy + rr + u * 0.065f, text);
            return;
        }

        float itemH = u * 0.155f;
        float avail = r.bottom - pad * 0.6f - top;
        int n = Math.max(1, Math.min(items.size(), (int) (avail / itemH)));
        float iconSize = itemH * 0.5f;
        float textLeft = r.left + pad + iconSize + pad * 0.6f;
        float maxW = r.right - pad - textLeft;
        for (int i = 0; i < n; i++) {
            NotificationStore.Item item = items.get(i);
            float y = top + i * itemH;
            drawAppIcon(c, item, r.left + pad, y + (itemH - iconSize) / 2f - itemH * 0.04f, iconSize);

            String when = timeAgo(item.time, s.now, s.is24h);
            setText(fonts.semibold, itemH * 0.17f, pal.secondary, Paint.Align.RIGHT);
            c.drawText(when, r.right - pad, y + itemH * 0.28f, text);
            float whenW = text.measureText(when) + pad * 0.4f;
            text.setTextAlign(Paint.Align.LEFT);
            c.drawText(ellipsize(item.app, maxW - whenW), textLeft, y + itemH * 0.28f, text);

            String title = item.title.isEmpty() ? item.app : item.title;
            setText(fonts.bold, itemH * 0.21f, pal.primary, Paint.Align.LEFT);
            c.drawText(ellipsize(title, maxW), textLeft, y + itemH * 0.56f, text);

            if (!item.text.isEmpty()) {
                setText(fonts.semibold, itemH * 0.185f, pal.secondary, Paint.Align.LEFT);
                c.drawText(ellipsize(item.text, maxW), textLeft, y + itemH * 0.82f, text);
            }
            if (i < n - 1) {
                stroke.setColor(pal.separator);
                stroke.setStrokeWidth(Math.max(1f, u * 0.002f));
                c.drawLine(textLeft, y + itemH, r.right - pad, y + itemH, stroke);
            }
        }
    }

    private void drawAppIcon(Canvas c, NotificationStore.Item item, float x, float y, float size) {
        RectF box = new RectF(x, y, x + size, y + size);
        float corner = size * 0.24f;
        Bitmap icon = item.icon;
        if (icon != null) {
            BitmapShader shader = new BitmapShader(icon, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            matrix.setScale(size / icon.getWidth(), size / icon.getHeight());
            matrix.postTranslate(x, y);
            shader.setLocalMatrix(matrix);
            iconPaint.setShader(shader);
            c.drawRoundRect(box, corner, corner, iconPaint);
            iconPaint.setShader(null);
        } else {
            fill.setColor(pal.accent);
            c.drawRoundRect(box, corner, corner, fill);
            String letter = item.app.isEmpty() ? "?" : item.app.substring(0, 1).toUpperCase(Locale.getDefault());
            setText(fonts.bold, size * 0.5f, 0xFFFFFFFF, Paint.Align.CENTER);
            drawCenteredY(c, letter, box.centerX(), box.centerY());
        }
    }

    private static String timeAgo(long time, long now, boolean is24h) {
        long minutes = Math.max(0L, (now - time) / 60000L);
        if (minutes < 1) return "now";
        if (minutes < 60) return minutes + "m";
        if (minutes < 12 * 60) return (minutes / 60) + "h";
        return new SimpleDateFormat(is24h ? "HH:mm" : "h:mm a", Locale.getDefault()).format(new Date(time));
    }

    // ---------------------------------------------------------------- helpers

    private void drawMessage(Canvas c, RectF r, float top, String title, String detail) {
        float cy = top + (r.bottom - pad - top) / 2f;
        float maxW = r.width() - 2 * pad;
        setText(fonts.bold, u * 0.045f, pal.primary, Paint.Align.CENTER);
        fitTextSize(title, u * 0.045f, maxW);
        c.drawText(title, r.centerX(), detail == null ? cy + u * 0.015f : cy - u * 0.005f, text);
        if (detail != null) {
            setText(fonts.semibold, u * 0.036f, pal.secondary, Paint.Align.CENTER);
            fitTextSize(detail, u * 0.036f, maxW);
            c.drawText(detail, r.centerX(), cy + u * 0.05f, text);
        }
    }

    private void setText(Typeface tf, float size, int color, Paint.Align align) {
        text.setTypeface(tf);
        text.setTextSize(size);
        text.setColor(color);
        text.setTextAlign(align);
    }

    private void fitTextSize(String s, float maxSize, float maxWidth) {
        text.setTextSize(maxSize);
        float width = text.measureText(s);
        if (width > maxWidth && width > 0f) {
            text.setTextSize(maxSize * maxWidth / width);
        }
    }

    private String ellipsize(String s, float maxWidth) {
        return TextUtils.ellipsize(s, text, Math.max(0f, maxWidth), TextUtils.TruncateAt.END).toString();
    }

    private void drawCenteredY(Canvas c, String s, float x, float cy) {
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(s, x, cy - (fm.ascent + fm.descent) / 2f, text);
    }

    private static int dayOfWeek(int firstDayOfWeek, int col) {
        return ((firstDayOfWeek - 1 + col) % 7) + 1;
    }

    private static boolean isWeekend(int dayOfWeek) {
        return dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY;
    }
}
