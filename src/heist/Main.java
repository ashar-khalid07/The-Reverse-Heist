package heist;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Entry point: opens the window and starts the game. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JFrame frame = new JFrame("The Reverse Heist");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setIconImage(icon());
                GamePanel panel = new GamePanel(pickWindowScale());
                frame.setContentPane(panel);
                frame.pack();
                frame.setMinimumSize(new Dimension(640, 440));
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                panel.requestFocusInWindow();
                panel.start();
            }
        });
    }

    /** Picks the largest tidy window size that comfortably fits the screen. */
    private static double pickWindowScale() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        double[] options = {2.0, 1.5, 1.25, 1.0};
        for (double s : options) {
            if (Renderer.W * s <= screen.width * 0.92 && Renderer.H * s <= screen.height * 0.84) return s;
        }
        return 1.0;
    }

    private static BufferedImage icon() {
        BufferedImage img = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Palette.HUD_BG);
        g.fillRoundRect(0, 0, 48, 48, 12, 12);
        g.scale(1.6, 1.6);
        Sprites.itemIcon(g, Station.Kind.GOLD, 3, 3, false);
        g.dispose();
        return img;
    }
}
