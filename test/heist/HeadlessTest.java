package heist;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Automated checks that run without a window: rules, collisions, win/lose
 * conditions, restart, and a full stealth play-through driven by simulated
 * key presses that proves the level can be completed inside the time limit.
 * Also saves screenshots of each screen.
 *
 * Run with:  java -Djava.awt.headless=true -cp build/classes:build/test heist.HeadlessTest build/shots
 */
public final class HeadlessTest {
    private static int failures;
    private static File shotDir;
    private static final Renderer RENDERER = new Renderer();

    public static void main(String[] args) throws Exception {
        shotDir = new File(args.length > 0 ? args[0] : "build/shots");
        shotDir.mkdirs();
        levelSanity();
        collisionFuzz();
        caughtByGuard();
        cameraMeterRisesAndFalls();
        timeUpAndRestart();
        depositRules();
        stealthPlaythrough();
        menuShot();
        audioSynthesis();
        System.out.println(failures == 0 ? "\nALL CHECKS PASSED" : "\n" + failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + what);
        if (!ok) failures++;
    }

    private static void shot(Game g, String name) throws Exception {
        BufferedImage img = new BufferedImage(1440, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D gr = img.createGraphics();
        RENDERER.render(gr, g, 1440, 900, -1, -1);
        gr.dispose();
        ImageIO.write(img, "png", new File(shotDir, name + ".png"));
    }

    /** A 630x500 cover image, the size itch.io asks for. */
    private static void cover(Game g) throws Exception {
        BufferedImage wide = new BufferedImage(1920, 1200, BufferedImage.TYPE_INT_RGB);
        Graphics2D wg = wide.createGraphics();
        RENDERER.render(wg, g, 1920, 1200, -1, -1);
        wg.dispose();
        BufferedImage img = new BufferedImage(630, 500, BufferedImage.TYPE_INT_RGB);
        Graphics2D c = img.createGraphics();
        c.drawImage(wide.getSubimage(520, 112, 1210, 960), 0, 0, 630, 500, null);
        c.setColor(new java.awt.Color(6, 8, 15, 150));
        c.fillRect(0, 0, 630, 500);
        c.setColor(new java.awt.Color(6, 8, 15, 215));
        c.fillRect(0, 96, 630, 268);
        c.setColor(Palette.GOLD);
        c.fillRect(0, 96, 630, 4);
        c.fillRect(0, 360, 630, 4);
        PixelFont.drawCentered(c, "THE", 315, 124, 5, Palette.TEXT);
        PixelFont.drawCentered(c, "REVERSE", 319, 177, 12, new java.awt.Color(0, 0, 0));
        PixelFont.drawCentered(c, "REVERSE", 315, 172, 12, Palette.GOLD);
        PixelFont.drawCentered(c, "HEIST", 319, 273, 12, new java.awt.Color(0, 0, 0));
        PixelFont.drawCentered(c, "HEIST", 315, 268, 12, Palette.TEXT);
        PixelFont.drawCentered(c, "THE PERFECT HEIST IS", 315, 392, 3, Palette.TEXT);
        PixelFont.drawCentered(c, "PUTTING EVERYTHING BACK.", 315, 424, 3, Palette.TEXT);
        c.dispose();
        ImageIO.write(img, "png", new File(shotDir, "cover-630x500.png"));
    }

    private static void run(Game g, Input in, double seconds) {
        int frames = (int) Math.round(seconds / Game.STEP);
        for (int i = 0; i < frames; i++) g.update(Game.STEP, in);
    }

    private static void teleport(Game g, int col, int row) {
        g.player.x = Level.center(col);
        g.player.y = Level.center(row);
        g.player.vx = 0;
        g.player.vy = 0;
    }

    private static void pressE(Game g, Input in) {
        in.interactPressed = true;
        in.interactHeld = true;
        g.update(Game.STEP, in);
        in.interactHeld = false;
        g.update(Game.STEP, in);
    }

    // ------------------------------------------------------------------ tests

    private static void levelSanity() {
        Game g = new Game();
        int sc = Level.toTile(Level.START_X), sr = Level.toTile(Level.START_Y);
        check(!g.level.solid(sc, sr), "player start is on open floor");
        for (Station s : g.stations) {
            boolean reachable = false;
            for (int[] t : useTiles(g.level, s)) {
                if (g.level.findPath(sc, sr, t[0], t[1]) != null) reachable = true;
            }
            check(reachable, s.kind.label + " can be reached from the entrance");
        }
        check(g.guards.size() == 5 && g.cameras.size() == 4, "5 guards and 4 cameras are placed");
    }

    private static void collisionFuzz() {
        Game g = new Game();
        g.startGame();
        Input in = new Input();
        Random random = new Random(1);
        boolean everInsideWall = false;
        double farthest = 0;
        for (int i = 0; i < 120 * 150; i++) {
            if (i % 24 == 0) {
                in.up = random.nextBoolean();
                in.down = random.nextInt(3) == 0;
                in.left = random.nextBoolean();
                in.right = random.nextBoolean();
            }
            g.update(Game.STEP, in);
            if (g.screen != Game.Screen.PLAYING) g.startGame();
            if (g.level.collides(g.player.x, g.player.y, Player.RADIUS - 0.01)) everInsideWall = true;
            farthest = Math.max(farthest, Math.hypot(g.player.x - Level.START_X, g.player.y - Level.START_Y));
        }
        check(!everInsideWall, "player never ends up inside a wall (150s of random input)");
        check(farthest > 150, "player can actually move around (got " + (int) farthest + "px from start)");
    }

    private static void caughtByGuard() throws Exception {
        Game g = new Game();
        g.startGame();
        Input in = new Input();
        Guard guard = g.guards.get(0);
        boolean sawSuspicious = false, sawAlerted = false, shotTaken = false;
        double caughtAt = -1;
        for (int i = 0; i < 120 * 8 && g.screen == Game.Screen.PLAYING; i++) {
            double px = guard.x + Math.cos(guard.facing) * 60, py = guard.y + Math.sin(guard.facing) * 60;
            if (!g.level.collides(px, py, Player.RADIUS)) {
                g.player.x = px;
                g.player.y = py;
            }
            g.update(Game.STEP, in);
            if (guard.state == Guard.State.SUSPICIOUS) sawSuspicious = true;
            if (guard.state == Guard.State.ALERTED) sawAlerted = true;
            if (!shotTaken && g.detection > 62) {
                shot(g, "3-detected");
                shotTaken = true;
            }
            caughtAt = i * Game.STEP;
        }
        check(g.screen == Game.Screen.GAME_OVER && g.fail == Game.Fail.CAUGHT,
                "standing in front of a guard gets you caught (after " + String.format("%.1f", caughtAt) + "s)");
        check(caughtAt > 1.0, "there is at least a second of warning before being caught");
        check(sawSuspicious && sawAlerted, "guard goes unaware -> suspicious -> alerted");
        check(g.timesSpotted >= 1, "being spotted is counted");
        run(g, in, 1.0);
        shot(g, "5-game-over");
    }

    private static void cameraMeterRisesAndFalls() {
        Game g = new Game();
        g.startGame();
        Input in = new Input();
        SecurityCamera cam = g.cameras.get(3);
        for (int i = 0; i < 120; i++) {
            g.player.x = cam.x + Math.cos(cam.angle) * 70;
            g.player.y = cam.y + Math.sin(cam.angle) * 70;
            g.update(Game.STEP, in);
        }
        double seen = g.detection;
        check(seen > 25 && seen < 60, "one second in a camera cone raises detection (to " + (int) seen + "%)");
        teleport(g, 11, 18);
        run(g, in, 4.0);
        check(g.detection == 0, "detection drains away after leaving the cone");
    }

    private static void timeUpAndRestart() throws Exception {
        Game g = new Game();
        g.startGame();
        Input in = new Input();
        teleport(g, 11, 18); // tucked behind crates in the staff room
        run(g, in, 60);
        check(Math.abs(g.timeLeft - 120) < 0.05, "timer counts down in real time (60s played -> "
                + String.format("%.2f", g.timeLeft) + "s left)");
        run(g, in, 100);
        check(g.timeLeft < 30 && g.screen == Game.Screen.PLAYING, "still playing with under 30s left");
        run(g, in, 21);
        check(g.screen == Game.Screen.GAME_OVER && g.fail == Game.Fail.TIME_UP, "timer reaching zero ends the game");
        check(g.peakDetection == 0, "a player who stays hidden is never detected");

        run(g, in, 1.0);
        in.confirmPressed = true;
        g.update(Game.STEP, in);
        boolean fresh = g.screen == Game.Screen.PLAYING && g.timeLeft > 179.9 && g.detection == 0
                && g.returnedCount() == 0 && !g.vault.done && g.timesSpotted == 0
                && Math.abs(g.player.x - Level.START_X) < 1 && Math.abs(g.player.y - Level.START_Y) < 1;
        check(fresh, "restart resets timer, detection, objectives and player position");
    }

    private static void depositRules() {
        Game g = new Game();
        g.startGame();
        Input in = new Input();

        teleport(g, 19, 5);
        in.interactPressed = true;
        in.interactHeld = true;
        run(g, in, 0.5);
        in.interactHeld = false;
        check(g.screen == Game.Screen.PLAYING && !g.vault.done && g.vaultProgress == 0,
                "vault cannot be secured while goods are still outstanding");

        teleport(g, 2, 1);
        pressE(g, in);
        check(g.isDone(Station.Kind.CASH) && !g.isDone(Station.Kind.GOLD) && !g.isDone(Station.Kind.JEWELLERY),
                "cash point accepts only the cash");
        teleport(g, 38, 2);
        pressE(g, in);
        check(g.isDone(Station.Kind.GOLD) && !g.isDone(Station.Kind.JEWELLERY), "gold point accepts only the gold");
        teleport(g, 20, 14);
        pressE(g, in);
        check(g.returnedCount() == 2, "pressing E away from a return point does nothing");
        teleport(g, 38, 17);
        pressE(g, in);
        check(g.allReturned(), "all three categories can be returned");

        teleport(g, 11, 18); // hide for a moment so earlier glimpses drain away
        run(g, in, 5.0);
        teleport(g, 20, 5);
        in.interactPressed = true;
        in.interactHeld = true;
        run(g, in, 1.0);
        check(g.screen == Game.Screen.PLAYING && g.vaultProgress > 0.5, "vault needs E held, not just tapped");
        run(g, in, 0.7);
        check(g.screen == Game.Screen.VICTORY && g.vault.done, "holding E at the vault with everything returned wins");
    }

    // ------------------------------------------------ full stealth play-through

    private static final double PLAN_STEP = 0.25;
    private static final int FRAMES_PER_STEP = (int) Math.round(PLAN_STEP / Game.STEP);
    private static final int PLAN_STEPS = 700;
    private static final int HOLD_STEPS = 9;
    private static final int N = Level.COLS * Level.ROWS;

    /** Floor tiles from which a station is comfortably within reach. */
    private static List<int[]> useTiles(Level level, Station s) {
        List<int[]> tiles = new ArrayList<int[]>();
        for (int r = 0; r < Level.ROWS; r++) {
            for (int c = 0; c < Level.COLS; c++) {
                if (level.solid(c, r)) continue;
                if (Math.hypot(Level.center(c) - s.x, Level.center(r) - s.y) <= s.reach - 4) tiles.add(new int[] {c, r});
            }
        }
        return tiles;
    }

    /**
     * Works out, for every quarter second of the game, which tiles are out of
     * sight of every guard and camera; searches for a route that never steps
     * on a watched tile; then plays that route with simulated key presses.
     */
    private static void stealthPlaythrough() throws Exception {
        Game sim = new Game();
        sim.startGame();
        Level level = sim.level;

        boolean[][] watched = new boolean[PLAN_STEPS + 2][N];
        int totalFrames = (PLAN_STEPS + 1) * FRAMES_PER_STEP;
        int margin = FRAMES_PER_STEP + 6;
        double[][] offsets = {{0, 0}, {9, 0}, {-9, 0}, {0, 9}, {0, -9}};
        for (int f = 0; f <= totalFrames; f++) {
            if (f % 3 == 0) {
                int kFrom = Math.max(0, (int) Math.ceil((f - margin) / (double) FRAMES_PER_STEP));
                int kTo = Math.min(PLAN_STEPS + 1, (int) Math.floor((f + margin) / (double) FRAMES_PER_STEP));
                for (int tile = 0; tile < N; tile++) {
                    int c = tile % Level.COLS, r = tile / Level.COLS;
                    if (level.solid(c, r)) continue;
                    boolean seen = false;
                    for (double[] o : offsets) {
                        double px = Level.center(c) + o[0], py = Level.center(r) + o[1];
                        for (Guard gd : sim.guards) if (gd.canSee(level, px, py)) seen = true;
                        for (SecurityCamera cam : sim.cameras) if (cam.canSee(level, px, py)) seen = true;
                        if (seen) break;
                    }
                    if (seen) for (int k = kFrom; k <= kTo; k++) watched[k][tile] = true;
                }
            }
            sim.updateSecurity(Game.STEP, false);
        }

        Station[] goods = {sim.stations.get(0), sim.stations.get(1), sim.stations.get(2)};
        int[][] orders = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        int[] bestPlan = null;
        String bestOrder = "";
        for (int[] order : orders) {
            Station[] legs = {goods[order[0]], goods[order[1]], goods[order[2]], sim.vault};
            int[] plan = planRoute(level, watched, legs);
            String name = legs[0].kind.label + " > " + legs[1].kind.label + " > " + legs[2].kind.label;
            System.out.println("      route " + name + ": "
                    + (plan == null ? "no unseen route" : String.format("%.1fs", plan.length * PLAN_STEP)));
            if (plan != null && (bestPlan == null || plan.length < bestPlan.length)) {
                bestPlan = plan;
                bestOrder = name;
            }
        }
        check(bestPlan != null, "a completely unseen route through the bank exists");
        if (bestPlan == null) return;

        // Play it for real, with key presses only.
        Game g = new Game();
        g.startGame();
        Input in = new Input();
        int frame = 0;
        while (g.screen == Game.Screen.PLAYING && frame < 180 * 120) {
            double t = frame * Game.STEP;
            int k = Math.min((int) (t / PLAN_STEP), bestPlan.length - 2);
            double u = Math.min(1, (t - k * PLAN_STEP) / PLAN_STEP);
            int a = bestPlan[k], b = bestPlan[k + 1];
            double gx = Level.center(a % Level.COLS) + (Level.center(b % Level.COLS) - Level.center(a % Level.COLS)) * u;
            double gy = Level.center(a / Level.COLS) + (Level.center(b / Level.COLS) - Level.center(a / Level.COLS)) * u;
            double dx = gx - g.player.x, dy = gy - g.player.y;
            in.right = dx > 1.5;
            in.left = dx < -1.5;
            in.down = dy > 1.5;
            in.up = dy < -1.5;
            boolean wantE = g.nearby != null && !g.nearby.done
                    && (g.nearby.kind != Station.Kind.VAULT || g.allReturned());
            if (wantE && !in.interactHeld) in.interactPressed = true;
            in.interactHeld = wantE;
            g.update(Game.STEP, in);
            frame++;
            if (frame == 120 * 4) shot(g, "2-gameplay-start");
            if (frame == 120 * 30) shot(g, "2-gameplay-mid");
            if (frame == 120 * 14) cover(g);
        }
        check(g.screen == Game.Screen.VICTORY, "simulated player completes the whole game (" + bestOrder + ")");
        check(g.completionTime < 150, "completed in " + String.format("%.1f", g.completionTime)
                + "s of the 180s allowed");
        check(g.peakDetection < 50, "peak detection on that run was " + (int) g.peakDetection
                + "%, spotted " + g.timesSpotted + " time(s)");
        run(g, in, 1.2);
        shot(g, "4-victory");
    }

    /** Time-expanded breadth-first search over (tile, time). Returns the tile to stand on at each step. */
    private static int[] planRoute(Level level, boolean[][] watched, Station[] legs) {
        int legCount = legs.length;
        // parent[leg][step][tile]: -1 unreached, -2 arrived from the previous leg, otherwise previous tile.
        int[][][] parent = new int[legCount][PLAN_STEPS + 1][N];
        for (int[][] a : parent) for (int[] b : a) Arrays.fill(b, -1);
        boolean[][] goal = new boolean[legCount][N];
        for (int i = 0; i < legCount; i++) {
            for (int[] t : useTiles(level, legs[i])) goal[i][t[1] * Level.COLS + t[0]] = true;
        }
        int start = Level.toTile(Level.START_Y) * Level.COLS + Level.toTile(Level.START_X);
        parent[0][0][start] = start;
        int[] moves = {0, 1, -1, Level.COLS, -Level.COLS};

        for (int k = 0; k < PLAN_STEPS; k++) {
            for (int leg = 0; leg < legCount; leg++) {
                for (int tile = 0; tile < N; tile++) {
                    if (parent[leg][k][tile] == -1) continue;
                    // Reaching a return point hands over to the next leg at the same moment.
                    if (goal[leg][tile] && leg + 1 < legCount && parent[leg + 1][k][tile] == -1) {
                        parent[leg + 1][k][tile] = -2;
                    }
                    for (int m : moves) {
                        int next = tile + m;
                        if (next < 0 || next >= N) continue;
                        if (level.solid(next % Level.COLS, next / Level.COLS)) continue;
                        if (watched[k + 1][next]) continue;
                        if (parent[leg][k + 1][next] == -1) parent[leg][k + 1][next] = tile;
                    }
                }
            }
            // Finished? Need to be at the vault, able to stand there unseen while holding E.
            int last = legCount - 1;
            for (int tile = 0; tile < N; tile++) {
                if (parent[last][k][tile] == -1 || !goal[last][tile]) continue;
                boolean canHold = k + HOLD_STEPS <= PLAN_STEPS;
                for (int j = 0; canHold && j <= HOLD_STEPS; j++) if (watched[k + j][tile]) canHold = false;
                if (!canHold) continue;
                int[] plan = new int[k + HOLD_STEPS + 1];
                Arrays.fill(plan, tile);
                int leg = last, step = k, cur = tile;
                while (true) {
                    plan[step] = cur;
                    int p = parent[leg][step][cur];
                    if (p == -2) {
                        leg--;
                        continue;
                    }
                    if (leg == 0 && step == 0) break;
                    cur = p;
                    step--;
                }
                return plan;
            }
        }
        return null;
    }

    private static void audioSynthesis() {
        double[][] buffers = {
            Audio.footstep(), Audio.vaultTick(), Audio.alarm(), Audio.musicLoop(),
            Audio.notes(new double[] {440, 0, 660}, 0.1, 0.3, 2, 0.3),
        };
        boolean sane = true;
        double musicPeak = 0;
        for (int b = 0; b < buffers.length; b++) {
            double peak = 0;
            for (double v : buffers[b]) {
                if (Double.isNaN(v) || Double.isInfinite(v)) sane = false;
                peak = Math.max(peak, Math.abs(v));
            }
            if (peak < 0.02 || peak > 1.0) sane = false;
            if (b == 3) musicPeak = peak;
        }
        check(sane, "generated sounds are audible and not clipping (music peak "
                + String.format("%.2f", musicPeak) + ")");
    }

    private static void menuShot() throws Exception {
        Game g = new Game();
        Input in = new Input();
        run(g, in, 6);
        shot(g, "1-menu");
        check(g.screen == Game.Screen.MENU, "game starts on the main menu");
        in.confirmPressed = true;
        g.update(Game.STEP, in);
        check(g.screen == Game.Screen.PLAYING, "pressing Enter on the menu starts the game");
        in.pausePressed = true;
        g.update(Game.STEP, in);
        double frozen = g.timeLeft;
        run(g, in, 2);
        check(g.screen == Game.Screen.PAUSED && g.timeLeft == frozen, "pausing freezes the timer");
    }
}
