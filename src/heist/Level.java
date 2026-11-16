package heist;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The bank: a 40x20 tile map plus the geometry helpers everything else uses
 * (collision, line of sight, ray casting and grid path-finding).
 *
 * Map legend
 *   #  wall                 .  floor
 *   X  vault interior       V  vault door (final objective)
 *   C  cash return point    G  gold return point     J  jewellery return point
 *   s  shelf   b  crate   c  counter   d  desk   p  plant
 *   j  display case   g  gold pallet   m  cash table   t  bench
 *   D  entrance doors
 */
final class Level {
    static final int TILE = 24;
    static final int COLS = 40;
    static final int ROWS = 20;
    static final int WIDTH = COLS * TILE;
    static final int HEIGHT = ROWS * TILE;

    /** Where the player begins: just inside the entrance doors. */
    static final double START_X = 20 * TILE;
    static final double START_Y = 17 * TILE + TILE / 2.0;

    private static final String FULL_WALL = "########################################";
    private static final String CORRIDOR = "#......................................#";

    /**
     * Builds one map row from its three rooms. Each row is
     * wall + left room (11) + divider + middle room (14) + divider + right room (11) + wall.
     */
    private static String row(String left, char dividerL, String middle, char dividerR, String right) {
        if (left.length() != 11 || middle.length() != 14 || right.length() != 11) {
            throw new IllegalStateException("Bad map row: " + left + "|" + middle + "|" + right);
        }
        return "#" + left + dividerL + middle + dividerR + right + "#";
    }

    private static final String[] MAP = {
        FULL_WALL,
        //   CASH STORAGE         MAIN VAULT            GOLD RESERVE
        row("C........mm", '#', "XXXXXXXXXXXXXX", '#', "........ggG"),
        row("...........", '#', "XXXXXXXXXXXXXX", '#', "..........."),
        row("..ss..ss...", '#', "XXXXXXXXXXXXXX", '#', "..ss..gg..."),
        row("..ss..ss..b", '#', "######VV######", '#', "..ss..gg..."),
        row("...........", '#', "..............", '#', "..........."),
        row(".b..mm.....", '.', "..p........p..", '.', "....b.....s"),
        row("..........s", '#', "..............", '#', "..........s"),
        row("########..#", '#', "#####....#####", '#', "#####..####"),
        CORRIDOR,
        CORRIDOR,
        row("##..#######", '#', "#####....#####", '#', "#..########"),
        //   STAFF ROOM           LOBBY / ENTRANCE      JEWELLERY
        row("p.....dd...", '#', "..............", '#', "..........."),
        row("...........", '#', ".cccc....cccc.", '#', ".jjjj.jjjj."),
        row(".dd..s..dd.", '#', "..............", '#', "..........."),
        row(".....s.....", '.', "..............", '#', ".jj.jjjj.j."),
        row(".dd........", '#', "..tt......tt..", '.', "..........."),
        row(".........bb", '#', "..............", '#', ".jjjjj.jjj."),
        row("sss..p.....", '#', "p............p", '#', "..........J"),
        "###################DD###################",
    };

    final char[][] tiles = new char[ROWS][COLS];

    Level() {
        if (MAP.length != ROWS) throw new IllegalStateException("Map must have " + ROWS + " rows");
        for (int r = 0; r < ROWS; r++) {
            if (MAP[r].length() != COLS) {
                throw new IllegalStateException("Map row " + r + " has length " + MAP[r].length());
            }
            tiles[r] = MAP[r].toCharArray();
        }
    }

    /** Tile at a grid position; anything outside the map counts as wall. */
    char tile(int c, int r) {
        if (c < 0 || r < 0 || c >= COLS || r >= ROWS) return '#';
        return tiles[r][c];
    }

    boolean solid(int c, int r) {
        return tile(c, r) != '.';
    }

    static boolean wallLike(char t) {
        return t == '#' || t == 'X' || t == 'V' || t == 'D';
    }

    static int toTile(double pixel) {
        return (int) Math.floor(pixel / TILE);
    }

    static double center(int tileIndex) {
        return tileIndex * TILE + TILE / 2.0;
    }

    /** True if a circle at (x,y) overlaps any solid tile. */
    boolean collides(double x, double y, double radius) {
        int c0 = toTile(x - radius), c1 = toTile(x + radius);
        int r0 = toTile(y - radius), r1 = toTile(y + radius);
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                if (!solid(c, r)) continue;
                double nx = Math.max(c * TILE, Math.min(x, (c + 1) * TILE));
                double ny = Math.max(r * TILE, Math.min(y, (r + 1) * TILE));
                double dx = x - nx, dy = y - ny;
                if (dx * dx + dy * dy < radius * radius) return true;
            }
        }
        return false;
    }

    /**
     * Casts a ray from (x,y) and returns how far it travels before hitting a
     * solid tile, capped at maxDist. Walks the grid cell by cell (DDA).
     */
    double castRay(double x, double y, double angle, double maxDist) {
        double dx = Math.cos(angle), dy = Math.sin(angle);
        int c = toTile(x), r = toTile(y);
        int stepC = dx > 0 ? 1 : -1, stepR = dy > 0 ? 1 : -1;
        double inf = Double.MAX_VALUE;
        double deltaX = dx == 0 ? inf : Math.abs(TILE / dx);
        double deltaY = dy == 0 ? inf : Math.abs(TILE / dy);
        double nextX = dx == 0 ? inf : (dx > 0 ? (c + 1) * TILE - x : x - c * TILE) / Math.abs(dx);
        double nextY = dy == 0 ? inf : (dy > 0 ? (r + 1) * TILE - y : y - r * TILE) / Math.abs(dy);
        while (true) {
            double t;
            if (nextX < nextY) {
                t = nextX;
                nextX += deltaX;
                c += stepC;
            } else {
                t = nextY;
                nextY += deltaY;
                r += stepR;
            }
            if (t >= maxDist) return maxDist;
            if (solid(c, r)) return t;
        }
    }

    boolean lineOfSight(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 0.001) return true;
        return castRay(x1, y1, Math.atan2(dy, dx), dist) >= dist - 0.001;
    }

    /**
     * Shortest walkable route between two tiles (breadth-first search, 4 directions).
     * Returns the tiles from start to goal inclusive, or null if unreachable.
     */
    List<int[]> findPath(int c1, int r1, int c2, int r2) {
        if (solid(c1, r1) || solid(c2, r2)) return null;
        int[] prev = new int[COLS * ROWS];
        java.util.Arrays.fill(prev, -1);
        int start = r1 * COLS + c1, goal = r2 * COLS + c2;
        ArrayDeque<Integer> queue = new ArrayDeque<Integer>();
        queue.add(start);
        prev[start] = start;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            if (cur == goal) break;
            int cc = cur % COLS, cr = cur / COLS;
            for (int[] d : dirs) {
                int nc = cc + d[0], nr = cr + d[1];
                if (solid(nc, nr)) continue;
                int ni = nr * COLS + nc;
                if (prev[ni] != -1) continue;
                prev[ni] = cur;
                queue.add(ni);
            }
        }
        if (prev[goal] == -1) return null;
        List<int[]> path = new ArrayList<int[]>();
        int cur = goal;
        while (true) {
            path.add(new int[] {cur % COLS, cur / COLS});
            if (cur == start) break;
            cur = prev[cur];
        }
        Collections.reverse(path);
        return path;
    }

    /** Smallest signed difference between two angles, in the range -PI..PI. */
    static double angleDiff(double a, double b) {
        double d = (a - b) % (Math.PI * 2);
        if (d > Math.PI) d -= Math.PI * 2;
        if (d < -Math.PI) d += Math.PI * 2;
        return d;
    }
}
