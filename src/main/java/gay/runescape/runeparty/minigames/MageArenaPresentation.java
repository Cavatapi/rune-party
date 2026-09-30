package gay.runescape.runeparty.minigames;

import gay.runescape.runeparty.RunePartyPlugin;
import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.function.LongSupplier;
import java.util.function.IntSupplier;

public final class MageArenaPresentation implements MinigamePresentationFeature
{
    public static final int MAX_ARENA_SIDE = 8;

    /** Tune the four playtest sizes here. Player counts include the mage. */
    public enum ArenaSize
    {
        TWO_PLAYERS(4, 4),
        THREE_TO_FOUR_PLAYERS(6, 5),
        FIVE_TO_SIX_PLAYERS(7, 7),
        SEVEN_TO_EIGHT_PLAYERS(8, 8);

        private final int width;
        private final int height;

        ArenaSize(int width, int height)
        {
            if (width < 1 || height < 1 || width > MAX_ARENA_SIDE || height > MAX_ARENA_SIDE)
            {
                throw new IllegalArgumentException("Mage Arena dimensions must be between 1 and 8");
            }
            this.width = width;
            this.height = height;
        }

        public int getWidth() { return width; }
        public int getHeight() { return height; }

        public static ArenaSize forPlayerCount(int players)
        {
            if (players <= 2) return TWO_PLAYERS;
            if (players <= 4) return THREE_TO_FOUR_PLAYERS;
            if (players <= 6) return FIVE_TO_SIX_PLAYERS;
            return SEVEN_TO_EIGHT_PLAYERS;
        }
    }

    public static final long CAST_COOLDOWN_MS = 600;

    // Ground shadow before detonation.
    public static final long WARNING_MS = 600;

    // Damage window; the detonation animation plays once to completion.
    public static final long DANGER_MS = 600;
    public static final long OUT_BANNER_MS = 2500;

    private final RunePartyPlugin plugin;
    private final LongSupplier clock;
    private final IntSupplier durationSeconds;

    private final List<WorldPoint> arenaTiles = new ArrayList<>();
    private ArenaSize arenaSize;
    private int arenaPlayerCount;
    private final List<SpellCast> activeSpells = new ArrayList<>();
    private final Map<String, OutAnnouncement> eliminatedPlayers = new LinkedHashMap<>();

    private volatile long roundStartAt = 0;
    private volatile long roundEndsAt = 0;
    private boolean roundEnded;
    private final Set<String> dodgers = new HashSet<>();
    private boolean hasDodgerRoster;
    private boolean mageWon;
    private String mageRsn;

    /** Freeze the standalone roster before its first cast; late arrivals are spectators. */
    public synchronized void setPrototypeDodgers(List<String> rsns, String casterRsn)
    {
        if (roundEndsAt != 0) return;
        hasDodgerRoster = true;
        mageRsn = casterRsn == null ? null : normalizeRsn(casterRsn);
        dodgers.clear();
        for (String rsn : rsns)
        {
            if (rsn != null && !rsn.trim().isEmpty()
                    && (casterRsn == null || !normalizeRsn(rsn).equals(normalizeRsn(casterRsn))))
            {
                dodgers.add(normalizeRsn(rsn));
            }
        }
    }
    private volatile long lastCastAt = 0;

    public MageArenaPresentation(RunePartyPlugin plugin)
    {
        this(plugin, System::currentTimeMillis, plugin::getMageArenaDurationSeconds);
    }

    MageArenaPresentation(RunePartyPlugin plugin, LongSupplier clock)
    {
        this(plugin, clock, () -> 45);
    }

    MageArenaPresentation(RunePartyPlugin plugin, LongSupplier clock, IntSupplier durationSeconds)
    {
        this.plugin = plugin;
        this.clock = clock;
        this.durationSeconds = durationSeconds;
    }

    public synchronized void buildPrototypeArena(WorldPoint anchor)
    {
        buildPrototypeArena(anchor, 4);
    }

    public synchronized void buildPrototypeArena(WorldPoint anchor, int playerCount)
    {
        reset();

        if (anchor == null)
        {
            return;
        }

        arenaPlayerCount = Math.max(2, Math.min(8, playerCount));
        arenaSize = ArenaSize.forPlayerCount(arenaPlayerCount);
        for (int y = 0; y < arenaSize.getHeight(); y++)
        {
            for (int x = 0; x < arenaSize.getWidth(); x++)
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
        return castSpell(target, null);
    }

    public synchronized boolean castSpell(WorldPoint target, String casterRsn)
    {
        if (target == null || !isArenaTile(target) || !isRoundActive())
        {
            return false;
        }

        long now = clock.getAsLong();

        if (lastCastAt != 0 && now - lastCastAt < CAST_COOLDOWN_MS)
        {
            return false;
        }

        lastCastAt = now;
        activeSpells.add(new SpellCast(target, now, casterRsn));

        return true;
    }

    public synchronized void updateSpells()
    {
        long now = clock.getAsLong();
        if (isRoundFinished())
        {
            if (!roundEnded)
            {
                roundEnded = true;
                activeSpells.clear();
                plugin.clearMageArenaSpellEffects();
            }
            return;
        }

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
        if (point == null || isRoundFinished())
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
        if (point == null || isRoundFinished())
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

    /** Local tile check only: no position polling or network request. Returns true once
     * per player per round, so the caller can send a single elimination report. */
    public synchronized boolean checkPlayerHit(String rsn, WorldPoint point)
    {
        if (rsn == null || rsn.trim().isEmpty() || point == null || !isRoundActive())
        {
            return false;
        }
        if (hasDodgerRoster && !dodgers.contains(normalizeRsn(rsn))) return false;
        long now = clock.getAsLong();
        for (SpellCast spell : activeSpells)
        {
            if (spell.target.equals(point) && now >= spell.getDetonationAt()
                    && now < spell.getFinishedAt()
                    && (spell.casterRsn == null || !normalizeRsn(rsn).equals(normalizeRsn(spell.casterRsn))))
            {
                return recordElimination(rsn, false);
            }
        }
        return false;
    }

    /** Can also fold a confirmed elimination without replaying its banner on reconnect. */
    public synchronized boolean recordElimination(String rsn, boolean catchingUp)
    {
        if (rsn == null || rsn.trim().isEmpty() || isRoundFinished()) return false;
        String name = rsn.replace('\u00a0', ' ').trim();
        String key = normalizeRsn(name);
        if (eliminatedPlayers.containsKey(key)) return false;
        eliminatedPlayers.put(key, new OutAnnouncement(name, catchingUp ? 0 : clock.getAsLong() + OUT_BANNER_MS));
        if (!dodgers.isEmpty() && eliminatedPlayers.keySet().containsAll(dodgers)) mageWon = true;
        return true;
    }

    private static String normalizeRsn(String rsn)
    {
        return rsn.replace('\u00a0', ' ').replace('_', ' ').trim().toLowerCase(Locale.ROOT);
    }

    public synchronized List<OutAnnouncement> getOutAnnouncements()
    {
        long now = clock.getAsLong();
        List<OutAnnouncement> visible = new ArrayList<>();
        for (OutAnnouncement announcement : eliminatedPlayers.values())
        {
            if (now < announcement.getUntil()) visible.add(announcement);
        }
        return visible;
    }

    @Override
    public void onStarted(boolean catchingUp)
    {
        reset();
    }

    @Override
    public synchronized void onRoundBegin(boolean catchingUp)
    {
        startRound(clock.getAsLong());
    }

    private void startRound(long now)
    {
        activeSpells.clear();
        eliminatedPlayers.clear();
        lastCastAt = 0;
        roundStartAt = now;
        roundEndsAt = now + Math.max(1, Math.min(3600, durationSeconds.getAsInt())) * 1000L;
        roundEnded = false;
        mageWon = false;
        plugin.clearMageArenaSpellEffects();
    }

    @Override
    public synchronized void reset()
    {
        arenaTiles.clear();
        arenaSize = null;
        arenaPlayerCount = 0;
        activeSpells.clear();
        eliminatedPlayers.clear();
        roundStartAt = 0;
        roundEndsAt = 0;
        roundEnded = false;
        mageWon = false;
        dodgers.clear();
        mageRsn = null;
        hasDodgerRoster = false;
        lastCastAt = 0;
        plugin.clearMageArenaSpellEffects();
    }

    public synchronized List<WorldPoint> getArenaTiles()
    {
        return Collections.unmodifiableList(new ArrayList<>(arenaTiles));
    }

    public synchronized boolean isArenaBuilt()
    {
        return arenaSize != null && arenaTiles.size() == arenaSize.getWidth() * arenaSize.getHeight();
    }

    public synchronized int getArenaPlayerCount() { return arenaPlayerCount; }
    public synchronized ArenaSize getArenaSize() { return arenaSize; }

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
        return roundEndsAt != 0 && !isRoundFinished();
    }

    public boolean isRoundFinished()
    {
        return roundEndsAt != 0 && (mageWon || clock.getAsLong() >= roundEndsAt);
    }

    public synchronized String getTimerText()
    {
        if (!isArenaBuilt()) return null;
        if (roundEndsAt == 0) return "Mage Arena: " + Math.max(1, Math.min(3600, durationSeconds.getAsInt())) + "s - Shift-click to start";
        if (isRoundFinished()) return dodgers.isEmpty() ? "Mage Arena: Time's up!"
                : mageWon ? "Mage wins!" : "Players win!";
        long seconds = (roundEndsAt - clock.getAsLong() + 999) / 1000;
        return "Mage Arena: " + Math.max(0, seconds) + "s";
    }

    /** Relative payout only. The backend still owns actual coin amounts and rounding. */
    public synchronized double getRewardShare(String rsn)
    {
        if (rsn == null || !isRoundFinished() || dodgers.isEmpty()) return 0;
        String key = normalizeRsn(rsn);
        if (mageWon) return key.equals(mageRsn) ? 1 : 0;
        if (!dodgers.contains(key)) return 0;
        return eliminatedPlayers.containsKey(key) ? 0.5 : 1;
    }

    public RunePartyPlugin getPlugin()
    {
        return plugin;
    }

    public static final class OutAnnouncement
    {
        private final String rsn;
        private final long until;

        private OutAnnouncement(String rsn, long until)
        {
            this.rsn = rsn;
            this.until = until;
        }

        public String getRsn() { return rsn; }
        public long getUntil() { return until; }
    }

    private static final class SpellCast
    {
        private final WorldPoint target;
        private final long castAt;
        private final String casterRsn;
        private boolean detonated;

        private SpellCast(WorldPoint target, long castAt, String casterRsn)
        {
            this.target = target;
            this.castAt = castAt;
            this.casterRsn = casterRsn;
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
