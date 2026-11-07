package com.sapremdonda.infotainme;

import android.app.Notification;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Receives the phone's notifications once the user turns on notification access. */
public final class DashboardNotificationListener extends NotificationListenerService {

    private static final int ICON_PX = 128;

    private final Map<String, Bitmap> icons = new HashMap<>();
    private final Map<String, String> labels = new HashMap<>();

    @Override
    public void onListenerConnected() {
        List<NotificationStore.Item> list = new ArrayList<>();
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification sbn : active) {
                    NotificationStore.Item item = toItem(sbn);
                    if (item != null) {
                        list.add(item);
                    }
                }
            }
        } catch (SecurityException ignored) {
            // Access was revoked while connecting.
        }
        NotificationStore.replaceAll(list);
    }

    @Override
    public void onListenerDisconnected() {
        NotificationStore.replaceAll(Collections.emptyList());
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        NotificationStore.Item item = toItem(sbn);
        if (item != null) {
            NotificationStore.put(item);
        } else {
            NotificationStore.remove(sbn.getKey());
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        NotificationStore.remove(sbn.getKey());
    }

    /** Returns null for notifications that shouldn't be shown (ongoing, summaries, empty, our own). */
    private NotificationStore.Item toItem(StatusBarNotification sbn) {
        if (getPackageName().equals(sbn.getPackageName())) {
            return null;
        }
        Notification n = sbn.getNotification();
        if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0
                || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0
                || (n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) {
            return null;
        }
        Bundle extras = n.extras;
        String title = text(extras.getCharSequence(Notification.EXTRA_TITLE));
        String body = text(extras.getCharSequence(Notification.EXTRA_TEXT));
        if (body.isEmpty()) {
            body = text(extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        }
        if (title.isEmpty() && body.isEmpty()) {
            return null;
        }
        String pkg = sbn.getPackageName();
        return new NotificationStore.Item(sbn.getKey(), appLabel(pkg), title, body,
                sbn.getPostTime(), appIcon(pkg));
    }

    private static String text(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim().replace('\n', ' ');
    }

    private String appLabel(String pkg) {
        String label = labels.get(pkg);
        if (label == null) {
            try {
                PackageManager pm = getPackageManager();
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                label = pm.getApplicationLabel(info).toString();
            } catch (PackageManager.NameNotFoundException e) {
                label = pkg;
            }
            labels.put(pkg, label);
        }
        return label;
    }

    private Bitmap appIcon(String pkg) {
        if (icons.containsKey(pkg)) {
            return icons.get(pkg);
        }
        Bitmap bitmap = null;
        try {
            Drawable d = getPackageManager().getApplicationIcon(pkg);
            bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bitmap);
            d.setBounds(0, 0, ICON_PX, ICON_PX);
            d.draw(c);
        } catch (PackageManager.NameNotFoundException ignored) {
            // Drawn as a letter instead.
        }
        icons.put(pkg, bitmap);
        return bitmap;
    }
}
