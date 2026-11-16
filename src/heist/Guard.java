package heist;

import java.util.ArrayList;
import java.util.List;

/**
 * A patrolling security guard.
 *
 * Normal life is walking a fixed loop of waypoints. If the guard sees the
 * player they stop and stare (SUSPICIOUS); if detection keeps climbing they
 * give chase (ALERTED). After losing the player they walk to where they last
 * saw them, look around, then go back to the patrol.
 */
final class Guard {
    enum State { UNAWARE, SUSPICIOUS, ALERTED }

    private enum Mode { PATROL, WATCH, CHASE, SEARCH, LOOK, RETURN }

    static final double RANGE = 150;
    static final double FOV = Math.toRadians(70);
    /** Guards notice anyone this close, even behind them. */
    static final double PROXIMITY = 16;

    private static final double PATROL_SPEED = 52;
    private static final double SEARCH_SPEED = 64;
    private static final double CHASE_SPEED = 86;
    private static final double WAYPOINT_PAUSE = 0.6;
    private static final double CHASE_THRESHOLD = 55;

    double x, y, facing;
    State state = State.UNAWARE;
    boolean seesPlayer;
    boolean moving;
    double walkPhase;

    private Mode mode = Mode.PATROL;

    /** The patrol loop as pixel positions, and which of them are pause points. */
    private final double[][] route;
    private final boolean[] pausePoint;
    private int routeIndex;
    private double pause;

    private List<int[]> path;
    private int pathIndex;
    private double repathTimer;
    private double timer;
    private double lookBase;
    private double lastSeenX, lastSeenY;

    Guard(Level level, int[][] waypoints) {
        List<double[]> points = new ArrayList<double[]>();
        List<Boolean> pauses = new ArrayList<Boolean>();
        for (int i = 0; i < waypoints.length; i++) {
            int[] a = waypoints[i];
            int[] b = waypoints[(i + 1) % waypoints.length];
            List<int[]> leg = level.findPath(a[0], a[1], b[0], b[1]);
            if (leg == null) {
                throw new IllegalStateException("Guard waypoint unreachable: " + a[0] + "," + a[1]
                        + " -> " + b[0] + "," + b[1]);
            }
            for (int k = 0; k < leg.size() - 1; k++) {
                int[] t = leg.get(k);
                points.add(new double[] {Level.center(t[0]), Level.center(t[1])});
                pauses.add(k == 0);
            }
        }
        route = points.toArray(new double[0][]);
        pausePoint = new boolean[route.length];
        for (int i = 0; i < route.length; i++) pausePoint[i] = pauses.get(i);
        x = route[0][0];
        y = route[0][1];
        routeIndex = 1 % route.length;
        facing = Math.atan2(route[routeIndex][1] - y, route[routeIndex][0] - x);
    }

    boolean canSee(Level level, double px, double py) {
        double dx = px - x, dy = py - y;
        double distSq = dx * dx + dy * dy;
        if (distSq > RANGE * RANGE) return false;
        if (distSq > PROXIMITY * PROXIMITY) {
            double off = Level.angleDiff(Math.atan2(dy, dx), facing);
            if (Math.abs(off) > FOV / 2) return false;
        }
        return level.lineOfSight(x, y, px, py);
    }

    void update(double dt, Level level, boolean playerPresent, double px, double py, double detection) {
        moving = false;
        seesPlayer = playerPresent && canSee(level, px, py);

        if (seesPlayer) {
            lastSeenX = px;
            lastSeenY = py;
            if (mode != Mode.CHASE) {
                if (detection >= CHASE_THRESHOLD) {
                    mode = Mode.CHASE;
                    repathTimer = 0;
                } else {
                    mode = Mode.WATCH;
                }
            }
        } else if (mode == Mode.WATCH || mode == Mode.CHASE) {
            mode = Mode.SEARCH;
            timer = 0.5;
            path = level.findPath(Level.toTile(x), Level.toTile(y),
                    Level.toTile(lastSeenX), Level.toTile(lastSeenY));
            pathIndex = 1;
        }

        switch (mode) {
            case PATROL:
                state = State.UNAWARE;
                patrol(dt);
                break;
            case WATCH:
                state = State.SUSPICIOUS;
                turnToward(Math.atan2(py - y, px - x), 6 * dt);
                break;
            case CHASE:
                state = State.ALERTED;
                chase(dt, level, px, py);
                break;
            case SEARCH:
                state = State.SUSPICIOUS;
                if (timer > 0) {
                    timer -= dt;
                    turnToward(Math.atan2(lastSeenY - y, lastSeenX - x), 6 * dt);
                } else if (followPath(SEARCH_SPEED, dt)) {
                    mode = Mode.LOOK;
                    timer = 2.0;
                    lookBase = facing;
                }
                break;
            case LOOK:
                state = State.SUSPICIOUS;
                timer -= dt;
                facing = lookBase + Math.sin((2.0 - timer) * 3.2) * 1.1;
                if (timer <= 0) {
                    mode = Mode.RETURN;
                    double[] next = route[routeIndex];
                    path = level.findPath(Level.toTile(x), Level.toTile(y),
                            Level.toTile(next[0]), Level.toTile(next[1]));
                    pathIndex = 1;
                }
                break;
            case RETURN:
                state = State.UNAWARE;
                if (followPath(PATROL_SPEED, dt)) mode = Mode.PATROL;
                break;
            default:
                break;
        }

        if (moving) walkPhase += dt * 9;
    }

    private void patrol(double dt) {
        double[] target = route[routeIndex];
        double heading = Math.atan2(target[1] - y, target[0] - x);
        if (pause > 0) {
            // Standing at a corner: slowly swing round to face the next leg.
            pause -= dt;
            turnToward(heading, 2.6 * dt);
            return;
        }
        if (Math.hypot(target[0] - x, target[1] - y) > 0.5) turnToward(heading, 7 * dt);
        moving = true;
        if (moveToward(target[0], target[1], PATROL_SPEED, dt)) {
            if (pausePoint[routeIndex]) pause = WAYPOINT_PAUSE;
            routeIndex = (routeIndex + 1) % route.length;
        }
    }

    private void chase(double dt, Level level, double px, double py) {
        repathTimer -= dt;
        if (repathTimer <= 0) {
            path = level.findPath(Level.toTile(x), Level.toTile(y), Level.toTile(px), Level.toTile(py));
            pathIndex = 1;
            repathTimer = 0.3;
        }
        turnToward(Math.atan2(py - y, px - x), 8 * dt);
        if (Math.hypot(px - x, py - y) <= 15) return;

        boolean direct = path == null || pathIndex >= path.size() - 1;
        double tx, ty;
        if (direct) {
            tx = px;
            ty = py;
        } else {
            int[] t = path.get(pathIndex);
            tx = Level.center(t[0]);
            ty = Level.center(t[1]);
        }
        double oldX = x, oldY = y;
        boolean arrived = moveToward(tx, ty, CHASE_SPEED, dt);
        if (level.solid(Level.toTile(x), Level.toTile(y))) {
            x = oldX;
            y = oldY;
            return;
        }
        moving = true;
        if (arrived && !direct) pathIndex++;
    }

    /** Walks along the current path. Returns true once the end is reached. */
    private boolean followPath(double speed, double dt) {
        if (path == null || pathIndex >= path.size()) return true;
        int[] t = path.get(pathIndex);
        double tx = Level.center(t[0]), ty = Level.center(t[1]);
        if (Math.hypot(tx - x, ty - y) > 0.5) turnToward(Math.atan2(ty - y, tx - x), 7 * dt);
        moving = true;
        if (moveToward(tx, ty, speed, dt)) pathIndex++;
        return pathIndex >= path.size();
    }

    private boolean moveToward(double tx, double ty, double speed, double dt) {
        double dx = tx - x, dy = ty - y;
        double dist = Math.sqrt(dx * dx + dy * dy);
        double step = speed * dt;
        if (dist <= step) {
            x = tx;
            y = ty;
            return true;
        }
        x += dx / dist * step;
        y += dy / dist * step;
        return false;
    }

    private void turnToward(double target, double maxStep) {
        double diff = Level.angleDiff(target, facing);
        if (Math.abs(diff) <= maxStep) facing = target;
        else facing += Math.signum(diff) * maxStep;
    }
}
