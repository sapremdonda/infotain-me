package com.sapremdonda.infotainme;

import android.app.ActivityManager;
import android.content.Context;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;

/** Reads battery, memory and storage numbers from the phone. */
public final class DeviceMetrics {

    /** One reading of the phone's stats. Values are -1 or 0 when unavailable. */
    public static final class Snapshot {
        public final int batteryPercent;
        public final boolean charging;
        public final long ramTotal;
        public final long ramAvailable;
        public final long storageTotal;
        public final long storageAvailable;

        Snapshot(int batteryPercent, boolean charging, long ramTotal, long ramAvailable,
                 long storageTotal, long storageAvailable) {
            this.batteryPercent = batteryPercent;
            this.charging = charging;
            this.ramTotal = ramTotal;
            this.ramAvailable = ramAvailable;
            this.storageTotal = storageTotal;
            this.storageAvailable = storageAvailable;
        }
    }

    private DeviceMetrics() {
    }

    public static Snapshot read(Context context) {
        int battery = -1;
        boolean charging = false;
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            charging = bm.isCharging();
        }
        if (battery < 0 || battery > 100) {
            battery = -1;
        }

        long ramTotal = 0;
        long ramAvailable = 0;
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            ramTotal = info.totalMem;
            ramAvailable = info.availMem;
        }

        long storageTotal = 0;
        long storageAvailable = 0;
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            storageTotal = fs.getTotalBytes();
            storageAvailable = fs.getAvailableBytes();
        } catch (IllegalArgumentException ignored) {
            // Leave storage at zero; the dashboard shows "--".
        }

        return new Snapshot(battery, charging, ramTotal, ramAvailable, storageTotal, storageAvailable);
    }
}
