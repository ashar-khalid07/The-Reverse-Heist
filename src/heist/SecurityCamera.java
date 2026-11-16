package heist;

/** A wall-mounted CCTV camera that sweeps back and forth between two angles. */
final class SecurityCamera {
    static final double RANGE = 168;
    static final double FOV = Math.toRadians(46);
    private static final double SWEEP_SPEED = 0.7;
    private static final double END_PAUSE = 0.7;

    final double x, y;
    final double minAngle, maxAngle;
    double angle;
    boolean seesPlayer;
    private int direction = 1;
    private double pause;

    /**
     * @param phase where in the sweep the camera starts, 0 (min angle) to 1 (max angle),
     *              so the cameras are not all in step with each other
     */
    SecurityCamera(double x, double y, double minDegrees, double maxDegrees, double phase) {
        this.x = x;
        this.y = y;
        this.minAngle = Math.toRadians(minDegrees);
        this.maxAngle = Math.toRadians(maxDegrees);
        this.angle = minAngle + (maxAngle - minAngle) * phase;
    }

    void update(double dt) {
        if (pause > 0) {
            pause -= dt;
            return;
        }
        angle += direction * SWEEP_SPEED * dt;
        if (angle >= maxAngle) {
            angle = maxAngle;
            direction = -1;
            pause = END_PAUSE;
        } else if (angle <= minAngle) {
            angle = minAngle;
            direction = 1;
            pause = END_PAUSE;
        }
    }

    boolean canSee(Level level, double px, double py) {
        double dx = px - x, dy = py - y;
        if (dx * dx + dy * dy > RANGE * RANGE) return false;
        double off = Level.angleDiff(Math.atan2(dy, dx), angle);
        if (Math.abs(off) > FOV / 2) return false;
        return level.lineOfSight(x, y, px, py);
    }
}
