package heist;

/** Something the player can interact with: a return point for stolen goods, or the vault door. */
final class Station {
    enum Kind {
        CASH("CASH"), GOLD("GOLD"), JEWELLERY("JEWELLERY"), VAULT("MAIN VAULT");

        final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    final Kind kind;
    /** Top-left tile and width in tiles. */
    final int col, row, widthTiles;
    /** Centre in pixels, and how close the player must be to use it. */
    final double x, y, reach;

    boolean done;
    /** Seconds since it was completed; drives the confirmation flash. */
    double doneAge;

    Station(Kind kind, int col, int row, int widthTiles, double reach) {
        this.kind = kind;
        this.col = col;
        this.row = row;
        this.widthTiles = widthTiles;
        this.x = col * Level.TILE + widthTiles * Level.TILE / 2.0;
        this.y = Level.center(row);
        this.reach = reach;
    }
}
