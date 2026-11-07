package com.sapremdonda.infotainme;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;

/** Live preview of the car dashboard, shown in the phone app. */
public final class DashboardView extends View {

    private static final long METRICS_INTERVAL_MS = 5000L;

    private final DashboardRenderer renderer;
    private final Rect area = new Rect();
    private DeviceMetrics.Snapshot metrics;
    private long lastMetricsRead;
    private Boolean nightPreview;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            invalidate();
            postDelayed(this, 1000L - System.currentTimeMillis() % 1000L);
        }
    };

    public DashboardView(Context context) {
        super(context);
        renderer = new DashboardRenderer(context);
    }

    /** Preview as if the car's headlights were on (true) or off (false); null follows the phone. */
    public void setNightPreview(Boolean night) {
        nightPreview = night;
        invalidate();
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
        long now = System.currentTimeMillis();
        if (now - lastMetricsRead >= METRICS_INTERVAL_MS) {
            metrics = DeviceMetrics.read(getContext());
            lastMetricsRead = now;
        }
        boolean night = nightPreview != null ? nightPreview
                : (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        area.set(0, 0, getWidth(), getHeight());
        renderer.draw(canvas, area, DashboardState.capture(getContext(), night, metrics));
    }
}
