package heist;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * The window surface: turns keyboard and mouse events into an {@link Input},
 * runs the game loop, and asks the {@link Renderer} to draw each frame.
 */
final class GamePanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final transient Game game = new Game();
    private final transient Renderer renderer = new Renderer();
    private final transient Input input = new Input();

    private final AtomicBoolean framePending = new AtomicBoolean(false);
    private long lastFrameNanos;
    private double unsimulated;

    GamePanel(double windowScale) {
        setPreferredSize(new Dimension((int) Math.round(Renderer.W * windowScale),
                (int) Math.round(Renderer.H * windowScale)));
        setBackground(Color.BLACK);
        setFocusable(true);
        setDoubleBuffered(true);
        game.sound = new Audio();

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                key(e.getKeyCode(), true);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                key(e.getKeyCode(), false);
            }
        });
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                track(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                track(e);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                track(e);
                input.clicked = true;
                requestFocusInWindow();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                input.releaseAll();
            }
        });
    }

    private void track(MouseEvent e) {
        input.mouseX = (e.getX() - renderer.offsetX) / renderer.scale;
        input.mouseY = (e.getY() - renderer.offsetY) / renderer.scale;
    }

    private void key(int code, boolean down) {
        switch (code) {
            case KeyEvent.VK_W: case KeyEvent.VK_UP: input.up = down; break;
            case KeyEvent.VK_S: case KeyEvent.VK_DOWN: input.down = down; break;
            case KeyEvent.VK_A: case KeyEvent.VK_LEFT: input.left = down; break;
            case KeyEvent.VK_D: case KeyEvent.VK_RIGHT: input.right = down; break;
            case KeyEvent.VK_E:
                if (down && !input.interactHeld) input.interactPressed = true;
                input.interactHeld = down;
                break;
            case KeyEvent.VK_ENTER: case KeyEvent.VK_SPACE:
                if (down) input.confirmPressed = true;
                break;
            case KeyEvent.VK_P:
                if (down) input.pausePressed = true;
                break;
            case KeyEvent.VK_ESCAPE:
                if (down) {
                    input.pausePressed = true;
                    input.menuPressed = true;
                }
                break;
            case KeyEvent.VK_Q:
                if (down) input.menuPressed = true;
                break;
            case KeyEvent.VK_R:
                if (down) input.restartPressed = true;
                break;
            case KeyEvent.VK_M:
                if (down) input.mutePressed = true;
                break;
            default:
                break;
        }
    }

    /** Starts the game loop. A background thread only paces it; all game work runs on the Swing thread. */
    void start() {
        lastFrameNanos = System.nanoTime();
        Thread pacer = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    if (framePending.compareAndSet(false, true)) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                frame();
                            }
                        });
                    }
                    try {
                        Thread.sleep(3);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }, "game-loop");
        pacer.setDaemon(true);
        pacer.start();
    }

    private void frame() {
        try {
            long now = System.nanoTime();
            double elapsed = (now - lastFrameNanos) / 1e9;
            if (elapsed < 1.0 / 62) return; // cap at roughly 60 frames a second
            lastFrameNanos = now;

            // Advance the simulation in fixed steps so it behaves the same on every machine.
            unsimulated += Math.min(elapsed, 0.1);
            while (unsimulated >= Game.STEP) {
                game.update(Game.STEP, input);
                unsimulated -= Game.STEP;
            }
            paintImmediately(0, 0, getWidth(), getHeight());
            Toolkit.getDefaultToolkit().sync();
        } finally {
            framePending.set(false);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        renderer.render((Graphics2D) g, game, getWidth(), getHeight(), input.mouseX, input.mouseY);
    }
}
