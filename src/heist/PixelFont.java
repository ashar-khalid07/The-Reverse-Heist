package heist;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny built-in 5x7 bitmap font. Using our own font keeps the pixel-art look
 * identical on every operating system and needs no font files.
 * Lower-case letters are drawn as capitals. '~' draws a tick mark.
 */
final class PixelFont {
    private PixelFont() {}

    /** Each glyph is stored as horizontal runs: {row, startColumn, length}. */
    private static final Map<Character, int[][]> GLYPHS = new HashMap<Character, int[][]>();

    private static void def(char ch, String... rows) {
        List<int[]> runs = new ArrayList<int[]>();
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            int c = 0;
            while (c < row.length()) {
                if (row.charAt(c) == '#') {
                    int start = c;
                    while (c < row.length() && row.charAt(c) == '#') c++;
                    runs.add(new int[] {r, start, c - start});
                } else {
                    c++;
                }
            }
        }
        GLYPHS.put(ch, runs.toArray(new int[0][]));
    }

    static {
        def('A', ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        def('B', "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.");
        def('C', ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###.");
        def('D', "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####.");
        def('E', "#####", "#....", "#....", "####.", "#....", "#....", "#####");
        def('F', "#####", "#....", "#....", "####.", "#....", "#....", "#....");
        def('G', ".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".###.");
        def('H', "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        def('I', ".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###.");
        def('J', "..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##..");
        def('K', "#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#");
        def('L', "#....", "#....", "#....", "#....", "#....", "#....", "#####");
        def('M', "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#");
        def('N', "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#");
        def('O', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        def('P', "####.", "#...#", "#...#", "####.", "#....", "#....", "#....");
        def('Q', ".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#");
        def('R', "####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#");
        def('S', ".####", "#....", "#....", ".###.", "....#", "....#", "####.");
        def('T', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..");
        def('U', "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        def('V', "#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#..");
        def('W', "#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#");
        def('X', "#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#");
        def('Y', "#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..");
        def('Z', "#####", "....#", "...#.", "..#..", ".#...", "#....", "#####");
        def('0', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        def('1', "..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###.");
        def('2', ".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####");
        def('3', ".###.", "#...#", "....#", "..##.", "....#", "#...#", ".###.");
        def('4', "...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#.");
        def('5', "#####", "#....", "####.", "....#", "....#", "#...#", ".###.");
        def('6', ".###.", "#....", "#....", "####.", "#...#", "#...#", ".###.");
        def('7', "#####", "....#", "...#.", "..#..", "..#..", "..#..", "..#..");
        def('8', ".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###.");
        def('9', ".###.", "#...#", "#...#", ".####", "....#", "....#", ".###.");
        def('.', ".....", ".....", ".....", ".....", ".....", ".....", "..#..");
        def(',', ".....", ".....", ".....", ".....", ".....", "..#..", ".#...");
        def('!', "..#..", "..#..", "..#..", "..#..", "..#..", ".....", "..#..");
        def('?', ".###.", "#...#", "....#", "...#.", "..#..", ".....", "..#..");
        def(':', ".....", ".....", "..#..", ".....", "..#..", ".....", ".....");
        def('\'', "..#..", "..#..", ".....", ".....", ".....", ".....", ".....");
        def('"', ".#.#.", ".#.#.", ".....", ".....", ".....", ".....", ".....");
        def('-', ".....", ".....", ".....", ".###.", ".....", ".....", ".....");
        def('/', "....#", "....#", "...#.", "..#..", ".#...", "#....", "#....");
        def('%', "##..#", "##..#", "...#.", "..#..", ".#...", "#..##", "#..##");
        def('(', "...#.", "..#..", ".#...", ".#...", ".#...", "..#..", "...#.");
        def(')', ".#...", "..#..", "...#.", "...#.", "...#.", "..#..", ".#...");
        def('+', ".....", "..#..", "..#..", "#####", "..#..", "..#..", ".....");
        def('>', "#....", ".#...", "..#..", "...#.", "..#..", ".#...", "#....");
        def('<', "....#", "...#.", "..#..", ".#...", "..#..", "...#.", "....#");
        def('$', ".####", "#.#..", "#.#..", ".###.", "..#.#", "..#.#", "####.");
        def('*', ".....", ".....", ".###.", ".###.", ".###.", ".....", ".....");
        def('~', ".....", "....#", "....#", "...#.", "#.#..", ".#...", ".....");
    }

    /** Pixel width of a string at the given scale. */
    static int width(String s, int scale) {
        return s.isEmpty() ? 0 : (s.length() * 6 - 1) * scale;
    }

    static void draw(Graphics2D g, String s, int x, int y, int scale, Color color) {
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(color);
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            int[][] runs = GLYPHS.get(Character.toUpperCase(s.charAt(i)));
            if (runs != null) {
                for (int[] run : runs) {
                    g.fillRect(cx + run[1] * scale, y + run[0] * scale, run[2] * scale, scale);
                }
            }
            cx += 6 * scale;
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
    }

    static void drawCentered(Graphics2D g, String s, int centerX, int y, int scale, Color color) {
        draw(g, s, centerX - width(s, scale) / 2, y, scale, color);
    }

    static void drawRight(Graphics2D g, String s, int rightX, int y, int scale, Color color) {
        draw(g, s, rightX - width(s, scale), y, scale, color);
    }

    /** Text with a dark drop shadow so it stays readable over the game world. */
    static void drawShadowed(Graphics2D g, String s, int x, int y, int scale, Color color) {
        draw(g, s, x + scale, y + scale, scale, new Color(0, 0, 0, 170));
        draw(g, s, x, y, scale, color);
    }

    static void drawCenteredShadowed(Graphics2D g, String s, int centerX, int y, int scale, Color color) {
        drawShadowed(g, s, centerX - width(s, scale) / 2, y, scale, color);
    }
}
