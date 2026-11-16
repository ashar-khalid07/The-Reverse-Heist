package heist;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * All game rules and state. This class knows nothing about windows, drawing
 * or sound devices: the window feeds it an {@link Input} and a time step,
 * the {@link Renderer} reads its fields, and sounds go out through {@link SoundSink}.
 */
final class Game {
    enum Screen { MENU, PLAYING, PAUSED, VICTORY, GAME_OVER }

    enum Fail { CAUGHT, TIME_UP }

    enum Sound { STEP, DEPOSIT, DENY, SPOTTED, CAMERA, ALARM, VICTORY, FAIL, CLICK, VAULT_TICK, TIME_TICK, START }

    interface SoundSink {
        void play(Sound sound);

        void toggleMute();

        boolean muted();
    }

    /** A short message shown at the top of the screen. */
    static final class Toast {
        static final int INFO = 0, GOOD = 1, WARN = 2, BAD = 3;
        final String text;
        final int kind;
        final double life;
        double age;

        Toast(String text, int kind, double life) {
            this.text = text;
            this.kind = kind;
            this.life = life;
        }
    }

    /** A purely decorative spark. */
    static final class Particle {
        double x, y, vx, vy, age, life, size;
        int rgb;
    }

    /** Fixed simulation step: the game always advances in slices of this size. */
    static final double STEP = 1.0 / 120.0;
    static final double TIME_LIMIT = 180.0;
    static final double VAULT_HOLD = 1.5;

    // Tuning for the detection meter (percent per second).
    private static final double GUARD_GAIN_FAR = 36;
    private static final double GUARD_GAIN_NEAR = 75;
    private static final double CAMERA_GAIN = 40;
    private static final double DECAY = 22;
    private static final double DECAY_DELAY = 0.7;

    // Clickable buttons, in game coordinates: {x, y, width, height}.
    static final int[] BTN_PLAY = {370, 158, 220, 58};
    static final int[] BTN_PRIMARY = {282, 412, 194, 44};
    static final int[] BTN_SECONDARY = {484, 412, 194, 44};

    final Level level = new Level();
    Player player;
    final List<Guard> guards = new ArrayList<Guard>();
    final List<SecurityCamera> cameras = new ArrayList<SecurityCamera>();
    final List<Station> stations = new ArrayList<Station>();
    Station vault;

    Screen screen = Screen.MENU;
    Fail fail;
    /** Seconds since the current screen appeared (for UI animation and input delay). */
    double screenTime;
    /** Seconds of simulated time, used for animation. */
    double worldTime;

    double timeLeft = TIME_LIMIT;
    double detection;
    double peakDetection;
    int timesSpotted;
    boolean seenNow;
    boolean seenByCamera;
    double completionTime;

    /** The interactable the player is currently close enough to use, or null. */
    Station nearby;
    double vaultProgress;

    final List<Toast> toasts = new ArrayList<Toast>();
    final List<Particle> particles = new ArrayList<Particle>();

    SoundSink sound;

    private double unseenTimer;
    private double denyCooldown;
    private double cameraBeepTimer;
    private boolean warnedThirty;
    private int lastTickSecond;
    private final Random sparkle = new Random(7);

    Game() {
        buildWorld();
    }

    // ------------------------------------------------------------------ setup

    private void buildWorld() {
        player = new Player(Level.START_X, Level.START_Y);

        stations.clear();
        stations.add(new Station(Station.Kind.CASH, 1, 1, 1, 30));
        stations.add(new Station(Station.Kind.GOLD, 38, 1, 1, 30));
        stations.add(new Station(Station.Kind.JEWELLERY, 38, 18, 1, 30));
        vault = new Station(Station.Kind.VAULT, 19, 4, 2, 34);
        stations.add(vault);

        // Each guard walks a loop through these tiles (column, row).
        guards.clear();
        guards.add(new Guard(level, new int[][] {{13, 12}, {26, 12}, {26, 14}, {13, 14}})); // lobby
        guards.add(new Guard(level, new int[][] {{37, 10}, {2, 10}, {2, 9}, {37, 9}}));     // main corridor
        guards.add(new Guard(level, new int[][] {{10, 2}, {10, 5}, {1, 5}, {1, 2}}));       // cash storage
        guards.add(new Guard(level, new int[][] {{29, 5}, {29, 2}, {37, 2}, {37, 5}}));     // gold reserve
        guards.add(new Guard(level, new int[][] {{38, 14}, {28, 14}, {28, 12}, {38, 12}})); // jewellery

        // Cameras sit in room corners: pixel position, sweep from/to (degrees), start phase.
        // Angles: 0 = right, 90 = down, -90 = up.
        int t = Level.TILE;
        cameras.clear();
        cameras.add(new SecurityCamera(28 * t + 5, 1 * t + 5, 30, 95, 0.0));    // gold: watches west door
        cameras.add(new SecurityCamera(28 * t + 5, 7 * t + 19, -50, 5, 0.5));   // gold: watches south door
        cameras.add(new SecurityCamera(28 * t + 5, 18 * t + 19, -85, 2, 0.3));  // jewellery
        cameras.add(new SecurityCamera(13 * t + 5, 5 * t + 5, 0, 70, 0.8));     // vault door
    }

    void startGame() {
        buildWorld();
        timeLeft = TIME_LIMIT;
        detection = 0;
        peakDetection = 0;
        timesSpotted = 0;
        seenNow = false;
        seenByCamera = false;
        unseenTimer = 99;
        vaultProgress = 0;
        nearby = null;
        fail = null;
        completionTime = 0;
        denyCooldown = 0;
        cameraBeepTimer = 0;
        warnedThirty = false;
        lastTickSecond = -1;
        toasts.clear();
        particles.clear();
        setScreen(Screen.PLAYING);
        toast("RETURN THE STOLEN GOODS. STAY OUT OF SIGHT.", Toast.INFO, 4.5);
        play(Sound.START);
    }

    private void toMenu() {
        buildWorld();
        toasts.clear();
        particles.clear();
        detection = 0;
        vaultProgress = 0;
        nearby = null;
        setScreen(Screen.MENU);
    }

    private void setScreen(Screen next) {
        screen = next;
        screenTime = 0;
    }

    // ----------------------------------------------------------------- update

    /** Advances the game by dt seconds using the given input, then clears one-shot key presses. */
    void update(double dt, Input in) {
        screenTime += dt;
        if (in.mutePressed && sound != null) sound.toggleMute();

        switch (screen) {
            case MENU:
                worldTime += dt;
                updateSecurity(dt, false);
                if (in.confirmPressed || clicked(in, BTN_PLAY)) {
                    play(Sound.CLICK);
                    startGame();
                }
                break;
            case PLAYING:
                if (in.pausePressed) {
                    setScreen(Screen.PAUSED);
                } else {
                    updatePlaying(dt, in);
                }
                break;
            case PAUSED:
                if (in.pausePressed || in.confirmPressed) {
                    screen = Screen.PLAYING;
                } else if (in.restartPressed) {
                    startGame();
                } else if (in.menuPressed) {
                    toMenu();
                }
                break;
            case VICTORY:
            case GAME_OVER:
                worldTime += dt;
                updateEffects(dt);
                if (screenTime > 0.6) {
                    if (in.confirmPressed || in.restartPressed || clicked(in, BTN_PRIMARY)) {
                        play(Sound.CLICK);
                        startGame();
                    } else if (in.menuPressed || clicked(in, BTN_SECONDARY)) {
                        play(Sound.CLICK);
                        toMenu();
                    }
                }
                break;
            default:
                break;
        }
        in.clearEdges();
    }

    /** Moves guards and cameras. With playerPresent false they simply patrol (used by the menu). */
    void updateSecurity(double dt, boolean playerPresent) {
        for (Guard g : guards) {
            g.update(dt, level, playerPresent, player.x, player.y, detection);
        }
        for (SecurityCamera c : cameras) {
            c.update(dt);
            c.seesPlayer = playerPresent && c.canSee(level, player.x, player.y);
        }
    }

    private void updatePlaying(double dt, Input in) {
        worldTime += dt;

        if (player.update(dt, in, level)) play(Sound.STEP);

        updateSecurity(dt, true);
        updateDetection(dt);
        updateInteraction(dt, in);
        updateEffects(dt);
        if (screen != Screen.PLAYING) return; // the vault was just secured

        // Countdown.
        timeLeft -= dt;
        if (!warnedThirty && timeLeft <= 30) {
            warnedThirty = true;
            toast("30 SECONDS LEFT!", Toast.WARN, 3);
            play(Sound.TIME_TICK);
        }
        if (timeLeft <= 10 && timeLeft > 0) {
            int second = (int) Math.ceil(timeLeft);
            if (second != lastTickSecond) {
                lastTickSecond = second;
                play(Sound.TIME_TICK);
            }
        }

        if (detection >= 100) {
            detection = 100;
            lose(Fail.CAUGHT);
        } else if (timeLeft <= 0) {
            timeLeft = 0;
            lose(Fail.TIME_UP);
        }
    }

    private void updateDetection(double dt) {
        boolean seen = false;
        boolean camera = false;
        double gain = 0;
        for (Guard g : guards) {
            if (!g.seesPlayer) continue;
            seen = true;
            double dist = Math.hypot(player.x - g.x, player.y - g.y);
            double closeness = 1 - Math.min(1, dist / Guard.RANGE);
            gain = Math.max(gain, GUARD_GAIN_FAR + (GUARD_GAIN_NEAR - GUARD_GAIN_FAR) * closeness);
        }
        for (SecurityCamera c : cameras) {
            if (!c.seesPlayer) continue;
            seen = true;
            camera = true;
            gain = Math.max(gain, CAMERA_GAIN);
        }

        if (seen) {
            if (!seenNow && unseenTimer > 1.0) {
                timesSpotted++;
                play(Sound.SPOTTED);
                toast(camera ? "A CAMERA HAS SPOTTED YOU!" : "SECURITY IS SUSPICIOUS!", Toast.WARN, 2.2);
            }
            unseenTimer = 0;
            detection = Math.min(100, detection + gain * dt);
            if (camera) {
                cameraBeepTimer -= dt;
                if (cameraBeepTimer <= 0) {
                    play(Sound.CAMERA);
                    cameraBeepTimer = 0.45;
                }
            }
        } else {
            unseenTimer += dt;
            cameraBeepTimer = 0;
            if (unseenTimer > DECAY_DELAY) detection = Math.max(0, detection - DECAY * dt);
        }
        seenNow = seen;
        seenByCamera = camera;
        peakDetection = Math.max(peakDetection, detection);
    }

    private void updateInteraction(double dt, Input in) {
        denyCooldown -= dt;

        nearby = null;
        double best = Double.MAX_VALUE;
        for (Station s : stations) {
            double d = Math.hypot(player.x - s.x, player.y - s.y);
            if (d <= s.reach && d < best) {
                best = d;
                nearby = s;
            }
        }

        boolean securing = false;
        if (nearby != null) {
            if (nearby.kind == Station.Kind.VAULT) {
                if (!allReturned()) {
                    if (in.interactPressed && denyCooldown <= 0) {
                        toast("VAULT LOCKED - RETURN ALL GOODS FIRST", Toast.BAD, 2.5);
                        play(Sound.DENY);
                        denyCooldown = 1.0;
                    }
                } else if (in.interactHeld) {
                    securing = true;
                    double before = vaultProgress;
                    vaultProgress += dt / VAULT_HOLD;
                    if ((int) (before * 6) != (int) (vaultProgress * 6)) play(Sound.VAULT_TICK);
                    if (vaultProgress >= 1) {
                        vaultProgress = 1;
                        win();
                    }
                }
            } else if (!nearby.done && in.interactPressed) {
                deposit(nearby);
            }
        }
        if (!securing && screen == Screen.PLAYING) {
            vaultProgress = Math.max(0, vaultProgress - dt * 1.5);
        }
    }

    private void updateEffects(double dt) {
        for (Station s : stations) {
            if (s.done) s.doneAge += dt;
        }
        for (Iterator<Toast> it = toasts.iterator(); it.hasNext();) {
            Toast t = it.next();
            t.age += dt;
            if (t.age >= t.life) it.remove();
        }
        for (Iterator<Particle> it = particles.iterator(); it.hasNext();) {
            Particle p = it.next();
            p.age += dt;
            if (p.age >= p.life) {
                it.remove();
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vx *= 1 - 2.5 * dt;
            p.vy *= 1 - 2.5 * dt;
        }
    }

    // ----------------------------------------------------------------- events

    private void deposit(Station s) {
        s.done = true;
        s.doneAge = 0;
        play(Sound.DEPOSIT);
        toast(s.kind.label + " RETURNED", Toast.GOOD, 2.5);
        int rgb = s.kind == Station.Kind.CASH ? Palette.CASH.getRGB()
                : s.kind == Station.Kind.GOLD ? Palette.GOLD.getRGB() : Palette.JEWEL.getRGB();
        burst(s.x, s.y, rgb, 22);
        if (allReturned()) {
            toast("ALL GOODS RETURNED - NOW SECURE THE MAIN VAULT", Toast.INFO, 4.5);
        }
    }

    private void win() {
        vault.done = true;
        vault.doneAge = 0;
        completionTime = TIME_LIMIT - timeLeft;
        burst(vault.x, vault.y + 8, Palette.GREEN.getRGB(), 40);
        burst(vault.x, vault.y + 8, Palette.GOLD.getRGB(), 30);
        toasts.clear();
        setScreen(Screen.VICTORY);
        play(Sound.VICTORY);
    }

    private void lose(Fail reason) {
        fail = reason;
        toasts.clear();
        setScreen(Screen.GAME_OVER);
        play(reason == Fail.CAUGHT ? Sound.ALARM : Sound.FAIL);
    }

    private void burst(double x, double y, int rgb, int count) {
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            double angle = sparkle.nextDouble() * Math.PI * 2;
            double speed = 30 + sparkle.nextDouble() * 90;
            p.x = x;
            p.y = y;
            p.vx = Math.cos(angle) * speed;
            p.vy = Math.sin(angle) * speed;
            p.life = 0.5 + sparkle.nextDouble() * 0.6;
            p.size = 2 + sparkle.nextInt(2);
            p.rgb = rgb;
            particles.add(p);
        }
    }

    private void toast(String text, int kind, double life) {
        toasts.add(new Toast(text, kind, life));
        while (toasts.size() > 3) toasts.remove(0);
    }

    private void play(Sound s) {
        if (sound != null) sound.play(s);
    }

    private static boolean clicked(Input in, int[] rect) {
        return in.clicked && inside(in, rect);
    }

    static boolean inside(Input in, int[] rect) {
        return in.mouseX >= rect[0] && in.mouseX < rect[0] + rect[2]
                && in.mouseY >= rect[1] && in.mouseY < rect[1] + rect[3];
    }

    // ---------------------------------------------------------------- queries

    boolean isDone(Station.Kind kind) {
        for (Station s : stations) {
            if (s.kind == kind) return s.done;
        }
        return false;
    }

    int returnedCount() {
        int n = 0;
        for (Station s : stations) {
            if (s.kind != Station.Kind.VAULT && s.done) n++;
        }
        return n;
    }

    boolean allReturned() {
        return returnedCount() == 3;
    }

    String objectiveText() {
        if (vault.done) return "VAULT SECURED";
        if (allReturned()) return "SECURE THE MAIN VAULT";
        return "RETURN THE STOLEN GOODS  " + returnedCount() + "/3";
    }
}
