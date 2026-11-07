package com.sapremdonda.infotainme;

/** Gradient wallpapers. Each has a day version and a darker night version. */
public final class Wallpaper {

    public static final Wallpaper[] ALL = {
            new Wallpaper("mono", "Mono",
                    new int[]{0xFFFFFFFF, 0xFF8E8E93, 0xFF000000},
                    new int[]{0xFF48484A, 0xFF1C1C1E, 0xFF000000}),
            new Wallpaper("mist", "Mist",
                    new int[]{0xFFF5F5F7, 0xFFC7C7CC},
                    new int[]{0xFF2C2C2E, 0xFF0B0B0C}),
            new Wallpaper("ocean", "Ocean",
                    new int[]{0xFFE3F2FF, 0xFF8EC5FF, 0xFF3A7BD5},
                    new int[]{0xFF153A66, 0xFF0A1B33, 0xFF03070F}),
            new Wallpaper("sunset", "Sunset",
                    new int[]{0xFFFFF1E0, 0xFFFFB38A, 0xFFF0648C},
                    new int[]{0xFF4A1A3A, 0xFF1F0A1C, 0xFF0A0309}),
            new Wallpaper("forest", "Forest",
                    new int[]{0xFFEEF7EE, 0xFF9FD3A8, 0xFF3E8E5E},
                    new int[]{0xFF173D2A, 0xFF0A1F15, 0xFF030A06}),
            new Wallpaper("lavender", "Lavender",
                    new int[]{0xFFF6F0FF, 0xFFCBB6FF, 0xFF8B6CEF},
                    new int[]{0xFF2E1C4D, 0xFF140B26, 0xFF07040E}),
            new Wallpaper("sand", "Sand",
                    new int[]{0xFFFFF8EC, 0xFFEAD7B7, 0xFFC9A57A},
                    new int[]{0xFF3A2F22, 0xFF1A140E, 0xFF0A0806}),
    };

    public final String id;
    public final String name;
    private final int[] day;
    private final int[] night;

    private Wallpaper(String id, String name, int[] day, int[] night) {
        this.id = id;
        this.name = name;
        this.day = day;
        this.night = night;
    }

    public int[] colors(boolean dark) {
        return dark ? night : day;
    }

    public static Wallpaper byId(String id) {
        for (Wallpaper w : ALL) {
            if (w.id.equals(id)) {
                return w;
            }
        }
        return ALL[0];
    }

    public Wallpaper next() {
        for (int i = 0; i < ALL.length; i++) {
            if (ALL[i] == this) {
                return ALL[(i + 1) % ALL.length];
            }
        }
        return ALL[0];
    }
}
