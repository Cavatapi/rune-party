package gay.runescape.runeparty.minigames;

import gay.runescape.runeparty.RunePartyPlugin;

import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Client-side gameplay state for Mage Arena.
 *
 * Mage Arena uses a 6x5 dodger grid. One player acts as the Mage
 * from a separate position outside the grid and casts spells onto
 * individual arena tiles.
 *
 * For the prototype, this class is intentionally local-only.
 * Server events and multiplayer synchronization will be added after
 * the basic arena and spell mechanics are working.
 */
public final class MageArenaPresentation implements MinigamePresentationFeature
{
    public static final int GRID_WIDTH = 6;
    public static final int GRID_HEIGHT = 5;

    public static final int EXPECTED_ARENA_TILES =
            GRID_WIDTH * GRID_HEIGHT;

    // Mage can begin another cast every 0.6 seconds.
    public static final long CAST_COOLDOWN_MS = 600;

    // Target tile stays yellow before the spell detonates.
    public static final long WARNING_MS = 1800;

    // Target tile stays red briefly after detonation.
    public static final long DANGER_MS = 600;

    private final RunePartyPlugin plugin;

    /*
     * The 30 tiles making up the dodger arena.
     *
     * Later these will be populated from the board-swap tiles supplied
     * by Rune Party, similar to Rune Match.
     */
    private final List<WorldPoint> arenaTiles = new ArrayList<>();

    private volatile long roundStartAt = 0;

    public MageArenaPresentation(RunePartyPlugin plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public void onStarted(boolean catchingUp)
    {
        reset();
    }

    @Override
    public void onRoundBegin(boolean catchingUp)
    {
        roundStartAt = System.currentTimeMillis();
    }

    @Override
    public void reset()
    {
        arenaTiles.clear();
        roundStartAt = 0;
    }

    public List<WorldPoint> getArenaTiles()
    {
        return Collections.unmodifiableList(arenaTiles);
    }

    public boolean isArenaBuilt()
    {
        return arenaTiles.size() == EXPECTED_ARENA_TILES;
    }

    public boolean isArenaTile(WorldPoint point)
    {
        return point != null && arenaTiles.contains(point);
    }

    public long getRoundStartAt()
    {
        return roundStartAt;
    }

    public boolean isRoundActive()
    {
        return roundStartAt != 0;
    }

    public RunePartyPlugin getPlugin()
    {
        return plugin;
    }
}