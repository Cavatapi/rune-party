package gay.runescape.runeparty.minigames;

import gay.runescape.runeparty.RunePartyPlugin;
import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.LongSupplier;

public final class MageArenaPresentation implements MinigamePresentationFeature
{
    public static final int GRID_WIDTH = 6;
    public static final int GRID_HEIGHT = 5;
    public static final int EXPECTED_ARENA_TILES = GRID_WIDTH * GRID_HEIGHT;

    public static final long CAST_COOLDOWN_MS = 600;

    // Ground shadow for 1.2 seconds.
    public static final long WARNING_MS = 1200;

    // Damage window; the detonation animation plays once to completion.
    public static final long DANGER_MS = 600;

    private final RunePartyPlugin plugin;
    private final LongSupplier clock;

    private final List<WorldPoint> arenaTiles = new ArrayList<>();
    private final List<SpellCast> activeSpells = new ArrayList<>();

    private volatile long roundStartAt = 0;
    private volatile long lastCastAt = 0;

    public MageArenaPresentation(RunePartyPlugin plugin)
    {
        this(plugin, System::currentTimeMillis);
    }

    MageArenaPresentation(RunePartyPlugin plugin, LongSupplier clock)
    {
        this.plugin = plugin;
        this.clock = clock;
    }

    public synchronized void buildPrototypeArena(WorldPoint anchor)
    {
        reset();

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

    public synchronized boolean castSpell(WorldPoint target)
    {
        if (target == null || !isArenaTile(target))
        {
            return false;
        }

        long now = clock.getAsLong();

        if (lastCastAt != 0 && now - lastCastAt < CAST_COOLDOWN_MS)
        {
            return false;
        }

        lastCastAt = now;
        activeSpells.add(new SpellCast(target, now));

        return true;
    }

    public synchronized void updateSpells()
    {
        long now = clock.getAsLong();

        Iterator<SpellCast> iterator = activeSpells.iterator();

        while (iterator.hasNext())
        {
            SpellCast spell = iterator.next();

            if (now >= spell.getFinishedAt())
            {
                iterator.remove();
            }
            else if (!spell.detonated && now >= spell.getDetonationAt())
            {
                spell.detonated = true;
                plugin.triggerMageArenaSpell(spell.getTarget());
            }
        }
    }

    public synchronized boolean isWarningTile(WorldPoint point)
    {
        if (point == null)
        {
            return false;
        }

        long now = clock.getAsLong();

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

    public synchronized boolean isDangerTile(WorldPoint point)
    {
        if (point == null)
        {
            return false;
        }

        long now = clock.getAsLong();

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
        roundStartAt = clock.getAsLong();
    }

    @Override
    public synchronized void reset()
    {
        arenaTiles.clear();
        activeSpells.clear();
        roundStartAt = 0;
        lastCastAt = 0;
        plugin.clearMageArenaSpellEffects();
    }

    public synchronized List<WorldPoint> getArenaTiles()
    {
        return Collections.unmodifiableList(new ArrayList<>(arenaTiles));
    }

    public synchronized boolean isArenaBuilt()
    {
        return arenaTiles.size() == EXPECTED_ARENA_TILES;
    }

    public synchronized boolean isArenaTile(WorldPoint point)
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
        private boolean detonated;

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
