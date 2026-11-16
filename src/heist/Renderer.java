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
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/**
 * Draws the whole game from a {@link Game}'s current state. It never changes
 * game state. Everything is laid out on a fixed 960x600 canvas which is then
 * scaled to fit whatever size the window is.
 */
final class Renderer {
    static final int W = 960;
    static final int H = 600;
    /** The map sits between the top HUD bar and the bottom inventory bar. */
    static final int TOP = 56;
    static final int BOTTOM = TOP + Level.HEIGHT;

    /** How the 960x600 canvas is placed in the window; the window uses these to map mouse clicks. */
    double scale = 1, offsetX, offsetY;

    private BufferedImage mapCache;
    private double mapCacheScale = -1;
    private BufferedImage vignette;

    void render(Graphics2D target, Game game, int width, int height, double mouseX, double mouseY) {
        Graphics2D g = (Graphics2D) target.create();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);

        scale = Math.min(width / (double) W, height / (double) H);
        offsetX = Math.floor((width - W * scale) / 2);
        offsetY = Math.floor((height - H * scale) / 2);
        g.translate(offsetX, offsetY);
        g.scale(scale, scale);
        g.clipRect(0, 0, W, H);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Palette.VOID);
        g.fillRect(0, 0, W, H);

        AffineTransform base = g.getTransform();
        if (game.screen == Game.Screen.GAME_OVER && game.fail == Game.Fail.CAUGHT && game.screenTime < 0.5) {
            double k = 1 - game.screenTime / 0.5;
            g.translate(Math.sin(game.screenTime * 95) * 5 * k, Math.cos(game.screenTime * 80) * 3 * k);
        }
        drawWorld(g, game);
        g.setTransform(base);

        if (game.screen == Game.Screen.MENU) {
            drawMenu(g, game, mouseX, mouseY);
        } else {
            drawTopBar(g, game);
            drawBottomBar(g, game);
            if (game.screen == Game.Screen.PLAYING) drawToasts(g, game);
            if (game.screen == Game.Screen.PAUSED) drawPause(g);
            if (game.screen == Game.Screen.VICTORY) drawVictory(g, game, mouseX, mouseY);
            if (game.screen == Game.Screen.GAME_OVER) drawGameOver(g, game, mouseX, mouseY);
        }
        g.dispose();
    }

    // ================================================================= world

    private void drawWorld(Graphics2D g, Game game) {
        AffineTransform before = g.getTransform();
        g.translate(0, TOP);
        boolean playing = game.screen != Game.Screen.MENU;
        double time = game.worldTime;

        drawMapCache(g, game.level);

        for (Station s : game.stations) {
            if (s.kind == Station.Kind.VAULT) {
                Sprites.vaultDoor(g, s, playing && game.allReturned(), game.vaultProgress, time);
            } else {
                Sprites.station(g, s, time, playing);
            }
        }

        for (SecurityCamera cam : game.cameras) {
            Color c = Palette.alpha(Palette.RED, cam.seesPlayer ? 120 : 44);
            drawCone(g, game.level, cam.x, cam.y, cam.angle, SecurityCamera.FOV, SecurityCamera.RANGE, c);
        }
        for (Guard guard : game.guards) {
            Color c;
            switch (guard.state) {
                case ALERTED: c = Palette.alpha(Palette.RED, 110); break;
                case SUSPICIOUS: c = Palette.alpha(Palette.AMBER, 92); break;
                default: c = new Color(255, 232, 160, 50); break;
            }
            drawCone(g, game.level, guard.x, guard.y, guard.facing, Guard.FOV, Guard.RANGE, c);
        }

        for (SecurityCamera cam : game.cameras) Sprites.camera(g, cam, time);
        for (Guard guard : game.guards) Sprites.guard(g, guard, time);
        if (playing) {
            Sprites.player(g, game.player,
                    !game.isDone(Station.Kind.CASH),
                    !game.isDone(Station.Kind.GOLD),
                    !game.isDone(Station.Kind.JEWELLERY));
        }

        for (Game.Particle p : game.particles) {
            double k = 1 - p.age / p.life;
            Color c = new Color(p.rgb);
            g.setColor(Palette.alpha(c, (int) (255 * k)));
            g.fill(new Ellipse2D.Double(p.x - p.size / 2, p.y - p.size / 2, p.size, p.size));
        }

        if (game.screen == Game.Screen.PLAYING) drawPrompt(g, game);

        if (vignette == null) vignette = buildVignette();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(vignette, 0, 0, Level.WIDTH, Level.HEIGHT, null);

        if (playing && game.detection > 1) drawDangerEdges(g, game);
        if (game.screen == Game.Screen.PLAYING && game.seenNow) drawDetectedBanner(g, game);
        g.setTransform(before);
    }

    /** The static map is painted once into an image at screen resolution, then reused every frame. */
    private void drawMapCache(Graphics2D g, Level level) {
        AffineTransform at = g.getTransform();
        double deviceScale = Math.sqrt(Math.abs(at.getDeterminant()));
        if (mapCache == null || Math.abs(deviceScale - mapCacheScale) > 1e-6) {
            int w = Math.max(1, (int) Math.ceil(Level.WIDTH * deviceScale));
            int h = Math.max(1, (int) Math.ceil(Level.HEIGHT * deviceScale));
            mapCache = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D mg = mapCache.createGraphics();
            mg.scale(w / (double) Level.WIDTH, h / (double) Level.HEIGHT);
            Sprites.paintStaticMap(mg, level);
            mg.dispose();
            mapCacheScale = deviceScale;
        }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(mapCache, 0, 0, Level.WIDTH, Level.HEIGHT, null);
    }

    /** A vision cone, clipped against walls so it shows exactly what can be seen. */
    private void drawCone(Graphics2D g, Level level, double x, double y, double angle,
            double fov, double range, Color color) {
        int rays = 30;
        Path2D shape = new Path2D.Double();
        shape.moveTo(x, y);
        for (int i = 0; i <= rays; i++) {
            double a = angle - fov / 2 + fov * i / rays;
            double d = level.castRay(x, y, a, range);
            shape.lineTo(x + Math.cos(a) * d, y + Math.sin(a) * d);
        }
        shape.closePath();
        Color faint = Palette.alpha(color, color.getAlpha() / 3);
        g.setPaint(new RadialGradientPaint((float) x, (float) y, (float) range,
                new float[] {0f, 1f}, new Color[] {color, faint}));
        g.fill(shape);
        g.setColor(Palette.alpha(color, Math.min(255, color.getAlpha() + 40)));
        g.setStroke(new BasicStroke(1f));
        g.draw(shape);
    }

    private BufferedImage buildVignette() {
        int w = 240, h = 120;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double nx = (x + 0.5) / w * 2 - 1, ny = (y + 0.5) / h * 2 - 1;
                double d = Math.sqrt(nx * nx * 0.8 + ny * ny);
                double k = Math.max(0, Math.min(1, (d - 0.55) / 0.75));
                int alpha = (int) (Math.pow(k, 1.6) * 170);
                img.setRGB(x, y, (alpha << 24) | 0x05070d);
            }
        }
        return img;
    }

    /** Red glow creeping in from the screen edges as the detection meter fills. */
    private void drawDangerEdges(Graphics2D g, Game game) {
        double level = game.detection / 100.0;
        double pulse = game.seenNow ? 0.75 + 0.25 * Math.sin(game.worldTime * 12) : 0.6;
        int a = (int) (150 * level * pulse);
        if (a <= 2) return;
        Color hot = Palette.alpha(Palette.RED, a), clear = Palette.alpha(Palette.RED, 0);
        int depth = 70, w = Level.WIDTH, h = Level.HEIGHT;
        g.setPaint(new GradientPaint(0, 0, hot, 0, depth, clear));
        g.fillRect(0, 0, w, depth);
        g.setPaint(new GradientPaint(0, h, hot, 0, h - depth, clear));
        g.fillRect(0, h - depth, w, depth);
        g.setPaint(new GradientPaint(0, 0, hot, depth, 0, clear));
        g.fillRect(0, 0, depth, h);
        g.setPaint(new GradientPaint(w, 0, hot, w - depth, 0, clear));
        g.fillRect(w - depth, 0, depth, h);
    }

    private void drawDetectedBanner(Graphics2D g, Game game) {
        String text = game.seenByCamera ? "CAMERA SEES YOU - GET OUT OF ITS CONE!" : "SPOTTED - BREAK LINE OF SIGHT!";
        boolean flash = Math.sin(game.worldTime * 14) > 0;
        int tw = PixelFont.width(text, 2);
        int bw = tw + 36, bx = (W - bw) / 2, by = Level.HEIGHT - 38;
        g.setColor(flash ? Palette.RED : new Color(0x8f2a2e));
        g.fillRoundRect(bx, by, bw, 26, 8, 8);
        g.setColor(Palette.OUTLINE);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(bx, by, bw, 26, 8, 8);
        PixelFont.draw(g, text, bx + 18, by + 6, 2, Color.WHITE);
    }

    /** The "press E" bubble that appears above the player near something usable. */
    private void drawPrompt(Graphics2D g, Game game) {
        Station s = game.nearby;
        if (s == null) return;
        String before = "", key = "E", after;
        Color color = Palette.TEXT;
        if (s.kind == Station.Kind.VAULT) {
            if (s.done) return;
            if (!game.allReturned()) {
                int left = 3 - game.returnedCount();
                key = null;
                after = "LOCKED - " + left + (left == 1 ? " ITEM" : " ITEMS") + " STILL TO RETURN";
                color = Palette.RED;
            } else {
                before = "HOLD";
                after = "TO SECURE VAULT";
                color = Palette.GOLD;
            }
        } else {
            if (s.done) return;
            after = "RETURN " + s.kind.label;
            color = Sprites.kindColor(s.kind);
        }

        int beforeW = before.isEmpty() ? 0 : PixelFont.width(before, 2) + 8;
        int keyW = key == null ? 0 : 20 + 8;
        int w = 10 + beforeW + keyW + PixelFont.width(after, 2) + 10;
        int h = 26;
        int x = (int) Math.round(game.player.x - w / 2.0);
        int y = (int) Math.round(game.player.y - 44 - Math.sin(game.worldTime * 5) * 1.5);
        if (y < 4) y = (int) Math.round(game.player.y + 20);
        x = Math.max(4, Math.min(W - w - 4, x));

        g.setColor(new Color(8, 11, 20, 230));
        g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(Palette.alpha(color, 220));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(x, y, w, h, 8, 8);
        int cx = x + 10;
        if (!before.isEmpty()) {
            PixelFont.draw(g, before, cx, y + 6, 2, color);
            cx += beforeW;
        }
        if (key != null) {
            keycap(g, key, cx, y + 3);
            cx += keyW;
        }
        PixelFont.draw(g, after, cx, y + 6, 2, color);
    }

    // =================================================================== HUD

    private void drawTopBar(Graphics2D g, Game game) {
        g.setColor(Palette.HUD_BG);
        g.fillRect(0, 0, W, TOP);
        g.setColor(Palette.HUD_LINE);
        g.fillRect(0, TOP - 2, W, 2);

        // Current objective.
        PixelFont.draw(g, "CURRENT OBJECTIVE", 16, 10, 1, Palette.MUTED);
        Color objectiveColor = game.vault.done ? Palette.GREEN : game.allReturned() ? Palette.GOLD : Palette.TEXT;
        PixelFont.draw(g, game.objectiveText(), 16, 26, 2, objectiveColor);

        // Countdown.
        int seconds = (int) Math.ceil(Math.max(0, game.timeLeft));
        String clock = twoDigits(seconds / 60) + ":" + twoDigits(seconds % 60);
        boolean low = game.timeLeft <= 30;
        boolean blink = low && Math.sin(game.worldTime * 10) > 0;
        g.setColor(Palette.HUD_PANEL);
        g.fillRoundRect(408, 6, 144, 42, 8, 8);
        g.setColor(low ? Palette.RED : Palette.HUD_LINE);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(408, 6, 144, 42, 8, 8);
        PixelFont.drawCentered(g, clock, 480, 13, 4, low ? (blink ? Palette.RED : new Color(0xff8a8e)) : Palette.TEXT);

        // Detection meter.
        double det = game.detection;
        Color meter = det < 40 ? Palette.GREEN : det < 70 ? Palette.AMBER : Palette.RED;
        PixelFont.draw(g, "DETECTION", 660, 10, 1, game.seenNow ? meter : Palette.MUTED);
        int shake = det > 70 && game.seenNow ? (int) Math.round(Math.sin(game.worldTime * 50) * 1.5) : 0;
        int bx = 660 + shake, by = 24, bw = 224, bh = 18;
        g.setColor(Palette.HUD_PANEL);
        g.fillRect(bx, by, bw, bh);
        int fillW = (int) Math.round((bw - 4) * det / 100.0);
        if (fillW > 0) {
            g.setColor(meter);
            g.fillRect(bx + 2, by + 2, fillW, bh - 4);
            g.setColor(new Color(255, 255, 255, 60));
            g.fillRect(bx + 2, by + 2, fillW, 3);
        }
        g.setColor(Palette.HUD_BG);
        for (int i = 1; i < 4; i++) g.fillRect(bx + bw * i / 4, by + 2, 1, bh - 4);
        g.setColor(game.seenNow ? meter : Palette.HUD_LINE);
        g.setStroke(new BasicStroke(2f));
        g.drawRect(bx, by, bw, bh);
        PixelFont.drawRight(g, (int) Math.round(det) + "%", 944, 26, 2, det > 0 ? meter : Palette.MUTED);
    }

    private void drawBottomBar(Graphics2D g, Game game) {
        g.setColor(Palette.HUD_BG);
        g.fillRect(0, BOTTOM, W, H - BOTTOM);
        g.setColor(Palette.HUD_LINE);
        g.fillRect(0, BOTTOM, W, 2);

        Station.Kind[] kinds = {Station.Kind.CASH, Station.Kind.GOLD, Station.Kind.JEWELLERY, Station.Kind.VAULT};
        for (int i = 0; i < kinds.length; i++) {
            Station.Kind kind = kinds[i];
            int x = 10 + i * 196, y = BOTTOM + 9, w = 190, h = 48;
            boolean done = game.isDone(kind);
            String status;
            Color statusColor;
            if (kind == Station.Kind.VAULT) {
                status = done ? "SECURED ~" : game.allReturned() ? "UNLOCKED" : "LOCKED";
                statusColor = done ? Palette.GREEN : game.allReturned() ? Palette.GOLD : Palette.RED;
            } else {
                status = done ? "RETURNED ~" : "NOT RETURNED";
                statusColor = done ? Palette.GREEN : Palette.AMBER;
            }
            g.setColor(Palette.HUD_PANEL);
            g.fillRoundRect(x, y, w, h, 8, 8);
            g.setColor(done ? Palette.alpha(Palette.GREEN, 170) : Palette.HUD_LINE);
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(x, y, w, h, 8, 8);
            Sprites.itemIcon(g, kind, x + 8, y + 12, done && kind != Station.Kind.VAULT);
            PixelFont.draw(g, kind.label, x + 40, y + 6, 2, Palette.TEXT);
            PixelFont.draw(g, status, x + 40, y + 27, 2, statusColor);
        }

        int hx = 800;
        PixelFont.draw(g, "WASD / ARROWS  MOVE", hx, BOTTOM + 12, 1, Palette.MUTED);
        PixelFont.draw(g, "E  INTERACT", hx, BOTTOM + 24, 1, Palette.MUTED);
        PixelFont.draw(g, "P  PAUSE    M  MUTE", hx, BOTTOM + 36, 1, Palette.MUTED);
        if (game.sound != null && game.sound.muted()) {
            PixelFont.draw(g, "SOUND OFF", hx, BOTTOM + 48, 1, Palette.AMBER);
        }
    }

    private void drawToasts(Graphics2D g, Game game) {
        int y = TOP + 12;
        for (Game.Toast t : game.toasts) {
            double fade = Math.min(1, Math.min(t.age / 0.2, (t.life - t.age) / 0.4));
            if (fade <= 0) continue;
            Color accent;
            switch (t.kind) {
                case Game.Toast.GOOD: accent = Palette.GREEN; break;
                case Game.Toast.WARN: accent = Palette.AMBER; break;
                case Game.Toast.BAD: accent = Palette.RED; break;
                default: accent = Palette.TEXT; break;
            }
            int tw = PixelFont.width(t.text, 2);
            int w = tw + 34, x = (W - w) / 2;
            int slide = (int) Math.round((1 - Math.min(1, t.age / 0.2)) * -8);
            g.setColor(new Color(8, 11, 20, (int) (225 * fade)));
            g.fillRoundRect(x, y + slide, w, 26, 8, 8);
            g.setColor(Palette.alpha(accent, (int) (255 * fade)));
            g.fillRoundRect(x, y + slide, 6, 26, 6, 6);
            PixelFont.draw(g, t.text, x + 20, y + slide + 6, 2, Palette.alpha(accent, (int) (255 * fade)));
            y += 32;
        }
    }

    // =============================================================== screens

    /** Props used only to animate the main menu. */
    private final Player menuThief = new Player(0, 0);
    private final SecurityCamera menuCamera = new SecurityCamera(0, 0, 5, 45, 0);

    private void drawMenu(Graphics2D g, Game game, double mx, double my) {
        double t = game.worldTime, st = game.screenTime;

        // Let the bank show through in the middle; darker at the top and bottom where the text sits.
        int mid = H / 2;
        g.setPaint(new GradientPaint(0, 0, new Color(6, 8, 15, 236), 0, mid, new Color(6, 8, 15, 160)));
        g.fillRect(0, 0, W, mid);
        g.setPaint(new GradientPaint(0, mid, new Color(6, 8, 15, 160), 0, H, new Color(6, 8, 15, 244)));
        g.fillRect(0, mid, W, H - mid);

        // Two security searchlights sweeping across the screen.
        searchlight(g, -60, -60, Math.toRadians(42 + 24 * Math.sin(t * 0.55)));
        searchlight(g, W + 60, -60, Math.toRadians(138 + 24 * Math.sin(t * 0.55 + 2.2)));

        drawMenuTitle(g, t, st);

        // Subtitle with gold rules either side.
        String sub = "THE PERFECT HEIST IS PUTTING EVERYTHING BACK.";
        int sw = PixelFont.width(sub, 2);
        PixelFont.drawCenteredShadowed(g, sub, W / 2, 124, 2, new Color(0xc9d1e3));
        g.setColor(Palette.GOLD_DARK);
        g.fillRect(W / 2 - sw / 2 - 70, 130, 54, 2);
        g.fillRect(W / 2 + sw / 2 + 16, 130, 54, 2);

        // Pulsing play button that lifts on hover.
        double pulse = 0.5 + 0.5 * Math.sin(t * 3);
        boolean hover = inside(mx, my, Game.BTN_PLAY);
        int[] r = Game.BTN_PLAY;
        int grow = (int) Math.round(5 + 4 * pulse);
        g.setColor(Palette.alpha(Palette.GOLD, (int) (35 + 45 * pulse)));
        g.fillRoundRect(r[0] - grow, r[1] - grow, r[2] + grow * 2, r[3] + grow * 2, 18, 18);
        int[] drawn = hover ? new int[] {r[0], r[1] - 2, r[2], r[3]} : r;
        button(g, drawn, "PLAY", 3, true, hover);
        PixelFont.drawCentered(g, "PRESS ENTER", W / 2, 230, 1, Palette.alpha(Palette.MUTED, (int) (110 + 130 * pulse)));

        // Three quick steps instead of a wall of text.
        String[][] cards = {
            {"RETURN THE GOODS", "SNEAK CASH, GOLD AND JEWELLERY", "BACK TO THEIR DEPARTMENTS."},
            {"STAY UNSEEN", "GUARDS AND CAMERAS FILL THE", "DETECTION METER. AT 100% YOU'RE OUT."},
            {"SECURE THE VAULT", "WITH EVERYTHING BACK, HOLD E", "AT THE DOOR BEFORE 3:00 IS UP."},
        };
        Color[] accents = {Palette.GOLD, Palette.RED, Palette.GREEN};
        int cw = 268, ch = 166, gap = 18;
        int cx0 = (W - (cw * 3 + gap * 2)) / 2;
        for (int i = 0; i < 3; i++) {
            double k = ease(clamp01((st - 0.25 - i * 0.12) / 0.5));
            if (k <= 0) continue;
            int x = cx0 + i * (cw + gap);
            int y = 256 + (int) Math.round((1 - k) * 24);
            Composite oc = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) k));
            panel(g, x, y, cw, ch, accents[i]);
            g.setColor(accents[i]);
            g.fill(new Ellipse2D.Double(x + 12, y + 12, 22, 22));
            PixelFont.drawCentered(g, String.valueOf(i + 1), x + 24, y + 16, 2, Palette.OUTLINE);
            drawCardVisual(g, i, x, y, cw, t);
            PixelFont.drawCentered(g, cards[i][0], x + cw / 2, y + 104, 2, accents[i]);
            PixelFont.drawCentered(g, cards[i][1], x + cw / 2, y + 128, 1, Palette.TEXT);
            PixelFont.drawCentered(g, cards[i][2], x + cw / 2, y + 142, 1, Palette.TEXT);
            g.setComposite(oc);
        }

        drawControlsStrip(g, 444);

        // The burglar sneaking along the bottom with a full sack.
        double span = W + 80;
        menuThief.x = -40 + (t * 46) % span;
        menuThief.y = 522;
        menuThief.facing = 0;
        menuThief.moving = true;
        menuThief.walkPhase = t * 9;
        AffineTransform beforeThief = g.getTransform();
        g.translate(menuThief.x, menuThief.y);
        g.scale(1.7, 1.7);
        g.translate(-menuThief.x, -menuThief.y);
        Sprites.player(g, menuThief, true, true, true);
        g.setTransform(beforeThief);

        PixelFont.drawCentered(g, "JAM THEME: GENRE X BUT WITHOUT Y  -  A HEIST WITHOUT STEALING", W / 2, 580, 1, Palette.DIM);

        // Subtle CRT scanlines, then a fade in from black.
        g.setColor(new Color(0, 0, 0, 16));
        for (int y = 0; y < H; y += 3) g.fillRect(0, y, W, 1);
        if (st < 0.6) {
            g.setColor(new Color(0, 0, 0, (int) (255 * (1 - st / 0.6))));
            g.fillRect(0, 0, W, H);
        }
    }

    private static void searchlight(Graphics2D g, double x, double y, double angle) {
        double len = 780, half = Math.toRadians(9);
        Path2D p = new Path2D.Double();
        p.moveTo(x, y);
        p.lineTo(x + Math.cos(angle - half) * len, y + Math.sin(angle - half) * len);
        p.lineTo(x + Math.cos(angle + half) * len, y + Math.sin(angle + half) * len);
        p.closePath();
        g.setPaint(new RadialGradientPaint((float) x, (float) y, (float) len, new float[] {0f, 0.6f, 1f},
                new Color[] {new Color(255, 236, 190, 64), new Color(255, 236, 190, 26), new Color(255, 236, 190, 0)}));
        g.fill(p);
    }

    /** Big outlined title. Every few seconds the word REVERSE flips over, mirror-style. */
    private void drawMenuTitle(Graphics2D g, double t, double st) {
        int s = 8;
        String full = "THE REVERSE HEIST";
        int x0 = (W - PixelFont.width(full, s)) / 2;
        int y0 = 40 + (int) Math.round((1 - ease(clamp01(st / 0.7))) * -40);
        int rx = x0 + 4 * 6 * s, rw = PixelFont.width("REVERSE", s);

        // Soft gold glow behind REVERSE.
        AffineTransform before = g.getTransform();
        g.translate(rx + rw / 2.0, y0 + 28);
        g.scale(1, 0.32);
        g.setPaint(new RadialGradientPaint(0f, 0f, 300f, new float[] {0f, 1f},
                new Color[] {new Color(242, 193, 78, (int) (60 + 25 * Math.sin(t * 2))), new Color(242, 193, 78, 0)}));
        g.fillRect(-300, -300, 600, 600);
        g.setTransform(before);

        outlinedText(g, "THE", x0, y0, s, Palette.TEXT, Color.WHITE);
        outlinedText(g, "HEIST", x0 + 12 * 6 * s, y0, s, Palette.TEXT, Color.WHITE);

        double cyc = t % 8;
        double sx = 1;
        if (cyc > 6.8 && cyc < 7.6) {
            sx = Math.cos((cyc - 6.8) / 0.8 * Math.PI * 2);
            if (Math.abs(sx) < 0.05) sx = sx < 0 ? -0.05 : 0.05;
        }
        g.translate(rx + rw / 2.0, 0);
        g.scale(sx, 1);
        g.translate(-(rx + rw / 2.0), 0);
        String word = "REVERSE";
        for (int i = 0; i < word.length(); i++) {
            int ly = y0 + (int) Math.round(Math.sin(t * 2.6 + i * 0.55) * 3);
            outlinedText(g, word.substring(i, i + 1), rx + i * 6 * s, ly, s, Palette.GOLD, Palette.GOLD_LIGHT);
        }
        g.setTransform(before);
    }

    /** Pixel text with a thick dark outline, a drop shadow and a lighter top half. */
    private static void outlinedText(Graphics2D g, String text, int x, int y, int s, Color base, Color light) {
        int o = s / 2;
        PixelFont.draw(g, text, x + 3, y + s + 2, s, new Color(0, 0, 0, 190));
        for (int dx = -o; dx <= o; dx += o) {
            for (int dy = -o; dy <= o; dy += o) {
                if (dx != 0 || dy != 0) PixelFont.draw(g, text, x + dx, y + dy, s, Palette.OUTLINE);
            }
        }
        PixelFont.draw(g, text, x, y, s, base);
        Shape clip = g.getClip();
        g.clipRect(x - 4, y, PixelFont.width(text, s) + 8, 3 * s);
        PixelFont.draw(g, text, x, y, s, light);
        g.setClip(clip);
    }

    /** The little animated picture at the top of each step card. */
    private void drawCardVisual(Graphics2D g, int i, int x, int y, int cw, double t) {
        int cx = x + cw / 2, cy = y + 56;
        AffineTransform old = g.getTransform();
        Shape oldClip = g.getClip();
        if (i == 0) {
            Station.Kind[] kinds = {Station.Kind.CASH, Station.Kind.GOLD, Station.Kind.JEWELLERY};
            for (int k = 0; k < 3; k++) {
                double bob = Math.sin(t * 2.4 + k * 0.9) * 3;
                g.setTransform(old);
                g.translate(cx - 84 + k * 60, cy - 24 + bob);
                g.scale(2, 2);
                Sprites.itemIcon(g, kinds[k], 0, 0, false);
            }
        } else if (i == 1) {
            double level = 0.5 + 0.5 * Math.sin(t * 1.1);
            double ox = x + 62, oy = y + 34;
            menuCamera.angle = Math.toRadians(25 + 20 * Math.sin(t * 1.3));
            menuCamera.seesPlayer = level > 0.75;
            g.clipRect(x + 2, y + 2, cw - 4, 78);
            double len = 170, half = SecurityCamera.FOV / 2;
            Path2D cone = new Path2D.Double();
            cone.moveTo(ox, oy);
            cone.lineTo(ox + Math.cos(menuCamera.angle - half) * len, oy + Math.sin(menuCamera.angle - half) * len);
            cone.lineTo(ox + Math.cos(menuCamera.angle + half) * len, oy + Math.sin(menuCamera.angle + half) * len);
            cone.closePath();
            g.setPaint(new RadialGradientPaint((float) ox, (float) oy, (float) len, new float[] {0f, 1f},
                    new Color[] {Palette.alpha(Palette.RED, 120), Palette.alpha(Palette.RED, 10)}));
            g.fill(cone);
            g.setClip(oldClip);
            g.translate(ox, oy);
            g.scale(1.8, 1.8);
            Sprites.camera(g, menuCamera, t);
            g.setTransform(old);
            int bw = 150, bx = cx - bw / 2 + 20, by = y + 84;
            Color meter = level < 0.4 ? Palette.GREEN : level < 0.7 ? Palette.AMBER : Palette.RED;
            g.setColor(Palette.HUD_BG);
            g.fillRect(bx, by, bw, 8);
            g.setColor(meter);
            g.fillRect(bx + 1, by + 1, (int) Math.round((bw - 2) * level), 6);
            PixelFont.drawRight(g, (int) Math.round(level * 100) + "%", bx - 8, by, 1, meter);
        } else {
            double p = Math.min(1, (t * 0.45) % 1.3);
            g.setColor(Palette.HUD_LINE);
            g.setStroke(new BasicStroke(4f));
            g.draw(new Ellipse2D.Double(cx - 36, cy - 36, 72, 72));
            g.setColor(p >= 1 ? Palette.GREEN : Palette.alpha(Palette.GREEN, 210));
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Arc2D.Double(cx - 36, cy - 36, 72, 72, 90, -360 * p, Arc2D.OPEN));
            if (p >= 1) {
                g.setColor(Palette.alpha(Palette.GREEN, 70));
                g.fill(new Ellipse2D.Double(cx - 40, cy - 40, 80, 80));
            }
            g.translate(cx, cy);
            g.rotate(p * Math.PI * 2);
            g.scale(2.3, 2.3);
            Sprites.itemIcon(g, Station.Kind.VAULT, -12, -12, false);
        }
        g.setTransform(old);
        g.setClip(oldClip);
    }

    private static void drawControlsStrip(Graphics2D g, int y) {
        String[][] keys = {{"W", "A", "S", "D"}, {"E"}, {"P"}, {"M"}};
        String[] labels = {"MOVE (OR ARROWS)", "INTERACT", "PAUSE", "MUTE"};
        int groupGap = 30;
        int total = 0;
        for (int i = 0; i < keys.length; i++) {
            for (String k : keys[i]) total += keyWidth(k) + 3;
            total += 5 + PixelFont.width(labels[i], 2);
            if (i < keys.length - 1) total += groupGap;
        }
        int x = (W - total) / 2;
        g.setColor(new Color(13, 17, 29, 220));
        g.fillRoundRect(x - 18, y, total + 36, 36, 12, 12);
        g.setColor(Palette.HUD_LINE);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(x - 18, y, total + 36, 36, 12, 12);
        for (int i = 0; i < keys.length; i++) {
            for (String k : keys[i]) x = keycap(g, k, x, y + 7) + 3;
            x += 5;
            PixelFont.draw(g, labels[i], x, y + 11, 2, Palette.TEXT);
            x += PixelFont.width(labels[i], 2) + groupGap;
        }
    }

    private static int keyWidth(String label) {
        return Math.max(20, PixelFont.width(label, 2) + 10);
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private void drawPause(Graphics2D g) {
        g.setColor(new Color(6, 8, 15, 190));
        g.fillRect(0, TOP, W, Level.HEIGHT);
        panel(g, 300, 210, 360, 170, Palette.TEXT);
        PixelFont.drawCentered(g, "PAUSED", W / 2, 236, 5, Palette.TEXT);
        PixelFont.drawCentered(g, "P / ESC - RESUME", W / 2, 296, 2, Palette.MUTED);
        PixelFont.drawCentered(g, "R - RESTART", W / 2, 320, 2, Palette.MUTED);
        PixelFont.drawCentered(g, "Q - MAIN MENU", W / 2, 344, 2, Palette.MUTED);
    }

    private void drawVictory(Graphics2D g, Game game, double mx, double my) {
        double in = Math.min(1, game.screenTime / 0.45);
        g.setColor(new Color(6, 8, 15, (int) (185 * in)));
        g.fillRect(0, TOP, W, Level.HEIGHT);
        if (game.screenTime < 0.35) {
            g.setColor(Palette.alpha(Palette.GREEN, (int) (90 * (1 - game.screenTime / 0.35))));
            g.fillRect(0, TOP, W, Level.HEIGHT);
        }
        int drop = (int) Math.round((1 - ease(in)) * -30);
        int px = 210, py = 108 + drop, pw = 540, ph = 372;
        panel(g, px, py, pw, ph, Palette.GREEN);
        PixelFont.drawCenteredShadowed(g, "HEIST REVERSED!", W / 2, py + 28, 5, Palette.GREEN);
        PixelFont.drawCentered(g, "EVERY LAST ITEM IS BACK WHERE IT BELONGS.", W / 2, py + 82, 2, Palette.TEXT);
        String quip = game.timesSpotted == 0
                ? "NOBODY WILL EVER KNOW YOU WERE HERE."
                : "SECURITY HAS A FEW QUESTIONS, THOUGH.";
        PixelFont.drawCentered(g, quip, W / 2, py + 104, 2, Palette.MUTED);

        int rowY = py + 142;
        statRow(g, "COMPLETION TIME", formatTime(game.completionTime), rowY, Palette.TEXT);
        statRow(g, "TIMES SPOTTED", String.valueOf(game.timesSpotted), rowY + 28,
                game.timesSpotted == 0 ? Palette.GREEN : Palette.AMBER);
        statRow(g, "PEAK DETECTION", (int) Math.round(game.peakDetection) + "%", rowY + 56,
                game.peakDetection < 40 ? Palette.GREEN : game.peakDetection < 70 ? Palette.AMBER : Palette.RED);
        String rating = game.timesSpotted == 0 ? "GHOST"
                : game.peakDetection < 50 ? "PROFESSIONAL"
                : game.peakDetection < 80 ? "CLOSE CALL" : "BY THE SKIN OF YOUR TEETH";
        statRow(g, "RATING", rating, rowY + 84, Palette.GOLD);

        endButtons(g, game, "PLAY AGAIN", mx, my, drop);
    }

    private void drawGameOver(Graphics2D g, Game game, double mx, double my) {
        boolean caught = game.fail == Game.Fail.CAUGHT;
        double in = Math.min(1, game.screenTime / 0.45);
        g.setColor(new Color(6, 8, 15, (int) (195 * in)));
        g.fillRect(0, TOP, W, Level.HEIGHT);
        if (caught && game.screenTime < 0.4) {
            g.setColor(Palette.alpha(Palette.RED, (int) (120 * (1 - game.screenTime / 0.4))));
            g.fillRect(0, TOP, W, Level.HEIGHT);
        }
        int drop = (int) Math.round((1 - ease(in)) * -30);
        int px = 210, py = 108 + drop, pw = 540, ph = 372;
        panel(g, px, py, pw, ph, Palette.RED);
        PixelFont.drawCenteredShadowed(g, caught ? "BUSTED!" : "TIME'S UP!", W / 2, py + 26, 6, Palette.RED);
        if (caught) {
            PixelFont.drawCentered(g, "SECURITY CAUGHT YOU RED-HANDED...", W / 2, py + 88, 2, Palette.TEXT);
            PixelFont.drawCentered(g, "...PUTTING EVERYTHING BACK.", W / 2, py + 110, 2, Palette.MUTED);
        } else {
            PixelFont.drawCentered(g, "THE BANK OPENED BEFORE YOU COULD", W / 2, py + 88, 2, Palette.TEXT);
            PixelFont.drawCentered(g, "FINISH PUTTING EVERYTHING BACK.", W / 2, py + 110, 2, Palette.MUTED);
        }
        int rowY = py + 156;
        statRow(g, "GOODS RETURNED", game.returnedCount() + " / 3", rowY,
                game.returnedCount() == 3 ? Palette.GREEN : Palette.AMBER);
        statRow(g, "TIME ON THE CLOCK", formatTime(game.timeLeft), rowY + 28, Palette.TEXT);
        statRow(g, "TIMES SPOTTED", String.valueOf(game.timesSpotted), rowY + 56, Palette.TEXT);
        String tip = caught
                ? "TIP: WALLS AND SHELVES BLOCK THEIR VIEW."
                : "TIP: PLAN A LOOP AND KEEP MOVING.";
        PixelFont.drawCentered(g, tip, W / 2, rowY + 96, 2, Palette.GOLD);

        endButtons(g, game, "TRY AGAIN", mx, my, drop);
    }

    private void endButtons(Graphics2D g, Game game, String primary, double mx, double my, int drop) {
        if (drop != 0) return; // buttons appear once the panel has settled, where they can be clicked
        button(g, Game.BTN_PRIMARY, primary, 2, true, inside(mx, my, Game.BTN_PRIMARY));
        button(g, Game.BTN_SECONDARY, "MAIN MENU", 2, false, inside(mx, my, Game.BTN_SECONDARY));
        PixelFont.drawCentered(g, "ENTER - " + primary + "     ESC - MAIN MENU", W / 2, 466, 1, Palette.MUTED);
    }

    // =============================================================== helpers

    private static void statRow(Graphics2D g, String label, String value, int y, Color valueColor) {
        PixelFont.draw(g, label, 280, y, 2, Palette.MUTED);
        PixelFont.drawRight(g, value, 680, y, 2, valueColor);
        g.setColor(Palette.HUD_LINE);
        g.fillRect(280, y + 19, 400, 1);
    }

    private static void panel(Graphics2D g, int x, int y, int w, int h, Color accent) {
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRoundRect(x + 5, y + 7, w, h, 14, 14);
        g.setColor(new Color(13, 17, 29, 244));
        g.fillRoundRect(x, y, w, h, 14, 14);
        g.setColor(Palette.HUD_LINE);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(x, y, w, h, 14, 14);
        g.setColor(accent);
        g.fillRoundRect(x + 24, y - 2, w - 48, 4, 4, 4);
    }

    private static void button(Graphics2D g, int[] r, String label, int textScale, boolean primary, boolean hover) {
        int x = r[0], y = r[1], w = r[2], h = r[3];
        g.setColor(new Color(0, 0, 0, 130));
        g.fillRoundRect(x + 3, y + 4, w, h, 10, 10);
        if (primary) {
            g.setColor(hover ? Palette.GOLD_LIGHT : Palette.GOLD);
            g.fillRoundRect(x, y, w, h, 10, 10);
            g.setColor(Palette.GOLD_DARK);
            g.fillRoundRect(x, y + h - 7, w, 7, 8, 8);
        } else {
            g.setColor(hover ? new Color(0x2c3654) : Palette.HUD_PANEL);
            g.fillRoundRect(x, y, w, h, 10, 10);
            g.setColor(hover ? Palette.TEXT : Palette.MUTED);
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(x, y, w, h, 10, 10);
        }
        int ty = y + (h - 7 * textScale) / 2 - (primary ? 2 : 0);
        PixelFont.drawCentered(g, label, x + w / 2, ty, textScale, primary ? Palette.OUTLINE : Palette.TEXT);
    }

    /** Draws a keyboard key. Returns the x coordinate just past it. */
    private static int keycap(Graphics2D g, String label, int x, int y) {
        int w = Math.max(20, PixelFont.width(label, 2) + 10);
        g.setColor(new Color(0x10141f));
        g.fillRoundRect(x, y + 2, w, 20, 6, 6);
        g.setColor(new Color(0xdfe5f0));
        g.fillRoundRect(x, y, w, 19, 6, 6);
        PixelFont.drawCentered(g, label, x + w / 2 + 1, y + 3, 2, Palette.OUTLINE);
        return x + w;
    }

    private static boolean inside(double mx, double my, int[] r) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    private static double ease(double t) {
        return 1 - Math.pow(1 - t, 3);
    }

    private static String twoDigits(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    static String formatTime(double seconds) {
        int s = (int) Math.floor(Math.max(0, seconds));
        return twoDigits(s / 60) + ":" + twoDigits(s % 60);
    }
}
