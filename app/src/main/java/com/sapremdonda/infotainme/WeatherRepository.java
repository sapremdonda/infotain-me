package com.sapremdonda.infotainme;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches weather from Open-Meteo (free, no API key) for the phone's location.
 * Refreshes every 15 minutes and keeps the last result so the car shows it straight away.
 */
public final class WeatherRepository {

    public enum Status { NO_PERMISSION, LOADING, READY, ERROR }

    private static final String TAG = "InfotainWeather";
    private static final long REFRESH_MS = 15 * 60 * 1000L;
    private static final long RETRY_MS = 60 * 1000L;
    private static final long FRESH_LOCATION_MS = 30 * 60 * 1000L;

    private static WeatherRepository instance;

    private final Context app;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile Weather weather;
    private volatile boolean fetching;
    private volatile boolean lastFailed;
    private volatile long lastAttempt;

    private WeatherRepository(Context context) {
        app = context.getApplicationContext();
        SharedPreferences sp = Prefs.sp(app);
        String json = sp.getString("weather_json", null);
        if (json != null) {
            try {
                weather = Weather.parse(json, sp.getString("weather_place", ""),
                        sp.getLong("weather_time", 0));
            } catch (Exception ignored) {
                // Bad cache; fetch again.
            }
        }
    }

    public static synchronized WeatherRepository get(Context context) {
        if (instance == null) {
            instance = new WeatherRepository(context);
        }
        return instance;
    }

    public static boolean hasLocationPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public Weather weather() {
        return weather;
    }

    public Status status() {
        if (weather != null) {
            return Status.READY;
        }
        if (!hasLocationPermission(app) && !Prefs.sp(app).contains("last_lat")) {
            return Status.NO_PERMISSION;
        }
        return lastFailed ? Status.ERROR : Status.LOADING;
    }

    /** Cheap to call often: only starts a fetch when the data is old. */
    public void refreshIfStale() {
        long now = System.currentTimeMillis();
        Weather w = weather;
        if (fetching || (w != null && now - w.fetchedAt < REFRESH_MS) || now - lastAttempt < RETRY_MS) {
            return;
        }
        start(now);
    }

    /** Fetches now, e.g. right after location permission is granted. */
    public void refreshNow() {
        if (!fetching) {
            start(System.currentTimeMillis());
        }
    }

    private void start(long now) {
        lastAttempt = now;
        fetching = true;
        executor.execute(this::fetch);
    }

    private void fetch() {
        try {
            SharedPreferences sp = Prefs.sp(app);
            Location location = findLocation();
            double lat;
            double lon;
            if (location != null) {
                lat = location.getLatitude();
                lon = location.getLongitude();
                sp.edit().putFloat("last_lat", (float) lat).putFloat("last_lon", (float) lon).apply();
            } else if (sp.contains("last_lat")) {
                lat = sp.getFloat("last_lat", 0f);
                lon = sp.getFloat("last_lon", 0f);
            } else {
                lastFailed = true;
                return;
            }

            String url = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                            + "&current=temperature_2m,weather_code,is_day"
                            + "&hourly=temperature_2m,weather_code,is_day"
                            + "&daily=temperature_2m_max,temperature_2m_min"
                            + "&timezone=auto&forecast_days=2", lat, lon);
            String json = download(url);
            String place = placeName(lat, lon);
            if (place == null) {
                place = sp.getString("weather_place", "");
            }
            long now = System.currentTimeMillis();
            weather = Weather.parse(json, place, now);
            sp.edit().putString("weather_json", json).putString("weather_place", place)
                    .putLong("weather_time", now).apply();
            lastFailed = false;
        } catch (Exception e) {
            Log.w(TAG, "Weather update failed", e);
            lastFailed = true;
        } finally {
            fetching = false;
        }
    }

    private static String download(String address) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        try {
            if (conn.getResponseCode() != 200) {
                throw new IllegalStateException("HTTP " + conn.getResponseCode());
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    sb.append(line);
                }
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    @SuppressWarnings("deprecation")
    private String placeName(double lat, double lon) {
        if (!Geocoder.isPresent()) {
            return null;
        }
        try {
            List<Address> list = new Geocoder(app, Locale.getDefault()).getFromLocation(lat, lon, 1);
            if (list == null || list.isEmpty()) {
                return null;
            }
            Address a = list.get(0);
            if (a.getLocality() != null) return a.getLocality();
            if (a.getSubAdminArea() != null) return a.getSubAdminArea();
            return a.getAdminArea();
        } catch (Exception e) {
            return null;
        }
    }

    /** Recent last-known location, or a fresh fix (waits up to 10 seconds). Runs off the main thread. */
    @SuppressWarnings({"MissingPermission", "deprecation"})
    private Location findLocation() {
        if (!hasLocationPermission(app)) {
            return null;
        }
        LocationManager lm = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            return null;
        }
        Location best = null;
        for (String provider : lm.getProviders(true)) {
            try {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) {
                    best = l;
                }
            } catch (SecurityException | IllegalArgumentException ignored) {
                // Provider not allowed with our permission level.
            }
        }
        if (best != null && System.currentTimeMillis() - best.getTime() < FRESH_LOCATION_MS) {
            return best;
        }

        String provider = pickProvider(lm);
        if (provider == null) {
            return best;
        }
        AtomicReference<Location> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lm.getCurrentLocation(provider, null, Runnable::run, loc -> {
                    result.set(loc);
                    latch.countDown();
                });
            } else {
                LocationListener listener = loc -> {
                    result.set(loc);
                    latch.countDown();
                };
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper());
            }
            latch.await(10, TimeUnit.SECONDS);
        } catch (SecurityException | IllegalArgumentException | InterruptedException ignored) {
            // Fall back to whatever we had.
        }
        return result.get() != null ? result.get() : best;
    }

    private static String pickProvider(LocationManager lm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
            return LocationManager.FUSED_PROVIDER;
        }
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            return LocationManager.NETWORK_PROVIDER;
        }
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return LocationManager.GPS_PROVIDER;
        }
        return null;
    }
}
