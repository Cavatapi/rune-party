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
        presentation.onRoundBegin(false);
        assertTrue(presentation.castSpell(target));
        now.addAndGet(MageArenaPresentation.WARNING_MS - 1);
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

        now.addAndGet(MageArenaPresentation.DANGER_MS - 1);
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
        presentation.onRoundBegin(false);
        assertTrue(presentation.castSpell(target));
        now.addAndGet(MageArenaPresentation.CAST_COOLDOWN_MS - 1);
        assertFalse(presentation.castSpell(target));
        now.incrementAndGet();
        assertTrue(presentation.castSpell(target));
        now.set(10000 + MageArenaPresentation.WARNING_MS);
        presentation.updateSpells();
        assertTrue(presentation.isWarningTile(target));
        assertTrue(presentation.isDangerTile(target));
        now.set(10000 + MageArenaPresentation.CAST_COOLDOWN_MS + MageArenaPresentation.WARNING_MS);
        presentation.updateSpells();
        assertEquals(2, plugin.detonations.size());
    }

    @Test
    public void expiredAndResetCastsNeverReplay()
    {
        presentation.buildPrototypeArena(target);
        presentation.onRoundBegin(false);
        assertFalse(presentation.castSpell(new WorldPoint(1, 1, 0)));
        assertTrue(presentation.castSpell(target));
        now.addAndGet(MageArenaPresentation.WARNING_MS + MageArenaPresentation.DANGER_MS);
        presentation.updateSpells();
        assertTrue(plugin.detonations.isEmpty());

        assertTrue(presentation.castSpell(target));
        int clears = plugin.clears;
        presentation.reset();
        assertEquals(clears + 1, plugin.clears);
        now.addAndGet(MageArenaPresentation.WARNING_MS);
        presentation.updateSpells();
        assertTrue(plugin.detonations.isEmpty());
        assertFalse(presentation.isWarningTile(target));
        assertFalse(presentation.isArenaBuilt());
    }

    @Test
    public void rebuildingArenaCancelsOldCasts()
    {
        presentation.buildPrototypeArena(target);
        presentation.onRoundBegin(false);
        presentation.castSpell(target);
        presentation.buildPrototypeArena(new WorldPoint(3300, 3300, 0));
        now.addAndGet(MageArenaPresentation.WARNING_MS);
        presentation.updateSpells();
        assertTrue(plugin.detonations.isEmpty());
        assertFalse(presentation.isWarningTile(target));
    }

    @Test
    public void onlyDangerStageHitsAndOnlyOncePerPlayer()
    {
        presentation.buildPrototypeArena(target);
        presentation.onRoundBegin(false);
        presentation.castSpell(target);
        assertFalse(presentation.checkPlayerHit("Dodger", target));
        now.addAndGet(MageArenaPresentation.WARNING_MS);
        assertFalse(presentation.checkPlayerHit("Dodger", new WorldPoint(3201, 3200, 0)));
        assertFalse(presentation.checkPlayerHit("Dodger", new WorldPoint(3200, 3200, 1)));
        assertFalse(presentation.checkPlayerHit(null, target));
        assertTrue(presentation.checkPlayerHit("Dodger", target));
        assertFalse(presentation.checkPlayerHit("DODGER", target));
        assertEquals("Dodger", presentation.getOutAnnouncements().get(0).getRsn());
        assertTrue(presentation.checkPlayerHit("Second", target));
        assertEquals(2, presentation.getOutAnnouncements().size());
    }

    @Test
    public void enteringLateInFlamesHitsButEnteringAfterwardDoesNot()
    {
        presentation.buildPrototypeArena(target);
        presentation.onRoundBegin(false);
        presentation.castSpell(target);
        now.addAndGet(MageArenaPresentation.WARNING_MS + MageArenaPresentation.DANGER_MS - 1);
        assertTrue(presentation.checkPlayerHit("Late", target));
        now.incrementAndGet();
        assertFalse(presentation.checkPlayerHit("Safe", target));
    }

    @Test
    public void bannerExpiresAndResetAllowsPlayerToBeHitAgain()
    {
        presentation.buildPrototypeArena(target);
        presentation.onRoundBegin(false);
        presentation.castSpell(target);
        now.addAndGet(MageArenaPresentation.WARNING_MS);
        assertTrue(presentation.checkPlayerHit("Dodger", target));
        now.addAndGet(MageArenaPresentation.OUT_BANNER_MS);
        assertTrue(presentation.getOutAnnouncements().isEmpty());
        assertFalse(presentation.recordElimination("Dodger", false));
        presentation.buildPrototypeArena(target);
        presentation.onRoundBegin(false);
        presentation.castSpell(target);
        now.addAndGet(MageArenaPresentation.WARNING_MS);
        assertTrue(presentation.checkPlayerHit("Dodger", target));
    }

    @Test
    public void replayedEliminationDoesNotShowBanner()
    {
        assertTrue(presentation.recordElimination("Dodger", true));
        assertTrue(presentation.getOutAnnouncements().isEmpty());
        assertFalse(presentation.recordElimination("Dodger", false));
    }

    @Test
    public void casterIsImmuneToOwnSpellButOtherPlayersAreNot()
    {
        presentation.buildPrototypeArena(target);
        presentation.onRoundBegin(false);
        presentation.castSpell(target, "The Mage");
        now.addAndGet(MageArenaPresentation.WARNING_MS);
        assertFalse(presentation.checkPlayerHit("the_mage", target));
        assertTrue(presentation.checkPlayerHit("Dodger", target));
        assertEquals(1, presentation.getOutAnnouncements().size());
    }

    @Test
    public void roundWaitsForBeginAndStopsAtFortyFiveSeconds()
    {
        presentation.buildPrototypeArena(target);
        assertFalse(presentation.castSpell(target));
        assertFalse(presentation.isRoundActive());
        presentation.onRoundBegin(false);
        assertEquals("Mage Arena: 45s", presentation.getTimerText());
        now.addAndGet(44000);
        assertEquals("Mage Arena: 1s", presentation.getTimerText());
        assertTrue(presentation.castSpell(target));
        now.addAndGet(1000);
        assertFalse(presentation.castSpell(target));
        assertFalse(presentation.checkPlayerHit("Dodger", target));
        assertFalse(presentation.isDangerTile(target));
        int clears = plugin.clears;
        presentation.updateSpells();
        presentation.updateSpells();
        assertEquals(clears + 1, plugin.clears);
        assertEquals("Mage Arena: Time's up!", presentation.getTimerText());
    }

    @Test
    public void durationChangesApplyOnlyToNextRound()
    {
        java.util.concurrent.atomic.AtomicInteger seconds = new java.util.concurrent.atomic.AtomicInteger(45);
        MageArenaPresentation timed = new MageArenaPresentation(plugin, now::get, seconds::get);
        timed.buildPrototypeArena(target);
        timed.onRoundBegin(false);
        seconds.set(60);
        assertEquals("Mage Arena: 45s", timed.getTimerText());
        timed.onRoundBegin(false);
        assertEquals("Mage Arena: 60s", timed.getTimerText());
    }

    @Test
    public void eliminatingAllDodgersEndsRoundWithMageWin()
    {
        presentation.buildPrototypeArena(target);
        presentation.setPrototypeDodgers(java.util.Arrays.asList("Mage", "One", "Two"), "Mage");
        presentation.onRoundBegin(false);
        presentation.castSpell(target, "Mage");
        now.addAndGet(MageArenaPresentation.WARNING_MS);
        assertTrue(presentation.checkPlayerHit("One", target));
        assertTrue(presentation.isRoundActive());
        assertTrue(presentation.checkPlayerHit("Two", target));
        assertTrue(presentation.isRoundFinished());
        assertEquals("Mage wins!", presentation.getTimerText());
        assertEquals(1, presentation.getRewardShare("Mage"), 0);
        assertEquals(0, presentation.getRewardShare("One"), 0);
    }

    @Test
    public void survivorsWinAtTimeoutWithHalfRewardForEliminatedDodgers()
    {
        presentation.buildPrototypeArena(target);
        presentation.setPrototypeDodgers(java.util.Arrays.asList("One", "Two"), "Mage");
        presentation.onRoundBegin(false);
        presentation.castSpell(target, "Mage");
        now.addAndGet(MageArenaPresentation.WARNING_MS);
        assertTrue(presentation.checkPlayerHit("One", target));
        assertFalse(presentation.checkPlayerHit("Spectator", target));
        now.set(10000 + 45000);
        assertEquals("Players win!", presentation.getTimerText());
        assertEquals(1, presentation.getRewardShare("Two"), 0);
        assertEquals(0.5, presentation.getRewardShare("One"), 0);
        assertEquals(0, presentation.getRewardShare("Mage"), 0);
        assertFalse(presentation.recordElimination("Two", false));
        assertEquals("Players win!", presentation.getTimerText());
    }

    @Test
    public void generatesAllPlayerCountTiersWithCorrectBounds()
    {
        int[][] cases = {{2,4,4}, {3,6,5}, {4,6,5}, {5,7,7}, {6,7,7}, {7,8,8}, {8,8,8}};
        for (int[] test : cases)
        {
            presentation.buildPrototypeArena(target, test[0]);
            assertTrue(presentation.isArenaBuilt());
            assertEquals(test[1], presentation.getArenaSize().getWidth());
            assertEquals(test[2], presentation.getArenaSize().getHeight());
            assertEquals(test[1] * test[2], presentation.getArenaTiles().size());
            assertEquals(presentation.getArenaTiles().size(), new java.util.HashSet<>(presentation.getArenaTiles()).size());
            assertTrue(presentation.isArenaTile(new WorldPoint(3200 + test[1] - 1, 3200 + test[2] - 1, 0)));
            assertFalse(presentation.isArenaTile(new WorldPoint(3200 + test[1], 3200, 0)));
            assertFalse(presentation.isArenaTile(new WorldPoint(3200, 3200 + test[2], 0)));
        }
    }

    @Test
    public void sizeIsBoundedAndShrinkingRemovesOldTiles()
    {
        presentation.buildPrototypeArena(target, Integer.MAX_VALUE);
        assertEquals(64, presentation.getArenaTiles().size());
        presentation.buildPrototypeArena(target, 0);
        assertEquals(16, presentation.getArenaTiles().size());
        assertFalse(presentation.isArenaTile(new WorldPoint(3207, 3207, 0)));
        presentation.buildPrototypeArena(null, 8);
        assertFalse(presentation.isArenaBuilt());
        assertTrue(presentation.getArenaTiles().isEmpty());
    }

    @Test
    public void emptyDodgerRosterDoesNotAdmitSpectators()
    {
        presentation.buildPrototypeArena(target, 2);
        presentation.setPrototypeDodgers(java.util.Collections.singletonList("Mage"), "Mage");
        presentation.onRoundBegin(false);
        presentation.castSpell(target, "Mage");
        now.addAndGet(MageArenaPresentation.WARNING_MS);
        assertFalse(presentation.checkPlayerHit("Spectator", target));
        assertTrue(presentation.getOutAnnouncements().isEmpty());
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
