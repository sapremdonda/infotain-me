package com.sapremdonda.infotainme.car;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.AppManager;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.sapremdonda.infotainme.DashboardRenderer;
import com.sapremdonda.infotainme.DeviceMetrics;

/**
 * The car screen. As a navigation-category app we get a raw drawing surface (normally used
 * for maps), and draw the dashboard on it with the same renderer as the phone.
 */
public final class DashboardScreen extends Screen implements SurfaceCallback, DefaultLifecycleObserver {

    private static final String TAG = "InfotainCar";
    private static final long METRICS_INTERVAL_MS = 5000L;

    private final DashboardRenderer renderer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable private SurfaceContainer surfaceContainer;
    @Nullable private Rect visibleArea;
    @Nullable private DeviceMetrics.Snapshot metrics;
    private long lastMetricsRead;
    private boolean running;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            render();
            // Car screens are slower to redraw, so update about four times a second.
            long now = System.currentTimeMillis();
            handler.postDelayed(this, Math.min(250L, 1000L - now % 1000L));
        }
    };

    public DashboardScreen(@NonNull CarContext carContext) {
        super(carContext);
        renderer = new DashboardRenderer(carContext);
        getLifecycle().addObserver(this);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        Action refresh = new Action.Builder()
                .setTitle("Refresh")
                .setOnClickListener(() -> {
                    lastMetricsRead = 0;
                    render();
                })
                .build();
        return new NavigationTemplate.Builder()
                .setActionStrip(new ActionStrip.Builder().addAction(refresh).build())
                .build();
    }

    // ----------------------------------------------------------- lifecycle

    @Override
    public void onCreate(@NonNull LifecycleOwner owner) {
        getCarContext().getCarService(AppManager.class).setSurfaceCallback(this);
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        running = true;
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        running = false;
        handler.removeCallbacks(tick);
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        getCarContext().getCarService(AppManager.class).setSurfaceCallback(null);
    }

    // ------------------------------------------------------------- surface

    @Override
    public void onSurfaceAvailable(@NonNull SurfaceContainer container) {
        surfaceContainer = container;
        render();
    }

    @Override
    public void onVisibleAreaChanged(@NonNull Rect area) {
        visibleArea = new Rect(area);
        render();
    }

    @Override
    public void onStableAreaChanged(@NonNull Rect area) {
        // The visible area is all we need.
    }

    @Override
    public void onSurfaceDestroyed(@NonNull SurfaceContainer container) {
        surfaceContainer = null;
    }

    private void render() {
        SurfaceContainer container = surfaceContainer;
        if (container == null) {
            return;
        }
        Surface surface = container.getSurface();
        if (surface == null || !surface.isValid()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (metrics == null || now - lastMetricsRead >= METRICS_INTERVAL_MS) {
            metrics = DeviceMetrics.read(getCarContext());
            lastMetricsRead = now;
        }

        Rect area = visibleArea != null && !visibleArea.isEmpty()
                ? visibleArea
                : new Rect(0, 0, container.getWidth(), container.getHeight());

        Canvas canvas;
        try {
            canvas = surface.lockCanvas(null);
        } catch (IllegalArgumentException | IllegalStateException e) {
            Log.w(TAG, "Could not lock car surface", e);
            return;
        }
        try {
            renderer.draw(canvas, area, metrics, now);
        } finally {
            surface.unlockCanvasAndPost(canvas);
        }
    }
}
