package heist;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * All the art in the game, drawn in code.
 *
 * Every tile and character has its own method here, so to swap in real sprite
 * images later you only need to replace the body of the matching method with
 * a g.drawImage(...) call. Nothing outside this class cares how things are drawn.
 */
final class Sprites {
    private Sprites() {}

    private static final int T = Level.TILE;

    private static final Color FLOOR_CORRIDOR = new Color(0x232a3a);
    private static final Color FLOOR_CASH = new Color(0x20302e);
    private static final Color FLOOR_GOLD = new Color(0x322c24);
    private static final Color FLOOR_JEWEL = new Color(0x2a2540);
    private static final Color FLOOR_LOBBY = new Color(0x2b3348);
    private static final Color FLOOR_STAFF = new Color(0x272b35);
    private static final Color FLOOR_VAULT = new Color(0x243242);

    private static final Color WOOD = new Color(0x6b4f3a);
    private static final Color WOOD_LIGHT = new Color(0x8a6a4c);
    private static final Color WOOD_DARK = new Color(0x4a3626);
    private static final Color STEEL = new Color(0x56607a);
    private static final Color STEEL_LIGHT = new Color(0x7b869c);
    private static final Color STEEL_DARK = new Color(0x333b4f);
    private static final Color PAPER = new Color(0xdfe5f0);

    static void fill(Graphics2D g, Color c, int x, int y, int w, int h) {
        g.setColor(c);
        g.fillRect(x, y, w, h);
    }

    private static Ellipse2D ellipse(double x, double y, double w, double h) {
        return new Ellipse2D.Double(x, y, w, h);
    }

    /** Stable pseudo-random number for a tile, so decoration never flickers. */
    static int hash(int c, int r) {
        int h = c * 73856093 ^ r * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }

    // =================================================================== map

    /** Paints everything that never changes: floors, walls, furniture, lighting. */
    static void paintStaticMap(Graphics2D g, Level lv) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        for (int r = 0; r < Level.ROWS; r++) {
            for (int c = 0; c < Level.COLS; c++) {
                if (!Level.wallLike(lv.tile(c, r))) floor(g, c, r);
            }
        }
        rug(g);
        floorLabels(g);
        lightPools(g);
        shadows(g, lv);
        vaultInterior(g);
        for (int r = 0; r < Level.ROWS; r++) {
            for (int c = 0; c < Level.COLS; c++) {
                char t = lv.tile(c, r);
                if (t == '#' || t == 'V' || t == 'D') wall(g, lv, c, r);
            }
        }
        entrance(g);
        for (int r = 0; r < Level.ROWS; r++) {
            for (int c = 0; c < Level.COLS; c++) {
                furniture(g, lv, c, r);
            }
        }
    }

    private static Color floorColor(int c, int r) {
        if (r >= 1 && r <= 7) {
            if (c <= 11) return FLOOR_CASH;
            if (c >= 13 && c <= 26) return FLOOR_VAULT;
            if (c >= 28) return FLOOR_GOLD;
        }
        if (r >= 12 && r <= 18) {
            if (c <= 11) return FLOOR_STAFF;
            if (c >= 13 && c <= 26) return FLOOR_LOBBY;
            if (c >= 28) return FLOOR_JEWEL;
        }
        return FLOOR_CORRIDOR;
    }

    private static void floor(Graphics2D g, int c, int r) {
        int x = c * T, y = r * T;
        Color base = floorColor(c, r);
        if (base == FLOOR_LOBBY && ((c + r) & 1) == 0) base = Palette.mix(base, Color.WHITE, 0.045);
        fill(g, base, x, y, T, T);
        Color line = Palette.mix(base, Color.BLACK, 0.24);
        fill(g, line, x + T - 1, y, 1, T);
        fill(g, line, x, y + T - 1, T, 1);
        int h = hash(c, r);
        fill(g, Palette.mix(base, Color.WHITE, 0.07), x + 3 + h % 15, y + 3 + (h >> 4) % 15, 2, 1);
        fill(g, Palette.mix(base, Color.BLACK, 0.2), x + 2 + (h >> 8) % 17, y + 2 + (h >> 12) % 17, 1, 2);
    }

    private static void rug(Graphics2D g) {
        int x = 18 * T + 6, y = 14 * T, w = 4 * T - 12, h = 5 * T;
        fill(g, new Color(0x47233a), x, y, w, h);
        fill(g, Palette.GOLD_DARK, x + 3, y + 3, w - 6, 1);
        fill(g, Palette.GOLD_DARK, x + 3, y + 3, 1, h - 3);
        fill(g, Palette.GOLD_DARK, x + w - 4, y + 3, 1, h - 3);
        fill(g, new Color(0x5a2c49), x + 8, y + 8, w - 16, h - 8);
    }

    private static void floorLabels(Graphics2D g) {
        Color ink = new Color(255, 255, 255, 34);
        PixelFont.drawCentered(g, "CASH STORAGE", 156, 7 * T + 5, 2, ink);
        PixelFont.drawCentered(g, "MAIN VAULT", 480, 7 * T + 5, 2, ink);
        PixelFont.drawCentered(g, "GOLD RESERVE", 792, 7 * T + 5, 2, ink);
        PixelFont.drawCentered(g, "STAFF ONLY", 156, 13 * T + 5, 2, ink);
        PixelFont.drawCentered(g, "LOBBY", 480, 12 * T + 5, 2, ink);
        PixelFont.drawCentered(g, "JEWELLERY", 804, 12 * T + 5, 2, ink);
    }

    private static void lightPools(Graphics2D g) {
        int[][] lights = {
            {156, 96, 150}, {804, 96, 150}, {480, 150, 120},
            {120, 240, 110}, {360, 240, 110}, {600, 240, 110}, {840, 240, 110},
            {156, 372, 150}, {480, 372, 170}, {804, 372, 150},
        };
        for (int[] l : lights) {
            g.setPaint(new RadialGradientPaint(l[0], l[1], l[2], new float[] {0f, 1f},
                    new Color[] {new Color(255, 240, 210, 30), new Color(255, 240, 210, 0)}));
            g.fillRect(l[0] - l[2], l[1] - l[2], l[2] * 2, l[2] * 2);
        }
    }

    private static void shadows(Graphics2D g, Level lv) {
        for (int r = 0; r < Level.ROWS; r++) {
            for (int c = 0; c < Level.COLS; c++) {
                if (lv.tile(c, r) != '.') continue;
                int x = c * T, y = r * T;
                char above = lv.tile(c, r - 1);
                if (above != '.') {
                    int a = Level.wallLike(above) ? 120 : 70;
                    g.setPaint(new GradientPaint(0, y, new Color(0, 0, 0, a), 0, y + 9, new Color(0, 0, 0, 0)));
                    g.fillRect(x, y, T, 9);
                }
                char left = lv.tile(c - 1, r);
                if (left != '.') {
                    int a = Level.wallLike(left) ? 70 : 40;
                    g.setPaint(new GradientPaint(x, 0, new Color(0, 0, 0, a), x + 6, 0, new Color(0, 0, 0, 0)));
                    g.fillRect(x, y, 6, T);
                }
            }
        }
    }

    private static void wall(Graphics2D g, Level lv, int c, int r) {
        int x = c * T, y = r * T;
        fill(g, Palette.WALL_TOP, x, y, T, T);
        if ((hash(c, r) & 3) == 0) fill(g, Palette.mix(Palette.WALL_TOP, Color.BLACK, 0.08), x + 4, y + 4, T - 8, 1);
        boolean up = Level.wallLike(lv.tile(c, r - 1));
        boolean down = Level.wallLike(lv.tile(c, r + 1));
        boolean left = Level.wallLike(lv.tile(c - 1, r));
        boolean right = Level.wallLike(lv.tile(c + 1, r));
        if (!down) {
            // The south face gives the walls a little height.
            fill(g, Palette.WALL_FACE, x, y + T - 9, T, 9);
            fill(g, Palette.WALL_HI, x, y + T - 10, T, 1);
            fill(g, Palette.WALL_EDGE, x, y + T - 1, T, 1);
        }
        if (!up) {
            fill(g, Palette.WALL_EDGE, x, y, T, 1);
            fill(g, Palette.WALL_HI, x, y + 1, T, 1);
        }
        if (!left) fill(g, Palette.WALL_EDGE, x, y, 1, T);
        if (!right) fill(g, Palette.WALL_EDGE, x + T - 1, y, 1, T);
    }

    /** The inside of the vault, visible but unreachable: rows of safe-deposit boxes. */
    private static void vaultInterior(Graphics2D g) {
        int x = 13 * T, y = 1 * T, w = 14 * T, h = 3 * T;
        fill(g, new Color(0x141926), x, y, w, h);
        for (int by = y + 6; by + 8 <= y + h - 4; by += 11) {
            for (int bx = x + 7; bx + 12 <= x + w - 6; bx += 15) {
                fill(g, new Color(0x2a3246), bx, by, 12, 8);
                fill(g, new Color(0x394360), bx, by, 12, 1);
                fill(g, Palette.GOLD_DARK, bx + 5, by + 3, 2, 2);
            }
        }
        g.setPaint(new RadialGradientPaint(x + w / 2f, y + h, 150, new float[] {0f, 1f},
                new Color[] {new Color(242, 193, 78, 46), new Color(242, 193, 78, 0)}));
        g.fillRect(x, y, w, h);
        fill(g, STEEL, x, y, w, 3);
        fill(g, STEEL, x, y, 3, h);
        fill(g, STEEL, x + w - 3, y, 3, h);
        fill(g, Palette.WALL_EDGE, x + 3, y + 3, w - 6, 1);
    }

    private static void entrance(Graphics2D g) {
        int x = 19 * T, y = 19 * T;
        fill(g, Palette.WALL_EDGE, x, y + 3, 2 * T, 18);
        fill(g, STEEL_LIGHT, x + 1, y + 4, 2 * T - 2, 16);
        fill(g, new Color(0x6aa9c9), x + 3, y + 6, 20, 12);
        fill(g, new Color(0x6aa9c9), x + 25, y + 6, 20, 12);
        fill(g, new Color(255, 255, 255, 110), x + 5, y + 8, 6, 1);
        fill(g, new Color(255, 255, 255, 110), x + 27, y + 8, 6, 1);
        fill(g, Palette.GOLD, x + 21, y + 11, 2, 3);
        fill(g, Palette.GOLD, x + 25, y + 11, 2, 3);
    }

    private static void furniture(Graphics2D g, Level lv, int c, int r) {
        int x = c * T, y = r * T;
        char t = lv.tile(c, r);
        boolean sameLeft = lv.tile(c - 1, r) == t;
        boolean sameRight = lv.tile(c + 1, r) == t;
        int h = hash(c, r);
        switch (t) {
            case 's': shelf(g, x, y, h); break;
            case 'b': crate(g, x, y); break;
            case 'c': counter(g, x, y, sameLeft, sameRight, h); break;
            case 'd': desk(g, x, y, sameLeft, sameRight); break;
            case 'p': plant(g, x, y); break;
            case 'j': displayCase(g, x, y, h); break;
            case 'g': goldPallet(g, x, y); break;
            case 'm': cashTable(g, x, y); break;
            case 't': bench(g, x, y, sameLeft, sameRight); break;
            default: break;
        }
    }

    private static void shelf(Graphics2D g, int x, int y, int h) {
        fill(g, Palette.OUTLINE, x + 1, y + 1, 22, 22);
        fill(g, new Color(0x3c4459), x + 2, y + 2, 20, 20);
        fill(g, STEEL, x + 2, y + 2, 20, 2);
        Color[] boxes = {new Color(0xb08d57), new Color(0x8792ab), new Color(0x7a5a3a), new Color(0x5f7fbf)};
        for (int i = 0; i < 3; i++) {
            int sy = y + 5 + i * 6;
            fill(g, new Color(0x262c3c), x + 3, sy + 4, 18, 1);
            for (int k = 0; k < 3; k++) {
                int pick = (h >> (i * 6 + k * 2)) & 3;
                if (pick == 3 && k == 1) continue;
                fill(g, boxes[pick], x + 4 + k * 6, sy, 4, 4);
            }
        }
    }

    private static void crate(Graphics2D g, int x, int y) {
        fill(g, Palette.OUTLINE, x + 2, y + 2, 20, 20);
        fill(g, new Color(0x7a5a3a), x + 3, y + 3, 18, 18);
        fill(g, new Color(0x94704a), x + 3, y + 3, 18, 2);
        Color dark = new Color(0x5a4028);
        fill(g, dark, x + 3, y + 5, 2, 16);
        fill(g, dark, x + 19, y + 5, 2, 16);
        fill(g, dark, x + 3, y + 19, 18, 2);
        for (int i = 0; i < 6; i++) fill(g, dark, x + 6 + i * 2, y + 7 + i * 2, 2, 2);
    }

    private static void counter(Graphics2D g, int x, int y, boolean left, boolean right, int h) {
        fill(g, WOOD_DARK, x, y + 3, T, 19);
        fill(g, WOOD, x, y + 9, T, 10);
        fill(g, new Color(0xaab2c3), x, y + 3, T, 6);
        fill(g, new Color(0xd5dbe6), x, y + 3, T, 1);
        fill(g, Palette.OUTLINE, x, y + 21, T, 1);
        if (!left) fill(g, Palette.OUTLINE, x, y + 3, 1, 19);
        if (!right) fill(g, Palette.OUTLINE, x + T - 1, y + 3, 1, 19);
        if ((h & 1) == 0) {
            fill(g, Palette.OUTLINE, x + 8, y + 4, 8, 4);
            fill(g, new Color(0x4cc9f0), x + 9, y + 5, 6, 2);
        } else {
            fill(g, Palette.GOLD_DARK, x + 10, y + 5, 4, 2);
        }
    }

    private static void desk(Graphics2D g, int x, int y, boolean left, boolean right) {
        fill(g, Palette.OUTLINE, x, y + 3, T, 18);
        int x0 = left ? x : x + 1, x1 = right ? x + T : x + T - 1;
        fill(g, new Color(0x5b4636), x0, y + 4, x1 - x0, 16);
        fill(g, WOOD_LIGHT, x0, y + 4, x1 - x0, 2);
        if (!left) {
            fill(g, Palette.OUTLINE, x + 7, y + 6, 11, 8);
            fill(g, new Color(0x4cc9f0), x + 8, y + 7, 9, 5);
            fill(g, new Color(0xaab2c3), x + 9, y + 15, 7, 3);
        } else {
            fill(g, PAPER, x + 4, y + 8, 7, 9);
            fill(g, new Color(0xaab2c3), x + 5, y + 10, 5, 1);
            fill(g, new Color(0xaab2c3), x + 5, y + 12, 5, 1);
            fill(g, Palette.RED, x + 15, y + 9, 3, 3);
        }
    }

    private static void plant(Graphics2D g, int x, int y) {
        fill(g, Palette.OUTLINE, x + 7, y + 12, 10, 10);
        fill(g, new Color(0x8a5a3c), x + 8, y + 13, 8, 8);
        fill(g, new Color(0x6b4330), x + 8, y + 19, 8, 2);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x1f6b3d));
        g.fill(ellipse(x + 2, y + 1, 20, 15));
        g.setColor(new Color(0x2b8a50));
        g.fill(ellipse(x + 3, y + 2, 18, 13));
        g.setColor(new Color(0x41b06b));
        g.fill(ellipse(x + 6, y + 3, 8, 6));
        g.fill(ellipse(x + 13, y + 7, 6, 5));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    private static void displayCase(Graphics2D g, int x, int y, int h) {
        fill(g, Palette.OUTLINE, x + 1, y + 1, 22, 22);
        fill(g, new Color(0x3a2f5c), x + 2, y + 2, 20, 20);
        fill(g, new Color(155, 216, 230, 50), x + 3, y + 3, 18, 18);
        Color[] gems = {Palette.JEWEL, Palette.PINK, Color.WHITE, Palette.GOLD};
        for (int i = 0; i < 3; i++) {
            int gx = x + 5 + (h >> (i * 5)) % 13;
            int gy = y + 5 + (h >> (i * 5 + 2)) % 13;
            fill(g, gems[(h >> (i * 3)) & 3], gx, gy, 2, 2);
        }
        Color frame = new Color(0xbfc8dc);
        fill(g, frame, x + 2, y + 2, 20, 1);
        fill(g, frame, x + 2, y + 21, 20, 1);
        fill(g, frame, x + 2, y + 2, 1, 20);
        fill(g, frame, x + 21, y + 2, 1, 20);
        fill(g, new Color(255, 255, 255, 100), x + 5, y + 5, 5, 1);
        fill(g, new Color(255, 255, 255, 100), x + 5, y + 6, 1, 3);
    }

    private static void goldBar(Graphics2D g, int x, int y, int w, int h) {
        fill(g, Palette.GOLD, x, y, w, h);
        fill(g, Palette.GOLD_LIGHT, x, y, w, 1);
        fill(g, Palette.GOLD_DARK, x, y + h - 1, w, 1);
    }

    private static void goldPallet(Graphics2D g, int x, int y) {
        fill(g, Palette.OUTLINE, x + 1, y + 16, 22, 7);
        fill(g, new Color(0x5a4028), x + 2, y + 17, 20, 5);
        for (int i = 0; i < 3; i++) goldBar(g, x + 3 + i * 6, y + 11, 5, 5);
        for (int i = 0; i < 2; i++) goldBar(g, x + 6 + i * 6, y + 5, 5, 5);
    }

    private static void cashBundle(Graphics2D g, int x, int y) {
        fill(g, Palette.CASH_DARK, x, y, 7, 4);
        fill(g, new Color(0x5fbf77), x, y, 7, 3);
        fill(g, PAPER, x + 3, y, 1, 4);
    }

    private static void cashTable(Graphics2D g, int x, int y) {
        fill(g, Palette.OUTLINE, x + 1, y + 3, 22, 19);
        fill(g, STEEL, x + 2, y + 4, 20, 17);
        fill(g, STEEL_LIGHT, x + 2, y + 4, 20, 2);
        cashBundle(g, x + 4, y + 8);
        cashBundle(g, x + 13, y + 8);
        cashBundle(g, x + 8, y + 14);
    }

    private static void bench(Graphics2D g, int x, int y, boolean left, boolean right) {
        fill(g, Palette.OUTLINE, x, y + 6, T, 13);
        int x0 = left ? x : x + 1, x1 = right ? x + T : x + T - 1;
        fill(g, new Color(0x3b4a7a), x0, y + 7, x1 - x0, 11);
        fill(g, new Color(0x4d5f99), x0, y + 7, x1 - x0, 3);
        fill(g, new Color(0x2c385e), x + 11, y + 10, 1, 8);
    }

    // ============================================================== stations

    static Color kindColor(Station.Kind kind) {
        switch (kind) {
            case CASH: return Palette.CASH;
            case GOLD: return Palette.GOLD;
            case JEWELLERY: return Palette.JEWEL;
            default: return Palette.GOLD;
        }
    }

    /** A return point for cash, gold or jewellery. */
    static void station(Graphics2D g, Station s, double time, boolean showMarker) {
        int x = s.col * T, y = s.row * T;
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        fill(g, Palette.OUTLINE, x, y, T, T);
        switch (s.kind) {
            case CASH:
                fill(g, STEEL, x + 1, y + 1, 22, 22);
                fill(g, STEEL_LIGHT, x + 1, y + 1, 22, 2);
                fill(g, STEEL_DARK, x + 4, y + 5, 16, 15);
                fill(g, Palette.OUTLINE, x + 6, y + 7, 12, 2);
                if (s.done) {
                    cashBundle(g, x + 5, y + 11);
                    cashBundle(g, x + 12, y + 11);
                    cashBundle(g, x + 8, y + 15);
                } else {
                    PixelFont.draw(g, "$", x + 10, y + 11, 1, Palette.alpha(Palette.CASH, 150));
                }
                break;
            case GOLD:
                fill(g, STEEL_DARK, x + 1, y + 1, 22, 22);
                fill(g, STEEL, x + 1, y + 1, 22, 2);
                for (int i = 0; i < 2; i++) {
                    for (int k = 0; k < 2; k++) {
                        int bx = x + 4 + k * 9, by = y + 6 + i * 8;
                        if (s.done) {
                            goldBar(g, bx, by, 7, 5);
                        } else {
                            fill(g, Palette.alpha(Palette.GOLD_DARK, 170), bx, by, 7, 5);
                            fill(g, new Color(0x1f2535), bx + 1, by + 1, 5, 3);
                        }
                    }
                }
                break;
            default:
                fill(g, new Color(0xbfc8dc), x + 1, y + 1, 22, 22);
                fill(g, new Color(0x4a2a6b), x + 2, y + 2, 20, 20);
                fill(g, new Color(0x5d3a82), x + 4, y + 4, 16, 16);
                if (s.done) {
                    gem(g, x + 7, y + 8, Palette.JEWEL);
                    gem(g, x + 15, y + 8, Palette.PINK);
                    gem(g, x + 11, y + 15, Color.WHITE);
                } else {
                    Color hollow = new Color(0x3a2157);
                    fill(g, hollow, x + 6, y + 7, 4, 4);
                    fill(g, hollow, x + 14, y + 7, 4, 4);
                    fill(g, hollow, x + 10, y + 14, 4, 4);
                }
                break;
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);

        Color accent = kindColor(s.kind);
        if (s.done) {
            g.setColor(Palette.alpha(Palette.GREEN, 200));
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new Rectangle2D.Double(x - 0.5, y - 0.5, T + 1, T + 1));
            if (s.doneAge < 0.6) {
                double k = s.doneAge / 0.6;
                g.setColor(Palette.alpha(Palette.GREEN, (int) (200 * (1 - k))));
                g.setStroke(new BasicStroke(2f));
                double grow = 4 + 26 * k;
                g.draw(new Ellipse2D.Double(s.x - grow, s.y - grow, grow * 2, grow * 2));
            }
        } else if (showMarker) {
            double pulse = 0.5 + 0.5 * Math.sin(time * 4);
            g.setColor(Palette.alpha(accent, (int) (110 + 120 * pulse)));
            g.setStroke(new BasicStroke(2f));
            double grow = 1.5 + pulse * 2;
            g.draw(new Rectangle2D.Double(x - grow, y - grow, T + grow * 2, T + grow * 2));
            marker(g, s.x, y - 6 - pulse * 3, accent);
        }
    }

    private static void gem(Graphics2D g, int cx, int cy, Color c) {
        fill(g, c, cx - 1, cy - 2, 3, 1);
        fill(g, c, cx - 2, cy - 1, 5, 2);
        fill(g, c, cx - 1, cy + 1, 3, 1);
        fill(g, Color.WHITE, cx - 1, cy - 1, 1, 1);
    }

    /** A small downward arrow that bobs above the next objective. */
    static void marker(Graphics2D g, double cx, double tipY, Color c) {
        Path2D p = new Path2D.Double();
        p.moveTo(cx - 5, tipY - 8);
        p.lineTo(cx + 5, tipY - 8);
        p.lineTo(cx, tipY);
        p.closePath();
        g.setColor(Palette.OUTLINE);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(p);
        g.setColor(c);
        g.fill(p);
    }

    /** The main vault door: red light while locked, gold when ready, green once secured. */
    static void vaultDoor(Graphics2D g, Station v, boolean unlocked, double progress, double time) {
        int x = v.col * T, y = v.row * T, w = 2 * T;
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        fill(g, Palette.OUTLINE, x, y, w, T);
        fill(g, STEEL_LIGHT, x + 2, y + 1, w - 4, T - 3);
        fill(g, new Color(0x9aa5ba), x + 2, y + 1, w - 4, 2);
        fill(g, STEEL, x + 2, y + T - 6, w - 4, 4);
        fill(g, STEEL_DARK, x + 3, y + 5, 3, 4);
        fill(g, STEEL_DARK, x + 3, y + 13, 3, 4);
        for (int i = 0; i < 4; i++) {
            fill(g, STEEL_DARK, x + 9 + (i % 2) * 29, y + 4 + (i / 2) * 13, 2, 2);
        }
        Color light = v.done ? Palette.GREEN
                : unlocked ? (Math.sin(time * 6) > 0 ? Palette.GOLD : Palette.GOLD_DARK)
                : Palette.RED;
        fill(g, Palette.OUTLINE, x + w - 9, y + 4, 5, 5);
        fill(g, light, x + w - 8, y + 5, 3, 3);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double cx = v.x, cy = v.y - 1;
        double spin = v.done ? Math.PI * 3 : progress * Math.PI * 3;
        g.setColor(Palette.OUTLINE);
        g.fill(ellipse(cx - 9.5, cy - 9.5, 19, 19));
        g.setColor(new Color(0xdfe5f0));
        g.setStroke(new BasicStroke(2.2f));
        g.draw(ellipse(cx - 7, cy - 7, 14, 14));
        for (int i = 0; i < 3; i++) {
            double a = spin + i * Math.PI / 3;
            g.draw(new Line2D.Double(cx - Math.cos(a) * 7, cy - Math.sin(a) * 7,
                    cx + Math.cos(a) * 7, cy + Math.sin(a) * 7));
        }
        g.setColor(v.done ? Palette.GREEN : Palette.GOLD);
        g.fill(ellipse(cx - 2.5, cy - 2.5, 5, 5));

        if (progress > 0 && !v.done) {
            g.setColor(Palette.GREEN);
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g.draw(new Arc2D.Double(cx - 13, cy - 13, 26, 26, 90, -360 * progress, Arc2D.OPEN));
        }
        if (v.done) {
            g.setColor(Palette.alpha(Palette.GREEN, 210));
            g.setStroke(new BasicStroke(2f));
            g.draw(new Rectangle2D.Double(x - 1, y - 1, w + 2, T + 2));
        } else if (unlocked) {
            double pulse = 0.5 + 0.5 * Math.sin(time * 4);
            g.setColor(Palette.alpha(Palette.GOLD, (int) (110 + 120 * pulse)));
            g.setStroke(new BasicStroke(2f));
            double grow = 1.5 + pulse * 2;
            g.draw(new Rectangle2D.Double(x - grow, y - grow, w + grow * 2, T + grow * 2));
            marker(g, cx, y - 5 - pulse * 3, Palette.GOLD);
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
    }

    // ============================================================ characters

    /** Characters are drawn a little larger than their collision circle so they read clearly. */
    private static final double CHARACTER_SCALE = 1.2;

    /** The player: a striped-shirt burglar whose loot sack shrinks as goods are returned. */
    static void player(Graphics2D g, Player p, boolean hasCash, boolean hasGold, boolean hasJewels) {
        AffineTransform old = g.getTransform();
        // A soft pool of light so the player is always easy to find.
        g.setPaint(new RadialGradientPaint((float) p.x, (float) p.y, 40f, new float[] {0f, 1f},
                new Color[] {new Color(255, 255, 255, 42), new Color(255, 255, 255, 0)}));
        g.fill(ellipse(p.x - 40, p.y - 40, 80, 80));
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(ellipse(p.x - 10, p.y - 7, 20, 18));
        g.translate(p.x, p.y);
        g.rotate(p.facing);
        g.scale(CHARACTER_SCALE, CHARACTER_SCALE);

        double swing = p.moving ? Math.sin(p.walkPhase) * 3.2 : 0;
        g.setColor(Palette.OUTLINE);
        g.fill(ellipse(swing - 2.5, -6.8, 6.5, 4.6));
        g.fill(ellipse(-swing - 2.5, 2.2, 6.5, 4.6));

        int items = (hasCash ? 1 : 0) + (hasGold ? 1 : 0) + (hasJewels ? 1 : 0);
        if (items > 0) {
            double rr = 3.6 + items * 0.9;
            double sx = -5.5 - rr * 0.6;
            g.setColor(new Color(0x5e4526));
            g.fill(ellipse(sx - rr - 0.9, -rr - 0.9, rr * 2 + 1.8, rr * 2 + 1.8));
            g.setColor(new Color(0xb08d57));
            g.fill(ellipse(sx - rr, -rr, rr * 2, rr * 2));
            g.setColor(new Color(0xcfae78));
            g.fill(ellipse(sx - rr * 0.5, -rr * 0.6, rr * 0.8, rr * 0.7));
            if (hasCash) dot(g, sx - 1.6, -2.2, Palette.CASH);
            if (hasGold) dot(g, sx + 1.2, 0.2, Palette.GOLD);
            if (hasJewels) dot(g, sx - 1.4, 2.4, Palette.JEWEL);
        }

        // Shoulders, seen from above, in a black-and-white striped jumper.
        Shape body = ellipse(-4.6, -8.6, 9.2, 17.2);
        g.setColor(Palette.OUTLINE);
        g.fill(ellipse(-5.6, -9.6, 11.2, 19.2));
        g.setColor(new Color(0xf4f6fa));
        g.fill(body);
        Shape clip = g.getClip();
        g.clip(body);
        g.setColor(new Color(0x1c2233));
        for (double sy = -7.6; sy < 9; sy += 3.6) g.fill(new Rectangle2D.Double(-6, sy, 12, 1.8));
        g.setClip(clip);

        // Head: beanie at the back, face and eye-mask at the front.
        g.setColor(Palette.OUTLINE);
        g.fill(ellipse(-3.6, -4.6, 9.2, 9.2));
        g.setColor(new Color(0xf1c9a5));
        g.fill(ellipse(-2.8, -3.8, 7.6, 7.6));
        g.setColor(new Color(0x1c2233));
        g.fill(ellipse(-3.6, -3.8, 6.2, 7.6));
        g.fill(new Rectangle2D.Double(2.4, -3, 1.3, 6));
        g.setTransform(old);
    }

    private static void dot(Graphics2D g, double cx, double cy, Color c) {
        g.setColor(c);
        g.fill(ellipse(cx - 1.4, cy - 1.4, 2.8, 2.8));
    }

    static void guard(Graphics2D g, Guard gd, double time) {
        AffineTransform old = g.getTransform();
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(ellipse(gd.x - 10, gd.y - 7, 20, 18));

        if (gd.state != Guard.State.UNAWARE) {
            Color ring = gd.state == Guard.State.ALERTED ? Palette.RED : Palette.AMBER;
            double pulse = 0.5 + 0.5 * Math.sin(time * 10);
            g.setColor(Palette.alpha(ring, (int) (120 + 100 * pulse)));
            g.setStroke(new BasicStroke(2f));
            double rr = 13 + pulse * 1.5;
            g.draw(ellipse(gd.x - rr, gd.y - rr, rr * 2, rr * 2));
        }

        g.translate(gd.x, gd.y);
        g.rotate(gd.facing);
        g.scale(CHARACTER_SCALE, CHARACTER_SCALE);
        double swing = gd.moving ? Math.sin(gd.walkPhase) * 3.2 : 0;
        g.setColor(Palette.OUTLINE);
        g.fill(ellipse(swing - 2.5, -6.8, 6.5, 4.6));
        g.fill(ellipse(-swing - 2.5, 2.2, 6.5, 4.6));

        // Torch held out in front.
        g.setColor(Palette.OUTLINE);
        g.fill(new Rectangle2D.Double(2.5, 4.4, 7.5, 3.4));
        g.setColor(new Color(0x596480));
        g.fill(new Rectangle2D.Double(3, 4.9, 5.5, 2.4));
        g.setColor(new Color(0xfff2b0));
        g.fill(new Rectangle2D.Double(8.3, 4.7, 1.8, 2.8));

        // Uniformed shoulders with a badge.
        g.setColor(Palette.OUTLINE);
        g.fill(ellipse(-5.8, -9.8, 11.6, 19.6));
        g.setColor(new Color(0x4f74e8));
        g.fill(ellipse(-4.8, -8.8, 9.6, 17.6));
        g.setColor(new Color(0x2f4fb8));
        g.fill(ellipse(-4.8, -8.8, 5, 17.6));
        g.setColor(Palette.GOLD);
        g.fill(ellipse(1.2, -6.6, 2.6, 2.6));

        // Peaked cap.
        g.setColor(Palette.OUTLINE);
        g.fill(ellipse(-3.8, -4.8, 9.6, 9.6));
        g.setColor(new Color(0xe8b792));
        g.fill(ellipse(-3, -4, 8, 8));
        g.setColor(new Color(0x16224f));
        g.fill(ellipse(-3.6, -4, 7, 8));
        g.fill(new Rectangle2D.Double(1.6, -4.2, 3.8, 8.4));
        g.setColor(Palette.GOLD);
        g.fill(new Rectangle2D.Double(0.4, -1, 1.6, 2));
        g.setTransform(old);

        if (gd.state != Guard.State.UNAWARE) {
            boolean alert = gd.state == Guard.State.ALERTED;
            int bx = (int) Math.round(gd.x) - 7;
            int by = (int) Math.round(gd.y - 35 - Math.abs(Math.sin(time * 7)) * 2);
            g.setColor(Palette.OUTLINE);
            g.fillRoundRect(bx - 1, by - 1, 16, 20, 6, 6);
            g.setColor(alert ? Palette.RED : Palette.AMBER);
            g.fillRoundRect(bx, by, 14, 18, 5, 5);
            PixelFont.draw(g, alert ? "!" : "?", bx + 2, by + 2, 2, Palette.OUTLINE);
        }
    }

    static void camera(Graphics2D g, SecurityCamera cam, double time) {
        AffineTransform old = g.getTransform();
        g.setColor(Palette.OUTLINE);
        g.fill(ellipse(cam.x - 6, cam.y - 6, 12, 12));
        g.setColor(new Color(0x39425a));
        g.fill(ellipse(cam.x - 4.5, cam.y - 4.5, 9, 9));
        g.translate(cam.x, cam.y);
        g.rotate(cam.angle);
        g.setColor(Palette.OUTLINE);
        g.fill(new Rectangle2D.Double(-3, -4.5, 14, 9));
        g.setColor(new Color(0xc5ccda));
        g.fill(new Rectangle2D.Double(-2, -3.5, 11, 7));
        g.setColor(new Color(0x8792ab));
        g.fill(new Rectangle2D.Double(-2, 1, 11, 2.5));
        g.setColor(Palette.OUTLINE);
        g.fill(new Rectangle2D.Double(8, -2.5, 3, 5));
        boolean lit = cam.seesPlayer || Math.sin(time * 5 + cam.x) > 0.2;
        g.setColor(lit ? Palette.RED : new Color(0x6b1f25));
        g.fill(ellipse(-0.5, -2.2, 3, 3));
        g.setTransform(old);
        if (cam.seesPlayer) {
            double pulse = 0.5 + 0.5 * Math.sin(time * 12);
            g.setColor(Palette.alpha(Palette.RED, (int) (120 + 110 * pulse)));
            g.setStroke(new BasicStroke(2f));
            g.draw(ellipse(cam.x - 10, cam.y - 10, 20, 20));
        }
    }

    // ================================================================= icons

    /** 24x24 inventory icon for one category of stolen goods (or the vault). */
    static void itemIcon(Graphics2D g, Station.Kind kind, int x, int y, boolean faded) {
        Composite old = g.getComposite();
        if (faded) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        switch (kind) {
            case CASH: {
                g.setColor(Palette.CASH_DARK);
                g.fillRect(x + 5, y + 4, 17, 11);
                g.setColor(Palette.OUTLINE);
                g.fillRect(x + 1, y + 8, 19, 13);
                g.setColor(Palette.CASH);
                g.fillRect(x + 2, y + 9, 17, 11);
                g.setColor(Palette.CASH_DARK);
                g.fill(ellipse(x + 7.5, y + 11.5, 6, 6));
                g.fillRect(x + 3, y + 10, 2, 2);
                g.fillRect(x + 16, y + 17, 2, 2);
                break;
            }
            case GOLD: {
                Path2D bar = new Path2D.Double();
                bar.moveTo(x + 1, y + 20);
                bar.lineTo(x + 23, y + 20);
                bar.lineTo(x + 19, y + 7);
                bar.lineTo(x + 5, y + 7);
                bar.closePath();
                g.setColor(Palette.GOLD_DARK);
                g.fill(bar);
                Path2D top = new Path2D.Double();
                top.moveTo(x + 3.5, y + 17);
                top.lineTo(x + 20.5, y + 17);
                top.lineTo(x + 18, y + 8.5);
                top.lineTo(x + 6, y + 8.5);
                top.closePath();
                g.setColor(Palette.GOLD);
                g.fill(top);
                g.setColor(Palette.GOLD_LIGHT);
                g.fill(new Rectangle2D.Double(x + 7, y + 10, 10, 2));
                break;
            }
            case JEWELLERY: {
                Path2D gemShape = new Path2D.Double();
                gemShape.moveTo(x + 6, y + 4);
                gemShape.lineTo(x + 18, y + 4);
                gemShape.lineTo(x + 23, y + 10);
                gemShape.lineTo(x + 12, y + 22);
                gemShape.lineTo(x + 1, y + 10);
                gemShape.closePath();
                g.setColor(Palette.JEWEL_DARK);
                g.fill(gemShape);
                Path2D face = new Path2D.Double();
                face.moveTo(x + 7, y + 5.5);
                face.lineTo(x + 17, y + 5.5);
                face.lineTo(x + 20, y + 10);
                face.lineTo(x + 4, y + 10);
                face.closePath();
                g.setColor(Palette.JEWEL);
                g.fill(face);
                Path2D shine = new Path2D.Double();
                shine.moveTo(x + 8, y + 10);
                shine.lineTo(x + 16, y + 10);
                shine.lineTo(x + 12, y + 20);
                shine.closePath();
                g.setColor(new Color(0xa8f3f8));
                g.fill(shine);
                break;
            }
            default: {
                g.setColor(Palette.OUTLINE);
                g.fill(ellipse(x + 1, y + 1, 22, 22));
                g.setColor(STEEL_LIGHT);
                g.fill(ellipse(x + 2.5, y + 2.5, 19, 19));
                g.setColor(STEEL_DARK);
                g.setStroke(new BasicStroke(2f));
                g.draw(ellipse(x + 6, y + 6, 12, 12));
                g.draw(new Line2D.Double(x + 12, y + 5, x + 12, y + 19));
                g.draw(new Line2D.Double(x + 5, y + 12, x + 19, y + 12));
                g.setColor(Palette.GOLD);
                g.fill(ellipse(x + 10, y + 10, 4, 4));
                break;
            }
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
        g.setComposite(old);
    }
}
