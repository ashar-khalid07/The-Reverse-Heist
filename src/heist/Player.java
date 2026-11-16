package heist;

/** The player character: movement, wall collision and walk animation state. */
final class Player {
    static final double RADIUS = 7;
    static final double SPEED = 112;
    /** How quickly velocity catches up with the keys; high = snappy, low = floaty. */
    private static final double RESPONSE = 16;
    private static final double STEP_DISTANCE = 26;

    double x, y;
    double vx, vy;
    double facing = -Math.PI / 2;
    double walkPhase;
    boolean moving;
    private double stepAccumulator;

    Player(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /** Advances the player by dt seconds. Returns true on frames where a footstep lands. */
    boolean update(double dt, Input in, Level level) {
        double ix = (in.right ? 1 : 0) - (in.left ? 1 : 0);
        double iy = (in.down ? 1 : 0) - (in.up ? 1 : 0);
        if (ix != 0 && iy != 0) {
            ix *= Math.sqrt(0.5);
            iy *= Math.sqrt(0.5);
        }
        double k = Math.min(1, RESPONSE * dt);
        vx += (ix * SPEED - vx) * k;
        vy += (iy * SPEED - vy) * k;
        if (ix == 0 && Math.abs(vx) < 1) vx = 0;
        if (iy == 0 && Math.abs(vy) < 1) vy = 0;

        double beforeX = x, beforeY = y;

        // Move one axis at a time so the player slides along walls instead of sticking.
        double nx = x + vx * dt;
        if (!level.collides(nx, y, RADIUS)) {
            x = nx;
        } else {
            if (iy == 0) nudgeAroundCorner(level, nx, true, dt);
            vx = 0;
        }
        double ny = y + vy * dt;
        if (!level.collides(x, ny, RADIUS)) {
            y = ny;
        } else {
            if (ix == 0) nudgeAroundCorner(level, ny, false, dt);
            vy = 0;
        }

        double moved = Math.hypot(x - beforeX, y - beforeY);
        double speed = Math.hypot(vx, vy);
        moving = speed > 12;
        if (moving) {
            double target = Math.atan2(vy, vx);
            facing += Level.angleDiff(target, facing) * Math.min(1, 18 * dt);
            walkPhase += moved * 0.42;
        }
        stepAccumulator += moved;
        if (stepAccumulator >= STEP_DISTANCE) {
            stepAccumulator = 0;
            return true;
        }
        return false;
    }

    /**
     * If the player is blocked by the very edge of a wall (for example when
     * slightly misaligned with a doorway), slide them sideways so they slip
     * through instead of getting stuck on the corner.
     */
    private void nudgeAroundCorner(Level level, double blockedPos, boolean movingOnX, double dt) {
        double slide = SPEED * 0.75 * dt;
        for (int offset = 1; offset <= 6; offset++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                double shift = sign * offset;
                boolean free = movingOnX
                        ? !level.collides(blockedPos, y + shift, RADIUS)
                        : !level.collides(x + shift, blockedPos, RADIUS);
                if (!free) continue;
                double step = sign * Math.min(slide, offset);
                if (movingOnX) {
                    if (!level.collides(x, y + step, RADIUS)) y += step;
                } else {
                    if (!level.collides(x + step, y, RADIUS)) x += step;
                }
                return;
            }
        }
    }
}
