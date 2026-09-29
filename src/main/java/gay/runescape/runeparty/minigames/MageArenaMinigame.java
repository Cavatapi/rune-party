package gay.runescape.runeparty.minigames;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Mage Arena
 *
 * One player is the Mage while the remaining players dodge spells
 * inside a 6x5 arena.
 *
 * The Mage targets arena tiles. Targeted tiles show a circular shadow before
 * erupting with the Flames of Zamorak animation when the spell detonates.
 */
public class MageArenaMinigame implements Minigame
{
    private static final Color SAFE_COLOR = new Color(65, 160, 90);
    private static final Color WARNING_COLOR = new Color(240, 200, 40);
    private static final Color DANGER_COLOR = new Color(220, 45, 45);
    private static final Color OUTLINE_COLOR = new Color(255, 255, 255);

    private static final int GRID_WIDTH = 6;
    private static final int GRID_HEIGHT = 5;
    private static final int GRID_GAP = 1;

    @Override
    public String getKey()
    {
        return "mage-arena";
    }

    @Override
    public String getDisplayName()
    {
        return "Mage Arena";
    }

    /**
     * Small wheel icon representing the Mage Arena.
     *
     * Most tiles are green, one is yellow to represent a charging spell,
     * and one is red to represent a detonating spell.
     */
    @Override
    public void drawIcon(Graphics2D g, int x, int y, int size, float alpha)
    {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));

        Color safe = withAlpha(SAFE_COLOR, a);
        Color warning = withAlpha(WARNING_COLOR, a);
        Color danger = withAlpha(DANGER_COLOR, a);
        Color outline = withAlpha(OUTLINE_COLOR, a);

        int cellWidth =
                (size - GRID_GAP * (GRID_WIDTH - 1)) / GRID_WIDTH;

        int cellHeight =
                (size - GRID_GAP * (GRID_HEIGHT - 1)) / GRID_HEIGHT;

        int usedWidth =
                cellWidth * GRID_WIDTH +
                        GRID_GAP * (GRID_WIDTH - 1);

        int usedHeight =
                cellHeight * GRID_HEIGHT +
                        GRID_GAP * (GRID_HEIGHT - 1);

        int left = x - usedWidth / 2;
        int top = y - usedHeight / 2;

        for (int row = 0; row < GRID_HEIGHT; row++)
        {
            for (int col = 0; col < GRID_WIDTH; col++)
            {
                Color tileColor = safe;

                // Charging spell
                if (row == 2 && col == 3)
                {
                    tileColor = warning;
                }

                // Detonating spell
                if (row == 1 && col == 1)
                {
                    tileColor = danger;
                }

                int tileX =
                        left + col * (cellWidth + GRID_GAP);

                int tileY =
                        top + row * (cellHeight + GRID_GAP);

                g.setColor(tileColor);
                g.fillRect(
                        tileX,
                        tileY,
                        cellWidth,
                        cellHeight
                );

                g.setColor(outline);
                g.drawRect(
                        tileX,
                        tileY,
                        cellWidth,
                        cellHeight
                );
            }
        }
    }

    private static Color withAlpha(Color color, int alpha)
    {
        return new Color(
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                alpha
        );
    }
}
