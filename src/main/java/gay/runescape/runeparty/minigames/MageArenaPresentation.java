package gay.runescape.runeparty.minigames;

import gay.runescape.runeparty.RunePartyPlugin;
import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class MageArenaPresentation implements MinigamePresentationFeature
{
    public static final int GRID_WIDTH = 6;
    public static final int GRID_HEIGHT = 5;
    public static final int EXPECTED_ARENA_TILES = GRID_WIDTH * GRID_HEIGHT;

    public static final long CAST_COOLDOWN_MS = 600;

    // Yellow for 1.2 seconds.
    public static final long WARNING_MS = 1200;

    // Red for 0.6 seconds.
    public static final long DANGER_MS = 600;

    private final RunePartyPlugin plugin;

    private final List<WorldPoint> arenaTiles = new ArrayList<>();
    private final List<SpellCast> activeSpells = new ArrayList<>();

    private volatile long roundStartAt = 0;
    private volatile long lastCastAt = 0;

    public MageArenaPresentation(RunePartyPlugin plugin)
    {
        this.plugin = plugin;
    }

    public void buildPrototypeArena(WorldPoint anchor)
    {
        arenaTiles.clear();

        if (anchor == null)
        {
            return;
        }

        for (int y = 0; y < GRID_HEIGHT; y++)
        {
            for (int x = 0; x < GRID_WIDTH; x++)
            {
                arenaTiles.add(new WorldPoint(
                        anchor.getX() + x,
                        anchor.getY() + y,
                        anchor.getPlane()
                ));
            }
        }
    }

    public boolean castSpell(WorldPoint target)
    {
        if (target == null || !isArenaTile(target))
        {
            return false;
        }

        long now = System.currentTimeMillis();

        if (lastCastAt != 0 && now - lastCastAt < CAST_COOLDOWN_MS)
        {
            return false;
        }

        lastCastAt = now;
        activeSpells.add(new SpellCast(target, now));

        return true;
    }

    public void updateSpells()
    {
        long now = System.currentTimeMillis();

        Iterator<SpellCast> iterator = activeSpells.iterator();

        while (iterator.hasNext())
        {
            SpellCast spell = iterator.next();

            if (now >= spell.getFinishedAt())
            {
                iterator.remove();
            }
        }
    }

    public boolean isWarningTile(WorldPoint point)
    {
        if (point == null)
        {
            return false;
        }

        long now = System.currentTimeMillis();

        for (SpellCast spell : activeSpells)
        {
            if (spell.getTarget().equals(point)
                    && now >= spell.getCastAt()
                    && now < spell.getDetonationAt())
            {
                return true;
            }
        }

        return false;
    }

    public boolean isDangerTile(WorldPoint point)
    {
        if (point == null)
        {
            return false;
        }

        long now = System.currentTimeMillis();

        for (SpellCast spell : activeSpells)
        {
            if (spell.getTarget().equals(point)
                    && now >= spell.getDetonationAt()
                    && now < spell.getFinishedAt())
            {
                return true;
            }
        }

        return false;
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
        activeSpells.clear();
        roundStartAt = 0;
        lastCastAt = 0;
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

    private static final class SpellCast
    {
        private final WorldPoint target;
        private final long castAt;

        private SpellCast(WorldPoint target, long castAt)
        {
            this.target = target;
            this.castAt = castAt;
        }

        public WorldPoint getTarget()
        {
            return target;
        }

        public long getCastAt()
        {
            return castAt;
        }

        public long getDetonationAt()
        {
            return castAt + WARNING_MS;
        }

        public long getFinishedAt()
        {
            return castAt + WARNING_MS + DANGER_MS;
        }
    }
}