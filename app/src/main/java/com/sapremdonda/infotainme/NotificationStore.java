package com.sapremdonda.infotainme;

import android.content.Context;
import android.graphics.Bitmap;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/** Phone notifications currently shown, newest first. Filled by {@link DashboardNotificationListener}. */
public final class NotificationStore {

    public static final class Item {
        public final String key;
        public final String app;
        public final String title;
        public final String text;
        public final long time;
        public final Bitmap icon;

        public Item(String key, String app, String title, String text, long time, Bitmap icon) {
            this.key = key;
            this.app = app;
            this.title = title;
            this.text = text;
            this.time = time;
            this.icon = icon;
        }
    }

    private static final int MAX_ITEMS = 30;
    private static final List<Item> items = new ArrayList<>();

    private NotificationStore() {
    }

    public static synchronized List<Item> snapshot() {
        return new ArrayList<>(items);
    }

    static synchronized void replaceAll(List<Item> list) {
        items.clear();
        items.addAll(list);
        tidy();
    }

    static synchronized void put(Item item) {
        removeKey(item.key);
        items.add(item);
        tidy();
    }

    static synchronized void remove(String key) {
        removeKey(key);
    }

    private static void removeKey(String key) {
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).key.equals(key)) {
                items.remove(i);
            }
        }
    }

    private static void tidy() {
        items.sort((a, b) -> Long.compare(b.time, a.time));
        while (items.size() > MAX_ITEMS) {
            items.remove(items.size() - 1);
        }
    }

    /** True when the user has turned on notification access for this app. */
    public static boolean isAccessGranted(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(),
                "enabled_notification_listeners");
        return enabled != null && enabled.contains(context.getPackageName() + "/");
    }
}
