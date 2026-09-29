package gay.runescape.runeparty.minigames;

import gay.runescape.runeparty.RunePartyPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class MageArenaPresentationTest
{
    private final AtomicLong now = new AtomicLong(10000);
    private final RecordingPlugin plugin = new RecordingPlugin();
    private final MageArenaPresentation presentation = new MageArenaPresentation(plugin, now::get);
    private final WorldPoint target = new WorldPoint(3200, 3200, 0);

    @Test
    public void detonatesOnceAtWarningBoundaryAndPreservesDamageWindow()
    {
        presentation.buildPrototypeArena(target);
        assertTrue(presentation.castSpell(target));
        now.addAndGet(1199);
        presentation.updateSpells();
        assertTrue(presentation.isWarningTile(target));
        assertFalse(presentation.isDangerTile(target));
        assertTrue(plugin.detonations.isEmpty());

        now.incrementAndGet();
        presentation.updateSpells();
        presentation.updateSpells();
        assertFalse(presentation.isWarningTile(target));
        assertTrue(presentation.isDangerTile(target));
        assertEquals(1, plugin.detonations.size());
        assertEquals(target, plugin.detonations.get(0));

        now.addAndGet(599);
        assertTrue(presentation.isDangerTile(target));
        now.incrementAndGet();
        presentation.updateSpells();
        assertFalse(presentation.isDangerTile(target));
        assertEquals(1, plugin.detonations.size());
    }

    @Test
    public void overlappingCastsOnSameTileEachDetonateOnce()
    {
        presentation.buildPrototypeArena(target);
        assertTrue(presentation.castSpell(target));
        now.addAndGet(599);
        assertFalse(presentation.castSpell(target));
        now.incrementAndGet();
        assertTrue(presentation.castSpell(target));
        now.addAndGet(600);
        presentation.updateSpells();
        assertTrue(presentation.isWarningTile(target));
        assertTrue(presentation.isDangerTile(target));
        now.addAndGet(600);
        presentation.updateSpells();
        assertEquals(2, plugin.detonations.size());
    }

    @Test
    public void expiredAndResetCastsNeverReplay()
    {
        presentation.buildPrototypeArena(target);
        assertFalse(presentation.castSpell(new WorldPoint(1, 1, 0)));
        assertTrue(presentation.castSpell(target));
        now.addAndGet(1800);
        presentation.updateSpells();
        assertTrue(plugin.detonations.isEmpty());

        assertTrue(presentation.castSpell(target));
        int clears = plugin.clears;
        presentation.reset();
        assertEquals(clears + 1, plugin.clears);
        now.addAndGet(1200);
        presentation.updateSpells();
        assertTrue(plugin.detonations.isEmpty());
        assertFalse(presentation.isWarningTile(target));
        assertFalse(presentation.isArenaBuilt());
    }

    @Test
    public void rebuildingArenaCancelsOldCasts()
    {
        presentation.buildPrototypeArena(target);
        presentation.castSpell(target);
        presentation.buildPrototypeArena(new WorldPoint(3300, 3300, 0));
        now.addAndGet(1200);
        presentation.updateSpells();
        assertTrue(plugin.detonations.isEmpty());
        assertFalse(presentation.isWarningTile(target));
    }

    private static final class RecordingPlugin extends RunePartyPlugin
    {
        private final List<WorldPoint> detonations = new ArrayList<>();
        private int clears;

        @Override
        public void triggerMageArenaSpell(WorldPoint point)
        {
            detonations.add(point);
        }

        @Override
        public void clearMageArenaSpellEffects()
        {
            clears++;
        }
    }
}
