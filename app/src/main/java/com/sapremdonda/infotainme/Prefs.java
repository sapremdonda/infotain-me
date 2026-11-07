package com.sapremdonda.infotainme;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** User settings, shared by the phone app and the car screen. */
public final class Prefs {

    public static final int THEME_AUTO = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    public static final int UNITS_AUTO = 0;
    public static final int UNITS_CELSIUS = 1;
    public static final int UNITS_FAHRENHEIT = 2;

    private static final List<String> FAHRENHEIT_COUNTRIES =
            Arrays.asList("US", "LR", "MM", "BS", "BZ", "KY", "PW");

    private Prefs() {
    }

    static SharedPreferences sp(Context context) {
        return context.getApplicationContext().getSharedPreferences("infotain", Context.MODE_PRIVATE);
    }

    public static int themeMode(Context c) {
        return sp(c).getInt("theme_mode", THEME_AUTO);
    }

    public static void setThemeMode(Context c, int mode) {
        sp(c).edit().putInt("theme_mode", mode).apply();
    }

    public static String wallpaperId(Context c) {
        return sp(c).getString("wallpaper", Wallpaper.ALL[0].id);
    }

    public static void setWallpaperId(Context c, String id) {
        sp(c).edit().putString("wallpaper", id).apply();
    }

    public static int units(Context c) {
        return sp(c).getInt("units", UNITS_AUTO);
    }

    public static void setUnits(Context c, int units) {
        sp(c).edit().putInt("units", units).apply();
    }

    public static boolean useFahrenheit(Context c) {
        int units = units(c);
        if (units == UNITS_AUTO) {
            return FAHRENHEIT_COUNTRIES.contains(Locale.getDefault().getCountry());
        }
        return units == UNITS_FAHRENHEIT;
    }

    public static boolean askedLocation(Context c) {
        return sp(c).getBoolean("asked_location", false);
    }

    public static void setAskedLocation(Context c) {
        sp(c).edit().putBoolean("asked_location", true).apply();
    }
}
