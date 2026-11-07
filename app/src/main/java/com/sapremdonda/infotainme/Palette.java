package com.sapremdonda.infotainme;

/** Colours for light and dark mode, including the liquid-glass card layers. */
final class Palette {

    static final Palette LIGHT = new Palette(
            // glass: tint, sheen, rim (bright, faint), inner glow, shadow
            0x9EFFFFFF, 0x59FFFFFF, 0xF2FFFFFF, 0x26FFFFFF, 0x33FFFFFF, 0x1F000000,
            // text and lines
            0xFF1C1C1E, 0xFF48484D, 0x24000000, 0x1A000000,
            // accents
            0xFF007AFF, 0xFF34C759, 0xFFFF3B30, 0xFF5856D6,
            // weather
            0xFFFFB800, 0xFF9AA6B5, 0xFF0A84FF, 0xFFF4C542);

    static final Palette DARK = new Palette(
            0x5C000000, 0x1FFFFFFF, 0x8CFFFFFF, 0x14FFFFFF, 0x14FFFFFF, 0x66000000,
            0xFFFFFFFF, 0xFFA1A1A8, 0x33FFFFFF, 0x24FFFFFF,
            0xFF0A84FF, 0xFF30D158, 0xFFFF453A, 0xFF5E5CE6,
            0xFFFFD60A, 0xFFE5E5EA, 0xFF64D2FF, 0xFFF5DE8A);

    final int glassTint;
    final int glassSheen;
    final int rimBright;
    final int rimFaint;
    final int innerGlow;
    final int shadow;
    final int primary;
    final int secondary;
    final int track;
    final int separator;
    final int accent;
    final int green;
    final int red;
    final int indigo;
    final int sun;
    final int cloud;
    final int rain;
    final int moon;

    private Palette(int glassTint, int glassSheen, int rimBright, int rimFaint, int innerGlow,
                    int shadow, int primary, int secondary, int track, int separator, int accent,
                    int green, int red, int indigo, int sun, int cloud, int rain, int moon) {
        this.glassTint = glassTint;
        this.glassSheen = glassSheen;
        this.rimBright = rimBright;
        this.rimFaint = rimFaint;
        this.innerGlow = innerGlow;
        this.shadow = shadow;
        this.primary = primary;
        this.secondary = secondary;
        this.track = track;
        this.separator = separator;
        this.accent = accent;
        this.green = green;
        this.red = red;
        this.indigo = indigo;
        this.sun = sun;
        this.cloud = cloud;
        this.rain = rain;
        this.moon = moon;
    }
}
