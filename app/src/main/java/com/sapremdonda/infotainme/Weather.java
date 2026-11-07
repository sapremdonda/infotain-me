package com.sapremdonda.infotainme;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One weather reading from Open-Meteo: now, today's high/low and the next few hours. */
public final class Weather {

    public enum Icon { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, THUNDER }

    public static final class Hour {
        public final int hour;
        public final double tempC;
        public final int code;
        public final boolean isDay;

        Hour(int hour, double tempC, int code, boolean isDay) {
            this.hour = hour;
            this.tempC = tempC;
            this.code = code;
            this.isDay = isDay;
        }
    }

    public final double tempC;
    public final int code;
    public final boolean isDay;
    public final double highC;
    public final double lowC;
    public final String place;
    public final long fetchedAt;
    public final List<Hour> hours;

    Weather(double tempC, int code, boolean isDay, double highC, double lowC, String place,
            long fetchedAt, List<Hour> hours) {
        this.tempC = tempC;
        this.code = code;
        this.isDay = isDay;
        this.highC = highC;
        this.lowC = lowC;
        this.place = place;
        this.fetchedAt = fetchedAt;
        this.hours = Collections.unmodifiableList(hours);
    }

    /** Parses an Open-Meteo forecast response (temperatures in Celsius). */
    static Weather parse(String json, String place, long fetchedAt) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject current = root.getJSONObject("current");
        String currentTime = current.optString("time", "");

        JSONObject daily = root.getJSONObject("daily");
        double high = daily.getJSONArray("temperature_2m_max").getDouble(0);
        double low = daily.getJSONArray("temperature_2m_min").getDouble(0);

        JSONObject hourly = root.getJSONObject("hourly");
        JSONArray times = hourly.getJSONArray("time");
        JSONArray temps = hourly.getJSONArray("temperature_2m");
        JSONArray codes = hourly.getJSONArray("weather_code");
        JSONArray days = hourly.optJSONArray("is_day");
        List<Hour> hours = new ArrayList<>();
        for (int i = 0; i < times.length() && hours.size() < 5; i++) {
            String t = times.getString(i); // e.g. 2026-11-07T19:00, local time
            if (t.compareTo(currentTime) <= 0 || t.length() < 13) {
                continue;
            }
            hours.add(new Hour(Integer.parseInt(t.substring(11, 13)), temps.getDouble(i),
                    codes.getInt(i), days == null || days.optInt(i, 1) == 1));
        }

        return new Weather(current.getDouble("temperature_2m"), current.getInt("weather_code"),
                current.optInt("is_day", 1) == 1, high, low, place, fetchedAt, hours);
    }

    public static Icon icon(int code) {
        if (code <= 1) return Icon.CLEAR;
        if (code == 2) return Icon.PARTLY;
        if (code == 3) return Icon.CLOUDY;
        if (code == 45 || code == 48) return Icon.FOG;
        if (code >= 51 && code <= 57) return Icon.DRIZZLE;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return Icon.RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return Icon.SNOW;
        if (code >= 95) return Icon.THUNDER;
        return Icon.CLOUDY;
    }

    public static String describe(int code) {
        switch (code) {
            case 0: return "Clear";
            case 1: return "Mostly clear";
            case 2: return "Partly cloudy";
            case 3: return "Cloudy";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: return "Drizzle";
            case 56: case 57: return "Freezing drizzle";
            case 61: return "Light rain";
            case 63: return "Rain";
            case 65: return "Heavy rain";
            case 66: case 67: return "Freezing rain";
            case 71: return "Light snow";
            case 73: return "Snow";
            case 75: return "Heavy snow";
            case 77: return "Snow grains";
            case 80: case 81: return "Showers";
            case 82: return "Heavy showers";
            case 85: case 86: return "Snow showers";
            case 95: return "Thunderstorm";
            case 96: case 99: return "Thunderstorm, hail";
            default: return "Cloudy";
        }
    }

    public static String formatTemp(double celsius, boolean fahrenheit) {
        double value = fahrenheit ? celsius * 9.0 / 5.0 + 32.0 : celsius;
        return Math.round(value) + "°";
    }
}
