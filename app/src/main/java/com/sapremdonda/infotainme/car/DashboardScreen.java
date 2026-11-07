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
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.core.graphics.drawable.IconCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.sapremdonda.infotainme.DashboardRenderer;
import com.sapremdonda.infotainme.DashboardState;
import com.sapremdonda.infotainme.DeviceMetrics;
import com.sapremdonda.infotainme.Prefs;
import com.sapremdonda.infotainme.R;
import com.sapremdonda.infotainme.Wallpaper;

/**
 * The car screen. As a navigation-category app we get a raw drawing surface (normally used
 * for maps) and draw the dashboard on it. Day/night comes from the car: Android Auto reports
 * dark mode when the headlights are on.
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
            handler.postDelayed(this, 1000L - System.currentTimeMillis() % 1000L);
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
        CarContext ctx = getCarContext();
        Action wallpaper = new Action.Builder()
                .setIcon(new CarIcon.Builder(
                        IconCompat.createWithResource(ctx, R.drawable.ic_wallpaper)).build())
                .setOnClickListener(this::nextWallpaper)
                .build();
        Action theme = new Action.Builder()
                .setIcon(new CarIcon.Builder(
                        IconCompat.createWithResource(ctx, R.drawable.ic_theme)).build())
                .setOnClickListener(this::nextTheme)
                .build();
        return new NavigationTemplate.Builder()
                .setActionStrip(new ActionStrip.Builder().addAction(wallpaper).addAction(theme).build())
                .build();
    }

    private void nextWallpaper() {
        CarContext ctx = getCarContext();
        Wallpaper next = Wallpaper.byId(Prefs.wallpaperId(ctx)).next();
        Prefs.setWallpaperId(ctx, next.id);
        CarToast.makeText(ctx, "Wallpaper: " + next.name, CarToast.LENGTH_SHORT).show();
        render();
    }

    private void nextTheme() {
        CarContext ctx = getCarContext();
        int mode = (Prefs.themeMode(ctx) + 1) % 3;
        Prefs.setThemeMode(ctx, mode);
        String name = mode == Prefs.THEME_AUTO ? "Automatic (headlights)"
                : mode == Prefs.THEME_LIGHT ? "Light" : "Dark";
        CarToast.makeText(ctx, "Theme: " + name, CarToast.LENGTH_SHORT).show();
        render();
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

        CarContext ctx = getCarContext();
        long now = System.currentTimeMillis();
        if (metrics == null || now - lastMetricsRead >= METRICS_INTERVAL_MS) {
            metrics = DeviceMetrics.read(ctx);
            lastMetricsRead = now;
        }
        DashboardState state = DashboardState.capture(ctx, ctx.isDarkMode(), metrics);

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
            renderer.draw(canvas, area, state);
        } finally {
            surface.unlockCanvasAndPost(canvas);
        }
    }
}
