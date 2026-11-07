package com.sapremdonda.infotainme;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The phone app: settings for the Android Auto display, with a live preview of it.
 * The dashboard itself runs on the car screen.
 */
public final class MainActivity extends Activity {

    private static final int REQ_LOCATION = 1;

    private Fonts fonts;
    private boolean night;
    private int textPrimary;
    private int textSecondary;
    private int accent;

    private DashboardView preview;
    private RadioGroup previewGroup;
    private RadioGroup themeGroup;
    private RadioGroup unitsGroup;
    private final List<Swatch> swatches = new ArrayList<>();
    private TextView locationStatus;
    private Button locationButton;
    private TextView notificationStatus;
    private Button notificationButton;
    private View notificationHelp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fonts = Fonts.get(this);
        night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        textPrimary = night ? 0xFFFFFFFF : 0xFF1C1C1E;
        textSecondary = night ? 0xFF98989F : 0xFF6C6C70;
        accent = night ? 0xFF0A84FF : 0xFF007AFF;
        setContentView(buildUi());

        if (!WeatherRepository.hasLocationPermission(this) && !Prefs.askedLocation(this)) {
            requestLocation();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_LOCATION) {
            WeatherRepository.get(this).refreshNow();
            refresh();
        }
    }

    // ------------------------------------------------------------------ UI

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        root.setPadding(p, dp(12), p, dp(32));

        root.addView(label("Infotain Me", 32, fonts.display, textPrimary), wrap());
        TextView subtitle = label("Settings for your car display", 16, fonts.semibold, textSecondary);
        root.addView(subtitle, margins(0, 0, 0, dp(16)));

        // Live preview, in a car-screen shape.
        FrameLayout previewFrame = new FrameLayout(this) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int w = MeasureSpec.getSize(widthSpec);
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(w * 9 / 16, MeasureSpec.EXACTLY));
            }
        };
        previewFrame.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(18));
            }
        });
        previewFrame.setClipToOutline(true);
        preview = new DashboardView(this);
        previewFrame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        root.addView(previewFrame, margins(0, 0, 0, dp(10)));

        previewGroup = segmented(new String[]{"Headlights off", "Headlights on"}, (g, id) -> {
            preview.setNightPreview(id == 1);
        });
        select(previewGroup, night ? 1 : 0);
        preview.setNightPreview(night);
        root.addView(previewGroup, margins(0, 0, 0, dp(8)));
        root.addView(label("Preview only. In the car, Automatic follows the car's headlights.",
                13, fonts.semibold, textSecondary), margins(dp(4), 0, dp(4), dp(8)));

        // Appearance.
        LinearLayout look = section(root, "Appearance");
        look.addView(label("Theme", 15, fonts.bold, textSecondary), wrap());
        themeGroup = radios(look, new String[]{
                "Automatic (follows car headlights)", "Always light", "Always dark"},
                (g, id) -> Prefs.setThemeMode(this, id));
        look.addView(label("Wallpaper", 15, fonts.bold, textSecondary), margins(0, dp(14), 0, dp(8)));
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        for (Wallpaper w : Wallpaper.ALL) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            Swatch swatch = new Swatch(this, w);
            swatches.add(swatch);
            item.addView(swatch, new LinearLayout.LayoutParams(dp(64), dp(64)));
            item.addView(label(w.name, 13, fonts.semibold, textSecondary), margins(0, dp(6), 0, 0));
            item.setOnClickListener(v -> {
                Prefs.setWallpaperId(this, w.id);
                refresh();
            });
            row.addView(item, margins(0, 0, dp(12), 0));
        }
        scroller.addView(row);
        look.addView(scroller, wrap());

        // Weather.
        LinearLayout weather = section(root, "Weather");
        weather.addView(label("Temperature", 15, fonts.bold, textSecondary), wrap());
        unitsGroup = radios(weather, new String[]{"Automatic", "Celsius (°C)", "Fahrenheit (°F)"},
                (g, id) -> Prefs.setUnits(this, id));
        locationStatus = label("", 15, fonts.semibold, textPrimary);
        weather.addView(locationStatus, margins(0, dp(14), 0, dp(8)));
        locationButton = button("Allow location", v -> onLocationButton());
        weather.addView(locationButton, wrap());

        // Notifications.
        LinearLayout notes = section(root, "Notifications");
        notificationStatus = label("", 15, fonts.semibold, textPrimary);
        notes.addView(notificationStatus, margins(0, 0, 0, dp(8)));
        notificationButton = button("Turn on notification access", v -> openNotificationAccess());
        notes.addView(notificationButton, wrap());
        LinearLayout help = new LinearLayout(this);
        help.setOrientation(LinearLayout.VERTICAL);
        help.addView(label("Switch greyed out? Android blocks this for apps installed from an APK. "
                        + "Open App info, tap ⋮ (top right), choose \"Allow restricted settings\", "
                        + "then try again.", 13, fonts.semibold, textSecondary),
                margins(0, dp(12), 0, dp(8)));
        help.addView(button("Open App info", v -> openAppInfo()), wrap());
        notificationHelp = help;
        notes.addView(help, wrap());

        // Android Auto setup.
        LinearLayout auto = section(root, "Show it in Android Auto");
        auto.addView(label("1. Open Android Auto settings on this phone.\n"
                + "2. Tap Version about 10 times to turn on developer mode.\n"
                + "3. ⋮ → Developer settings → turn on Unknown sources.\n"
                + "4. Connect to the car and open Infotain Me from the app list.\n\n"
                + "On the car screen, the two buttons switch wallpaper and theme.",
                15, fonts.semibold, textPrimary), wrap());

        // About.
        LinearLayout about = section(root, "About");
        about.addView(label("Version " + versionName() + "\nFont: Nunito. Add SF Pro Rounded font "
                        + "files to the app's assets/fonts folder to use it instead.",
                14, fonts.semibold, textSecondary), wrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.setBackgroundColor(night ? 0xFF000000 : 0xFFF2F2F7);
        scroll.addView(root);
        return scroll;
    }

    private void refresh() {
        select(themeGroup, Prefs.themeMode(this));
        select(unitsGroup, Prefs.units(this));
        String selected = Prefs.wallpaperId(this);
        for (Swatch s : swatches) {
            s.setSelected(s.wallpaper.id.equals(selected));
            s.dark = selected(previewGroup) == 1;
            s.invalidate();
        }

        boolean location = WeatherRepository.hasLocationPermission(this);
        locationStatus.setText(location ? "✓ Location allowed" : "Weather needs your location.");
        locationButton.setVisibility(location ? View.GONE : View.VISIBLE);

        boolean access = NotificationStore.isAccessGranted(this);
        notificationStatus.setText(access ? "✓ Notifications will show on the car screen"
                : "Let the car screen show your phone's notifications.");
        notificationButton.setVisibility(access ? View.GONE : View.VISIBLE);
        notificationHelp.setVisibility(access ? View.GONE : View.VISIBLE);
        preview.invalidate();
    }

    // -------------------------------------------------------------- actions

    private void requestLocation() {
        Prefs.setAskedLocation(this);
        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
    }

    private void onLocationButton() {
        // After a permanent "deny", Android won't show the prompt again, so open App info instead.
        if (!Prefs.askedLocation(this)
                || shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            requestLocation();
        } else {
            openAppInfo();
        }
    }

    private void openNotificationAccess() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            new ComponentName(this, DashboardNotificationListener.class).flattenToString());
        } else {
            intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        }
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }
    }

    private void openAppInfo() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)));
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    // --------------------------------------------------------- view helpers

    private LinearLayout section(LinearLayout root, String title) {
        root.addView(label(title.toUpperCase(Locale.getDefault()), 13, fonts.bold, textSecondary),
                margins(dp(16), dp(20), 0, dp(8)));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(night ? 0xFF1C1C1E : 0xFFFFFFFF);
        bg.setCornerRadius(dp(16));
        card.setBackground(bg);
        root.addView(card, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private TextView label(String s, int sp, Typeface tf, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    private Button button(String s, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTypeface(fonts.bold);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTextColor(0xFFFFFFFF);
        b.setStateListAnimator(null);
        b.setPadding(dp(18), 0, dp(18), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(accent);
        bg.setCornerRadius(dp(24));
        b.setBackground(bg);
        b.setOnClickListener(click);
        return b;
    }

    private RadioGroup radios(LinearLayout parent, String[] options, RadioGroup.OnCheckedChangeListener l) {
        RadioGroup g = new RadioGroup(this);
        for (int i = 0; i < options.length; i++) {
            RadioButton b = new RadioButton(this);
            b.setId(View.generateViewId());
            b.setText(options[i]);
            b.setTypeface(fonts.semibold);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            b.setTextColor(textPrimary);
            b.setButtonTintList(ColorStateList.valueOf(accent));
            b.setMinHeight(dp(44));
            g.addView(b);
        }
        g.setOnCheckedChangeListener((group, id) -> {
            l.onCheckedChanged(group, selected(group));
            preview.invalidate();
        });
        parent.addView(g, wrap());
        return g;
    }

    /** iOS-style two-option switcher. */
    private RadioGroup segmented(String[] options, RadioGroup.OnCheckedChangeListener l) {
        RadioGroup g = new RadioGroup(this);
        g.setOrientation(LinearLayout.HORIZONTAL);
        g.setPadding(dp(3), dp(3), dp(3), dp(3));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(night ? 0xFF1C1C1E : 0xFFE3E3E8);
        bg.setCornerRadius(dp(12));
        g.setBackground(bg);
        for (int i = 0; i < options.length; i++) {
            RadioButton b = new RadioButton(this);
            b.setId(View.generateViewId());
            b.setText(options[i]);
            b.setButtonDrawable(null);
            b.setGravity(Gravity.CENTER);
            b.setTypeface(fonts.bold);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            b.setTextColor(textPrimary);
            b.setMinHeight(dp(38));
            g.addView(b, new RadioGroup.LayoutParams(0, -2, 1f));
        }
        g.setOnCheckedChangeListener((group, id) -> {
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                GradientDrawable sel = new GradientDrawable();
                sel.setColor(night ? 0xFF48484A : 0xFFFFFFFF);
                sel.setCornerRadius(dp(10));
                child.setBackground(child.getId() == id ? sel : null);
            }
            l.onCheckedChanged(group, selected(group));
            if (!swatches.isEmpty()) {
                refresh();
            }
        });
        return g;
    }

    /** Index of the checked option in a group, or -1. */
    private static int selected(RadioGroup g) {
        return g.indexOfChild(g.findViewById(g.getCheckedRadioButtonId()));
    }

    private static void select(RadioGroup g, int index) {
        if (index >= 0 && index < g.getChildCount()) {
            g.check(g.getChildAt(index).getId());
        }
    }

    private ViewGroup.MarginLayoutParams wrap() {
        return new LinearLayout.LayoutParams(-2, -2);
    }

    private LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(l, t, r, b);
        return lp;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }

    /** A wallpaper thumbnail; a ring marks the selected one. */
    private final class Swatch extends View {
        final Wallpaper wallpaper;
        boolean dark;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);

        Swatch(Context context, Wallpaper wallpaper) {
            super(context);
            this.wallpaper = wallpaper;
            paint.setDither(true);
            ring.setStyle(Paint.Style.STROKE);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            float inset = dp(5);
            paint.setShader(new LinearGradient(0, 0, w, h, wallpaper.colors(dark), null,
                    Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(inset, inset, w - inset, h - inset), dp(12), dp(12), paint);
            ring.setStrokeWidth(dp(2.5f));
            if (isSelected()) {
                ring.setColor(accent);
                c.drawRoundRect(new RectF(dp(1.5f), dp(1.5f), w - dp(1.5f), h - dp(1.5f)),
                        dp(15), dp(15), ring);
            } else {
                ring.setStrokeWidth(dp(1));
                ring.setColor(night ? 0x33FFFFFF : 0x22000000);
                c.drawRoundRect(new RectF(inset, inset, w - inset, h - inset), dp(12), dp(12), ring);
            }
        }
    }
}
