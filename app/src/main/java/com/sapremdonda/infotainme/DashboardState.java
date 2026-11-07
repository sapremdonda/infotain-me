package com.sapremdonda.infotainme;

import android.content.Context;
import android.text.format.DateFormat;

import java.util.Collections;
import java.util.List;

/** Everything the dashboard needs for one frame. */
public final class DashboardState {

    public long now;
    public boolean dark;
    public Wallpaper wallpaper = Wallpaper.ALL[0];
    public boolean is24h;
    public boolean fahrenheit;
    public DeviceMetrics.Snapshot metrics;
    public WeatherRepository.Status weatherStatus = WeatherRepository.Status.LOADING;
    public Weather weather;
    public boolean notificationAccess;
    public List<NotificationStore.Item> notifications = Collections.emptyList();

    /**
     * @param environmentDark whether the car (headlights) or phone is in night mode;
     *                        used when the theme setting is Automatic.
     */
    public static DashboardState capture(Context context, boolean environmentDark,
                                         DeviceMetrics.Snapshot metrics) {
        DashboardState s = new DashboardState();
        s.now = System.currentTimeMillis();
        int mode = Prefs.themeMode(context);
        s.dark = mode == Prefs.THEME_AUTO ? environmentDark : mode == Prefs.THEME_DARK;
        s.wallpaper = Wallpaper.byId(Prefs.wallpaperId(context));
        s.is24h = DateFormat.is24HourFormat(context);
        s.fahrenheit = Prefs.useFahrenheit(context);
        s.metrics = metrics;
        WeatherRepository repo = WeatherRepository.get(context);
        repo.refreshIfStale();
        s.weatherStatus = repo.status();
        s.weather = repo.weather();
        s.notificationAccess = NotificationStore.isAccessGranted(context);
        s.notifications = NotificationStore.snapshot();
        return s;
    }
}
