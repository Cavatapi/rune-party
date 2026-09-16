package gay.runescape.runeparty.overlays;

import gay.runescape.runeparty.RosterReducer;
import gay.runescape.runeparty.RunePartyPlugin;

import net.runelite.api.Client;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.MouseManager;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/** A conversation with the Wise Old Man, drawn over the chatbox in the game's own dialog style --
 * chathead, speaker name, greeting text, and clickable options -- for the mini-encounter opened by
 * landing on his tile (see the server's own wise_old_man_encounter_opened doc). Only ever shows
 * for the local player actually mid-encounter (see WiseOldManPresentation#getEncounterRsn) -- every
 * other seated client sees nothing of this box at all, only the eventual "X stole ... from Y!"
 * announcement once (if) it resolves as a steal (see AnnouncementOverlay#renderWiseOldManStolen).
 * All the chatbox chrome (background, chathead, name, click handling, reset-on-new-encounter
 * bookkeeping) lives in ChatboxDialogueOverlay -- see that class's own doc for the "drawn rather
 * than driven through the game's real dialog interface" reasoning and the ChatheadRenderer/
 * Follower Buddy provenance; this subclass supplies only the two screens below.
 * <p>
 * ACTION_CHOICE (the greeting + steal-coins/steal-golden-gnome/decline options -- the Golden Gnome
 * option is hidden outright when the local player can't afford it or nobody else holds one, per
 * this feature's own confirmed design) and TARGET_SELECT (every eligible other seated player as
 * its own clickable row, simply paginated if there are more than fit on one screen -- up to
 * MAX_PLAYERS - 1 candidates is small enough that this never needs to be sophisticated). Submits
 * via RunePartyPlugin#submitWiseOldManChoice the instant a target (or "Never mind") is clicked,
 * then closes itself immediately rather than waiting for the server's own confirming
 * WISE_OLD_MAN_DISMISSED -- same "set optimistically on submit" latitude
 * goldenGnomePurchasedThisTurn's own doc describes. */
public final class WiseOldManDialogueOverlay extends ChatboxDialogueOverlay
{
    private static final int TARGETS_PER_PAGE = 5;

    private enum Screen { ACTION_CHOICE, TARGET_SELECT }

    private volatile Screen screen = Screen.ACTION_CHOICE;
    private volatile String pendingActionChoice = null; // "steal_coins" | "steal_golden_gnome", set once picked on screen 1
    private volatile int targetPage = 0;

    public WiseOldManDialogueOverlay(Client client, RunePartyPlugin plugin, MouseManager mouseManager, SpriteManager spriteManager, RosterReducer roster)
    {
        super(client, plugin, mouseManager, spriteManager, roster);
    }

    @Override
    protected String getEncounterRsn() { return plugin.getWiseOldManEncounterRsn(); }

    @Override
    protected long getRevealAt() { return plugin.getWiseOldManRevealAt(); }

    @Override
    protected int getNpcId() { return RunePartyPlugin.WISE_OLD_MAN_NPC_ID; }

    @Override
    protected String getSpeakerName() { return "Wise Old Man"; }

    @Override
    protected void resetForNextEncounter()
    {
        super.resetForNextEncounter();
        screen = Screen.ACTION_CHOICE;
        pendingActionChoice = null;
        targetPage = 0;
    }

    @Override
    protected void drawBody(Graphics2D g, String self)
    {
        if (screen == Screen.ACTION_CHOICE)
        {
            drawActionChoice(g, self);
        }
        else
        {
            drawTargetSelect(g);
        }
    }

    private void drawActionChoice(Graphics2D g, String self)
    {
        int coins = roster.getCoins(self);
        boolean canAffordGnome = coins >= RunePartyPlugin.WISE_OLD_MAN_GNOME_STEAL_COST;
        boolean anyoneHasGnome = roster.seatedPlayers().stream()
            .anyMatch(p -> !p.rsn.equalsIgnoreCase(self) && p.goldenGnomeCount > 0);
        boolean canOfferGnomeSteal = canAffordGnome && anyoneHasGnome;

        String greeting = canAffordGnome
            ? "Hello! Would you like to steal coins from another player (free)? Or steal a Golden Gnome (73 coins)?"
            : "It appears you don't have enough coins to do that.";

        int textWidth = bounds.width - TEXT_LEFT - TEXT_RIGHT_MARGIN;
        drawWrappedText(g, greeting, bounds.x + TEXT_LEFT, bounds.y + BODY_TOP_OFFSET, textWidth);

        List<String> labels = new ArrayList<>();
        List<Runnable> callbacks = new ArrayList<>();

        labels.add("Steal coins from another player (free)");
        callbacks.add(() -> beginTargetSelect("steal_coins"));

        if (canOfferGnomeSteal)
        {
            labels.add("Steal a Golden Gnome (73 coins)");
            callbacks.add(() -> beginTargetSelect("steal_golden_gnome"));
        }

        labels.add("Never mind");
        callbacks.add(() -> submitFinalChoice("decline", null));

        drawOptionRows(g, labels, callbacks, bounds.y + OPTIONS_TOP_OFFSET);
    }

    private void beginTargetSelect(String action)
    {
        pendingActionChoice = action;
        screen = Screen.TARGET_SELECT;
        targetPage = 0;
    }

    private void drawTargetSelect(Graphics2D g)
    {
        String self = plugin.getLocalRsn();
        boolean gnomeSteal = "steal_golden_gnome".equals(pendingActionChoice);

        List<String> candidates = new ArrayList<>();
        for (RosterReducer.RosterEntry p : roster.seatedPlayers())
        {
            if (self != null && p.rsn.equalsIgnoreCase(self)) continue;
            if (gnomeSteal && p.goldenGnomeCount <= 0) continue;
            candidates.add(p.rsn);
        }

        String prompt = gnomeSteal ? "Steal a Golden Gnome from who?" : "Steal coins from who?";
        int textWidth = bounds.width - TEXT_LEFT - TEXT_RIGHT_MARGIN;
        drawWrappedText(g, prompt, bounds.x + TEXT_LEFT, bounds.y + BODY_TOP_OFFSET, textWidth);

        int pageCount = Math.max(1, (candidates.size() + TARGETS_PER_PAGE - 1) / TARGETS_PER_PAGE);
        int page = Math.min(targetPage, pageCount - 1);
        int start = page * TARGETS_PER_PAGE;
        int end = Math.min(candidates.size(), start + TARGETS_PER_PAGE);

        List<String> labels = new ArrayList<>();
        List<Runnable> callbacks = new ArrayList<>();

        for (int i = start; i < end; i++)
        {
            String targetRsn = candidates.get(i);
            labels.add(targetRsn);
            callbacks.add(() -> submitFinalChoice(pendingActionChoice, targetRsn));
        }

        if (candidates.isEmpty())
        {
            labels.add("(nobody eligible)");
            callbacks.add(() -> { });
        }
        else if (pageCount > 1)
        {
            labels.add((page + 1) + "/" + pageCount + " -- more...");
            int nextPage = (page + 1) % pageCount;
            callbacks.add(() -> targetPage = nextPage);
        }

        labels.add("Back");
        callbacks.add(() -> { screen = Screen.ACTION_CHOICE; pendingActionChoice = null; });

        drawOptionRows(g, labels, callbacks, bounds.y + OPTIONS_TOP_OFFSET);
    }

    private void submitFinalChoice(String action, String target)
    {
        submitted = true;
        plugin.submitWiseOldManChoice(action, target);
    }
}
