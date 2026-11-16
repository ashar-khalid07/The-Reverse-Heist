package heist;

import java.awt.Color;

/** Shared colours so the whole game reads as one visual system. */
final class Palette {
    private Palette() {}

    static final Color VOID = new Color(0x080b14);
    static final Color HUD_BG = new Color(0x0d111d);
    static final Color HUD_PANEL = new Color(0x141a2b);
    static final Color HUD_LINE = new Color(0x27304a);
    static final Color TEXT = new Color(0xe8ecf4);
    static final Color MUTED = new Color(0x8792ab);
    static final Color DIM = new Color(0x4b556d);

    static final Color GOLD = new Color(0xf2c14e);
    static final Color GOLD_LIGHT = new Color(0xffe08a);
    static final Color GOLD_DARK = new Color(0xb98a2a);
    static final Color RED = new Color(0xe5484d);
    static final Color GREEN = new Color(0x3ddc84);
    static final Color AMBER = new Color(0xffb020);
    static final Color CASH = new Color(0x7bd389);
    static final Color CASH_DARK = new Color(0x2f7d4a);
    static final Color JEWEL = new Color(0x6ee7f0);
    static final Color JEWEL_DARK = new Color(0x2f9aa8);
    static final Color PINK = new Color(0xff8fd0);

    static final Color WALL_TOP = new Color(0x4d566b);
    static final Color WALL_HI = new Color(0x626c84);
    static final Color WALL_FACE = new Color(0x333b4f);
    static final Color WALL_EDGE = new Color(0x171c2a);
    static final Color OUTLINE = new Color(0x121622);

    static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}
