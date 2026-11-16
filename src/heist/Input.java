package heist;

/**
 * Snapshot of what the player is pressing. The window fills this in; the game
 * reads it. "Pressed" fields are one-shot edges that the game clears after use.
 */
final class Input {
    boolean up, down, left, right;
    boolean interactHeld;

    boolean interactPressed;
    boolean confirmPressed;
    boolean pausePressed;
    boolean menuPressed;
    boolean restartPressed;
    boolean mutePressed;
    boolean clicked;

    /** Mouse position in game (logical) coordinates. */
    double mouseX = -1, mouseY = -1;

    void clearEdges() {
        interactPressed = false;
        confirmPressed = false;
        pausePressed = false;
        menuPressed = false;
        restartPressed = false;
        mutePressed = false;
        clicked = false;
    }

    void releaseAll() {
        up = down = left = right = false;
        interactHeld = false;
        clearEdges();
    }
}
