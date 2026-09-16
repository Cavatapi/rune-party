package gay.runescape.runeparty.overlays;

import gay.runescape.runeparty.RosterReducer;
import gay.runescape.runeparty.RunePartyPlugin;

import gay.runescape.runeparty.items.Item;
import gay.runescape.runeparty.items.Items;

import net.runelite.api.Client;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.FontManager;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/** A conversation with the Item Shop's own shopkeeper, drawn over the chatbox in the game's own
 * dialog style -- chathead, speaker name, greeting text, and clickable options -- for the
 * mini-encounter opened by landing on his tile (see the server's own item_shop_encounter_opened
 * doc). Only ever shows for the local player actually mid-encounter (see
 * ItemShopPresentation#getEncounterRsn) -- every other seated client sees nothing of this box at
 * all, only the eventual "You/&lt;rsn&gt; purchased/can't afford &lt;item&gt;!" announcement (see
 * AnnouncementOverlay#renderItemShopOutcome). All the chatbox chrome (background, chathead, name,
 * click handling, reset-on-new-encounter bookkeeping) lives in ChatboxDialogueOverlay -- see that
 * class's own doc; this subclass supplies only the two screens below.
 * <p>
 * GREETING (the "Would you like to buy an item?" Yes!/No thanks. choice) and ITEM_LIST
 * (RunePartyPlugin#ITEM_SHOP_CATALOG as a paginated list, each row showing name, price, and effect
 * description -- rows the local player can't currently afford, or has no inventory room for, are
 * skipped outright, same "the dialogue doesn't even offer what the server would reject anyway"
 * latitude WiseOldManDialogueOverlay's own Golden-Gnome-steal option takes). Submits via
 * RunePartyPlugin#submitItemShopChoice the instant "buy" or "No thanks." is clicked for a final
 * choice, then closes itself immediately rather than waiting for the server's own confirming
 * ITEM_SHOP_DISMISSED -- same "set optimistically on submit" latitude WiseOldManDialogueOverlay's
 * own doc describes. A failed purchase (insufficient funds/inventory full, see the server's own
 * item_shop_choose doc) does NOT submit-and-close -- the dialogue stays open on the ITEM_LIST
 * screen so the player can pick something else. */
public final class ItemShopDialogueOverlay extends ChatboxDialogueOverlay
{
    // Each item gets two lines (name/price, then its own effect description right below, dimmer)
    // rather than the single line a plain option row uses -- "a menu of items and their
    // descriptions" per this feature's own confirmed design needs more per-row room than a plain
    // label does. Both lines together are one clickable row.
    private static final int ITEM_ROW_HEIGHT = 32;
    private static final int ITEM_DESCRIPTION_OFFSET = 14; // the description line's own offset below its row's top
    private static final int ITEMS_PER_PAGE = 3;
    private static final Color ITEM_DESCRIPTION_COLOR = new Color(0x80, 0x80, 0x80);

    private enum Screen { GREETING, ITEM_LIST }

    private volatile Screen screen = Screen.GREETING;
    private volatile int itemPage = 0;

    public ItemShopDialogueOverlay(Client client, RunePartyPlugin plugin, MouseManager mouseManager, SpriteManager spriteManager, RosterReducer roster)
    {
        super(client, plugin, mouseManager, spriteManager, roster);
    }

    @Override
    protected String getEncounterRsn() { return plugin.getItemShopEncounterRsn(); }

    @Override
    protected long getRevealAt() { return plugin.getItemShopRevealAt(); }

    @Override
    protected int getNpcId() { return RunePartyPlugin.ITEM_SHOP_NPC_ID; }

    @Override
    protected String getSpeakerName() { return "Shopkeeper"; }

    @Override
    protected void resetForNextEncounter()
    {
        super.resetForNextEncounter();
        screen = Screen.GREETING;
        itemPage = 0;
    }

    @Override
    protected void drawBody(Graphics2D g, String self)
    {
        if (screen == Screen.GREETING)
        {
            drawGreeting(g);
        }
        else
        {
            drawItemList(g, self);
        }
    }

    private void drawGreeting(Graphics2D g)
    {
        int textWidth = bounds.width - TEXT_LEFT - TEXT_RIGHT_MARGIN;
        drawWrappedText(g, "Would you like to buy an item?", bounds.x + TEXT_LEFT, bounds.y + BODY_TOP_OFFSET, textWidth);

        List<String> labels = new ArrayList<>();
        List<Runnable> callbacks = new ArrayList<>();

        labels.add("Yes!");
        callbacks.add(() -> { screen = Screen.ITEM_LIST; itemPage = 0; });

        labels.add("No thanks.");
        callbacks.add(() -> submitFinalChoice("decline", null));

        drawOptionRows(g, labels, callbacks, bounds.y + OPTIONS_TOP_OFFSET);
    }

    private void drawItemList(Graphics2D g, String self)
    {
        int coins = roster.getCoins(self);
        int held = 0;
        for (int count : roster.getItems(self).values()) held += count;
        boolean hasRoom = held < RunePartyPlugin.ITEM_CAP;

        List<RunePartyPlugin.ItemShopEntry> affordable = new ArrayList<>();
        if (hasRoom)
        {
            for (RunePartyPlugin.ItemShopEntry entry : RunePartyPlugin.ITEM_SHOP_CATALOG)
            {
                if (entry.price <= coins) affordable.add(entry);
            }
        }

        int textWidth = bounds.width - TEXT_LEFT - TEXT_RIGHT_MARGIN;
        String prompt = !hasRoom ? "You're holding too many items already -- use one first!"
            : affordable.isEmpty() ? "You can't afford anything here right now."
            : "Here's what I've got:";
        drawWrappedText(g, prompt, bounds.x + TEXT_LEFT, bounds.y + BODY_TOP_OFFSET, textWidth);

        int pageCount = Math.max(1, (affordable.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        int page = Math.min(itemPage, pageCount - 1);
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(affordable.size(), start + ITEMS_PER_PAGE);

        List<String> names = new ArrayList<>();
        List<String> descriptions = new ArrayList<>();
        List<Runnable> callbacks = new ArrayList<>();

        for (int i = start; i < end; i++)
        {
            RunePartyPlugin.ItemShopEntry entry = affordable.get(i);
            Item item = Items.get(entry.itemKey);
            String displayName = item != null ? item.getDisplayName() : entry.itemKey;
            String description = item != null ? item.getEffectDescription(true) : null;

            names.add(displayName + " (" + entry.price + " coins)");
            descriptions.add(description != null ? description : "");
            callbacks.add(() -> submitFinalChoice("buy_item", entry.itemKey));
        }

        int afterItemsY = bounds.y + OPTIONS_TOP_OFFSET + names.size() * ITEM_ROW_HEIGHT;
        if (!affordable.isEmpty() && pageCount > 1)
        {
            names.add((page + 1) + "/" + pageCount + " -- more...");
            descriptions.add("");
            int nextPage = (page + 1) % pageCount;
            callbacks.add(() -> itemPage = nextPage);
        }

        names.add("Back");
        descriptions.add("");
        callbacks.add(() -> screen = Screen.GREETING);

        drawItemRows(g, names, descriptions, callbacks, bounds.y + OPTIONS_TOP_OFFSET, afterItemsY);
    }

    private void submitFinalChoice(String action, String itemKey)
    {
        submitted = true;
        plugin.submitItemShopChoice(action, itemKey);
    }

    /** Draws each (name, description, callback) triple as a two-line row -- name/price on top
     * (the clickable label, same coloring/hover drawOptionRows itself uses), its effect
     * description dimmer right below -- until {@code plainRowsFromY}, from which point on (the
     * "more.../Back" trailer rows) only a single line is drawn per entry, same shape
     * drawOptionRows itself uses. Publishes hit-boxes/callbacks the same atomic-array-swap way
     * drawOptionRows does -- see that method's own doc (ChatboxDialogueOverlay) for why. */
    private void drawItemRows(Graphics2D g, List<String> names, List<String> descriptions, List<Runnable> callbacks, int top, int plainRowsFromY)
    {
        Rectangle[] rects = new Rectangle[names.size()];
        net.runelite.api.Point mouse = client.getMouseCanvasPosition();

        Font font = FontManager.getRunescapeFont();
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();

        int y = top;
        for (int i = 0; i < names.size(); i++)
        {
            boolean isItemRow = y < plainRowsFromY;
            int rowHeight = isItemRow ? ITEM_ROW_HEIGHT : OPTION_ROW_HEIGHT;
            Rectangle row = new Rectangle(bounds.x + TEXT_LEFT, y - fm.getAscent(),
                bounds.width - TEXT_LEFT - TEXT_RIGHT_MARGIN, rowHeight);
            rects[i] = row;

            boolean hovered = mouse != null && row.contains(mouse.getX(), mouse.getY());
            g.setColor(hovered ? OPTION_HOVER_COLOR : OPTION_COLOR);
            g.drawString(names.get(i), row.x, y);

            if (isItemRow && !descriptions.get(i).isEmpty())
            {
                g.setColor(ITEM_DESCRIPTION_COLOR);
                g.drawString(descriptions.get(i), row.x, y + ITEM_DESCRIPTION_OFFSET);
            }

            y += rowHeight;
        }

        optionBounds = rects;
        optionCallbacks = callbacks.toArray(new Runnable[0]);
    }
}
