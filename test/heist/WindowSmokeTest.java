package heist;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Opens the real game window (needs a display), presses a few keys with a
 * robot and saves screenshots. Fails if anything throws on the way.
 */
public final class WindowSmokeTest {
    private static volatile Throwable crash;

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "build/shots");
        dir.mkdirs();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                crash = e;
                e.printStackTrace();
            }
        });
        Main.main(new String[0]);
        Thread.sleep(3000);
        Robot robot = new Robot();
        Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        ImageIO.write(robot.createScreenCapture(screen), "png", new File(dir, "w1-menu.png"));

        tap(robot, KeyEvent.VK_ENTER);
        Thread.sleep(1200);
        robot.keyPress(KeyEvent.VK_D);
        Thread.sleep(1500);
        robot.keyRelease(KeyEvent.VK_D);
        robot.keyPress(KeyEvent.VK_W);
        Thread.sleep(500);
        robot.keyRelease(KeyEvent.VK_W);
        Thread.sleep(200);
        ImageIO.write(robot.createScreenCapture(screen), "png", new File(dir, "w2-playing.png"));

        tap(robot, KeyEvent.VK_P);
        Thread.sleep(600);
        ImageIO.write(robot.createScreenCapture(screen), "png", new File(dir, "w3-paused.png"));
        tap(robot, KeyEvent.VK_M);
        Thread.sleep(300);

        System.out.println(crash == null ? "WINDOW TEST OK" : "WINDOW TEST CRASHED: " + crash);
        System.exit(crash == null ? 0 : 1);
    }

    private static void tap(Robot robot, int key) throws InterruptedException {
        robot.keyPress(key);
        Thread.sleep(60);
        robot.keyRelease(key);
    }
}
