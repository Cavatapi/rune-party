package gay.runescape.runeparty.minigames;

import gay.runescape.runeparty.RunePartyPlugin;
import java.awt.Color;
import java.awt.Graphics2D;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Entirely screen-driven -- transformed-Brutus rendering lives in overlays/PlayerTransformOverlay,
 * the gather message/dash countdown in AnnouncementOverlay, the eliminated-target skull in
 * PlayerOverlay -- no side-panel control at all, same reasoning HotPotatoMinigame/WhosYourJaddyMinigame
 * already give (see hasSidePanelPresence). A real, randomly-reachable mini-game, so it needs a real
 * wheel icon. */
public class BrutusAttackMinigame implements Minigame
{
    private static final Color BRUTUS_ZONE_COLOR = new Color(0xCC, 0x22, 0x22);
    private static final Color TARGET_ZONE_COLOR = new Color(0x22, 0x66, 0xCC);

    @Override
    public String getKey()
    {
        return "brutus-attack";
    }

    @Override
    public String getDisplayName()
    {
        return "Brutus Bullet";
    }

    /** Two small colored squares facing each other -- Brutus's own zone (red) and the targets'
     * zone (blue), the exact same two hex colors the server's own brutus_attack.py colors the
     * arena's two ends with -- purely programmatic, same "no bundled raster asset needed for a
     * wheel icon" convention ClickClickClickMinigame/HotPotatoMinigame's own docs give. */
    @Override
    public void drawIcon(Graphics2D g, int x, int y, int size, float alpha)
    {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        int squareSize = Math.max(2, size / 3);
        int gap = Math.max(1, size / 8);

        g.setColor(new Color(BRUTUS_ZONE_COLOR.getRed(), BRUTUS_ZONE_COLOR.getGreen(), BRUTUS_ZONE_COLOR.getBlue(), a));
        g.fillRect(x - gap / 2 - squareSize, y - squareSize / 2, squareSize, squareSize);

        g.setColor(new Color(TARGET_ZONE_COLOR.getRed(), TARGET_ZONE_COLOR.getGreen(), TARGET_ZONE_COLOR.getBlue(), a));
        g.fillRect(x + gap / 2, y - squareSize / 2, squareSize, squareSize);
    }

    /** Never actually called -- see hasSidePanelPresence. */
    @Override
    public JComponent createControlPanel(RunePartyPlugin plugin)
    {
        return new JPanel();
    }

    @Override
    public boolean hasSidePanelPresence()
    {
        return false;
    }
}
