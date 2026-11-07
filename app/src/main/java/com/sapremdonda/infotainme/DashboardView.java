package com.sapremdonda.infotainme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;

/** Phone version of the dashboard: redraws every second, refreshes metrics every 5 seconds. */
public final class DashboardView extends View {

    private static final long METRICS_INTERVAL_MS = 5000L;

    private final DashboardRenderer renderer;
    private final Rect area = new Rect();
    private DeviceMetrics.Snapshot metrics;
    private long lastMetricsRead;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            if (now - lastMetricsRead >= METRICS_INTERVAL_MS) {
                metrics = DeviceMetrics.read(getContext());
                lastMetricsRead = now;
            }
            invalidate();
            // Wake again just after the next whole second, plus a few frames for a smooth seconds bar.
            postDelayed(this, Math.min(250L, 1000L - now % 1000L));
        }
    };

    public DashboardView(Context context) {
        super(context);
        renderer = new DashboardRenderer(context);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastMetricsRead = 0;
        post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        area.set(0, 0, getWidth(), getHeight());
        renderer.draw(canvas, area, metrics, System.currentTimeMillis());
    }
}
