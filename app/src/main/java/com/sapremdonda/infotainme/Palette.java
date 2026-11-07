package com.sapremdonda.infotainme;

/** Colours for light and dark mode. Cards are slightly see-through so the wallpaper shows. */
final class Palette {

    static final Palette LIGHT = new Palette(
            0xD9FFFFFF, 0x80FFFFFF, 0x1F000000,
            0xFF1C1C1E, 0xFF636366, 0x1A000000, 0x14000000,
            0xFF007AFF, 0xFF34C759, 0xFFFF3B30, 0xFF5856D6,
            0xFFFFB800, 0xFFA9B4C2, 0xFF0A84FF, 0xFFF4C542);

    static final Palette DARK = new Palette(
            0xB81C1C1E, 0x1AFFFFFF, 0,
            0xFFFFFFFF, 0xFF98989F, 0x2EFFFFFF, 0x1FFFFFFF,
            0xFF0A84FF, 0xFF30D158, 0xFFFF453A, 0xFF5E5CE6,
            0xFFFFD60A, 0xFFE5E5EA, 0xFF64D2FF, 0xFFF5DE8A);

    final int card;
    final int cardEdge;
    final int cardShadow;
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

    private Palette(int card, int cardEdge, int cardShadow, int primary, int secondary, int track,
                    int separator, int accent, int green, int red, int indigo, int sun, int cloud,
                    int rain, int moon) {
        this.card = card;
        this.cardEdge = cardEdge;
        this.cardShadow = cardShadow;
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
