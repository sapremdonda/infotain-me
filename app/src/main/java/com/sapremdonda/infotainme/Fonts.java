package com.sapremdonda.infotainme;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Typeface;

import java.io.IOException;
import java.io.InputStream;

/**
 * App fonts. Uses SF Pro Rounded if its files are added to assets/fonts
 * (it can't be shipped with the app because of Apple's licence), otherwise Nunito.
 */
public final class Fonts {

    private static Fonts instance;

    /** Big numbers like the clock. */
    public final Typeface display;
    /** Titles and values. */
    public final Typeface bold;
    /** Labels and body text. */
    public final Typeface semibold;

    private Fonts(AssetManager am) {
        display = load(am, "fonts/SF-Pro-Rounded-Bold.otf", "fonts/Nunito-ExtraBold.ttf");
        bold = load(am, "fonts/SF-Pro-Rounded-Bold.otf", "fonts/Nunito-Bold.ttf");
        semibold = load(am, "fonts/SF-Pro-Rounded-Semibold.otf", "fonts/Nunito-SemiBold.ttf");
    }

    public static synchronized Fonts get(Context context) {
        if (instance == null) {
            instance = new Fonts(context.getApplicationContext().getAssets());
        }
        return instance;
    }

    private static Typeface load(AssetManager am, String preferred, String fallback) {
        String path = exists(am, preferred) ? preferred : fallback;
        try {
            return Typeface.createFromAsset(am, path);
        } catch (RuntimeException e) {
            return Typeface.DEFAULT_BOLD;
        }
    }

    private static boolean exists(AssetManager am, String path) {
        try (InputStream ignored = am.open(path)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
