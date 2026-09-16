package gay.runescape.runeparty.overlays;

import gay.runescape.runeparty.GamePhase;
import gay.runescape.runeparty.RosterReducer;
import gay.runescape.runeparty.RunePartyPlugin;

import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;

/** Shared visual chrome for a one-off NPC "conversation" drawn over the chatbox -- chathead,
 * speaker name, background, wrapped body text, clickable option rows, and the click-capture/
 * reset-on-new-encounter bookkeeping every such dialogue needs. Extracted from
 * WiseOldManDialogueOverlay (the first of these) once ItemShopDialogueOverlay needed the exact
 * same chrome around a different body -- see those two for the concrete subclasses, and add a new
 * one here rather than copy-pasting either the same way ItemShopDialogueOverlay itself once was.
 * <p>
 * Drawn rather than driven through the game's real dialog interface, same reasoning the Follower
 * Buddy plugin's own FollowerDialog gives (https://github.com/MikeSpatol/follower-buddy): the real
 * dialog widgets only exist while the game itself has a conversation open, and no script exists
 * for a plugin to open one on demand. The chathead is genuine even so -- ChatheadRenderer projects
 * the NPC's own real chathead model (adapted from that same plugin's own ChatheadRenderer/
 * GouraudRasterizer/GameColourTable).
 * <p>
 * Colors and the "no drop shadow" choice below also follow that same plugin's own FollowerDialog:
 * a dark red speaker name, black body text (readable against the real chatbox sprite's light
 * backdrop -- CHATBOX_SPRITE_ID below is the exact same sprite, 1017, FollowerDialog's own
 * doc identifies as "the old api SpriteID.CHATBOX"), blue unselected options turning white on
 * hover, no shadow under any of it ("dialog text on parchment has none in the real client, and a
 * shadow visibly fattens the glyphs" -- FollowerDialog's own doc). This replaces an earlier,
 * unauthenticated palette (orange name/white body/lavender options) that read poorly against that
 * same sprite -- white body text over a light parchment backdrop is nearly illegible. Follower
 * Buddy itself goes one step further still, rendering the game's own real bitmap dialogue font
 * (a raw font-table dump, font id 497) rather than an AWT approximation -- genuinely pixel-exact,
 * but a much larger undertaking (a font-dump reader, not just a color/layout change) than this
 * project has needed so far; FontManager's own bundled RuneScape-styled fonts are what Follower
 * Buddy itself falls back to whenever its own font dump isn't loaded yet, so this is the same
 * fallback path, just used as the only path here.
 * <p>
 * A subclass supplies: which NPC's chathead/name to show (getNpcId/getSpeakerName), which
 * Presentation-backed encounterRsn/revealAt gate the box open (getEncounterRsn/getRevealAt), and
 * everything drawn below the name (drawBody) -- typically ending in a call to drawOptionRows
 * (plain single-line rows) and/or a subclass's own custom row-drawing for anything fancier (see
 * ItemShopDialogueOverlay's own drawItemRows for a two-line-per-entry example), both of which
 * publish into the same optionBounds/optionCallbacks this class's own click handling reads. */
public abstract class ChatboxDialogueOverlay extends Overlay
{
    protected static final int CHATHEAD_SIZE = 130;
    private static final int CHATBOX_SPRITE_ID = 1017; // the real client's own chatbox background sprite
    // Real widget bounds when available (see computeBounds) -- this fallback only covers the rare
    // frame it isn't (not yet loaded, or hidden). 519x165 is the real chatbox's own fixed-layout
    // size; BUTTON_STRIP (matching Follower Buddy's own FollowerDialog#BUTTON_STRIP) is the strip
    // along its bottom edge -- the filter/report/friend-chat button row -- that both the real
    // dialog interface and this one have to stop short of rather than draw over.
    private static final int FALLBACK_WIDTH = 519;
    private static final int FALLBACK_HEIGHT = 165;
    private static final int BUTTON_STRIP = 23;
    protected static final int TEXT_LEFT = 140; // clears the chathead portrait on the left
    protected static final int TEXT_RIGHT_MARGIN = 20;
    private static final int NAME_TOP_OFFSET = 22;
    protected static final int BODY_TOP_OFFSET = 44;
    protected static final int OPTION_ROW_HEIGHT = 18;
    protected static final int OPTIONS_TOP_OFFSET = 78;

    // See this class's own doc for why these match Follower Buddy's own FollowerDialog palette.
    private static final Color PARCHMENT = new Color(0xc8, 0xb8, 0x8f);
    private static final Color BORDER = new Color(0, 0, 0);
    protected static final Color NAME_COLOR = new Color(0x80, 0x00, 0x00);
    protected static final Color BODY_COLOR = Color.BLACK;
    protected static final Color OPTION_COLOR = new Color(0x00, 0x00, 0xff);
    protected static final Color OPTION_HOVER_COLOR = Color.WHITE;

    protected final Client client;
    protected final RunePartyPlugin plugin;
    protected final RosterReducer roster;
    private final MouseManager mouseManager;
    private final SpriteManager spriteManager;

    // All fields below are touched from both the client thread (render(), every frame) and the
    // AWT event thread (clickAdapter, on a real mouse click) -- volatile (or, for the two arrays,
    // a fresh array swapped in atomically each frame) rather than synchronized: a click landing
    // one frame stale just means it's ignored or acts on the previous frame's geometry, never a
    // torn read.
    private volatile boolean open = false;
    // The last encounterRsn actually seen (including null) -- see render()'s own doc for why local
    // UI state resets exactly when this changes, not whenever the box merely isn't open.
    private volatile String lastSeenEncounterRsn = null;
    protected volatile boolean submitted = false; // set the instant a final choice goes out
    protected volatile Rectangle bounds = new Rectangle();
    protected volatile Rectangle[] optionBounds = new Rectangle[0];
    protected volatile Runnable[] optionCallbacks = new Runnable[0];
    // Deferred click action, consumed on the very next render() call (client thread) rather than
    // run directly inside mousePressed (AWT thread) -- same safety idiom FollowerDialog's own
    // pendingAction field follows, since a subclass's own submit call has no business running off
    // the client thread.
    private volatile Runnable pendingClick;

    // This NPC's own chathead portrait, rendered once and cached forever -- its model never
    // changes, unlike a custom composed appearance that can animate its own jaw mid-line (see
    // ChatheadRenderer's own doc for why the plugin this was adapted from can't just do this).
    private BufferedImage chatheadImage;

    private final MouseAdapter clickAdapter = new MouseAdapter()
    {
        @Override
        public MouseEvent mousePressed(MouseEvent event)
        {
            if (!open || !SwingUtilities.isLeftMouseButton(event)) return event;
            if (!bounds.contains(event.getPoint())) return event; // outside the box -- let it pass through untouched

            Rectangle[] rects = optionBounds;
            Runnable[] callbacks = optionCallbacks;
            for (int i = 0; i < rects.length; i++)
            {
                if (rects[i] != null && rects[i].contains(event.getPoint()) && callbacks[i] != null)
                {
                    pendingClick = callbacks[i];
                    break;
                }
            }

            // Consumed either way -- the whole box is a dead zone for the game's own click-to-walk
            // underneath it, same as the real dialog interface is.
            event.consume();
            return event;
        }
    };

    protected ChatboxDialogueOverlay(Client client, RunePartyPlugin plugin, MouseManager mouseManager, SpriteManager spriteManager, RosterReducer roster)
    {
        this.client = client;
        this.plugin = plugin;
        this.mouseManager = mouseManager;
        this.spriteManager = spriteManager;
        this.roster = roster;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    public void register()
    {
        mouseManager.registerMouseListener(clickAdapter);
    }

    public void unregister()
    {
        mouseManager.unregisterMouseListener(clickAdapter);
    }

    /** The rsn currently mid-encounter for this dialogue, or null -- backed by this dialogue's own
     * Presentation class (e.g. WiseOldManPresentation#getEncounterRsn). */
    protected abstract String getEncounterRsn();

    /** When this dialogue is allowed to actually start showing -- backed by this dialogue's own
     * Presentation class (e.g. WiseOldManPresentation#getRevealAt()). */
    protected abstract long getRevealAt();

    /** This NPC's own id, for its chathead portrait here (see the paired *NpcOverlay for the same
     * id's other use, the in-world model). */
    protected abstract int getNpcId();

    /** The speaker name drawn at the top of the box. */
    protected abstract String getSpeakerName();

    /** Draws everything below the name -- the greeting/prompt text and whatever clickable rows
     * this screen needs -- and publishes their hit-boxes into optionBounds/optionCallbacks (see
     * drawOptionRows below, or a subclass's own row-drawing for anything fancier). Called only
     * once shouldBeOpen is confirmed true and background/chathead/name are already drawn. `self`
     * is the local player's own rsn (never null when this is called). */
    protected abstract void drawBody(Graphics2D g, String self);

    /** Local UI state to reset the instant a genuinely new encounter opens (or none at all
     * anymore) -- see render()'s own doc for why this is keyed off getEncounterRsn() itself
     * changing, not whenever the box merely isn't open. Base already resets submitted/
     * optionBounds/optionCallbacks/pendingClick; override to reset any subclass-specific screen/
     * selection state on top, calling super first. */
    protected void resetForNextEncounter()
    {
        submitted = false;
        pendingClick = null;
        optionBounds = new Rectangle[0];
        optionCallbacks = new Runnable[0];
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        String self = plugin.getLocalRsn();
        String encounterRsn = getEncounterRsn();

        // Fresh local UI state exactly when the real encounterRsn itself changes -- a genuinely
        // new encounter opening, or none at all anymore. Deliberately NOT keyed off
        // shouldBeOpen/open below: those also go false the instant a final choice is submitted,
        // well before the server's own confirming dismissal event actually lands and changes
        // encounterRsn -- resetting `submitted` on that transition instead would flip it back to
        // false while encounterRsn is still the local player's own, reopening the box for one or
        // more stray frames until the real dismissal finally arrives.
        if (!Objects.equals(encounterRsn, lastSeenEncounterRsn))
        {
            lastSeenEncounterRsn = encounterRsn;
            resetForNextEncounter();
        }

        boolean shouldBeOpen = plugin.getPhase() == GamePhase.ACTIVE
            && self != null && self.equalsIgnoreCase(encounterRsn)
            && !submitted
            && System.currentTimeMillis() >= getRevealAt();

        open = shouldBeOpen;
        if (!shouldBeOpen)
        {
            return null;
        }

        Runnable click = pendingClick;
        pendingClick = null;
        if (click != null) click.run();
        if (submitted) return null; // the click above just submitted a final choice -- nothing left to draw this frame

        bounds = computeBounds();

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawBackground(g);
        drawChathead(g);
        drawName(g);
        drawBody(g, self);

        return null;
    }

    /** The real chatbox widget's own current on-screen rectangle, minus BUTTON_STRIP off its
     * bottom edge -- adapts automatically to whichever layout (fixed/resizable, classic/modern,
     * with or without the side panels open) the player's actually using, same reasoning
     * FollowerDialog's own doc gives for checking the real widget first rather than always trusting
     * a fixed guess. Falls back to that same fixed guess only on the rare frame the real widget
     * isn't available at all (not loaded yet, or hidden -- e.g. the player's closed the chat
     * panel). */
    private Rectangle computeBounds()
    {
        Widget chat = client.getWidget(InterfaceID.CHATBOX, 0);
        Rectangle area = chat != null && !chat.isHidden()
            ? chat.getBounds()
            : new Rectangle(0, client.getCanvasHeight() - FALLBACK_HEIGHT, FALLBACK_WIDTH, FALLBACK_HEIGHT);
        return new Rectangle(area.x, area.y, area.width, Math.max(0, area.height - BUTTON_STRIP));
    }

    private void drawBackground(Graphics2D g)
    {
        BufferedImage sprite = spriteManager.getSprite(CHATBOX_SPRITE_ID, 0);
        if (sprite != null)
        {
            g.drawImage(sprite, bounds.x, bounds.y, bounds.width, bounds.height, null);
        }
        else
        {
            g.setColor(PARCHMENT);
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }
        g.setColor(BORDER);
        g.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
    }

    private BufferedImage chathead()
    {
        if (chatheadImage != null) return chatheadImage;
        Model model = RunePartyRender.loadNpcChatheadModel(client, getNpcId());
        if (model == null) return null; // not cached yet -- keep retrying every frame until it resolves
        chatheadImage = ChatheadRenderer.render(model, CHATHEAD_SIZE, CHATHEAD_SIZE);
        return chatheadImage;
    }

    private void drawChathead(Graphics2D g)
    {
        BufferedImage face = chathead();
        if (face == null) return;
        g.drawImage(face, bounds.x + 4, bounds.y + 2, null);
    }

    private void drawName(Graphics2D g)
    {
        g.setFont(FontManager.getRunescapeBoldFont());
        g.setColor(NAME_COLOR);
        g.drawString(getSpeakerName(), bounds.x + TEXT_LEFT, bounds.y + NAME_TOP_OFFSET);
    }

    /** Draws one clickable row per (label, callback) pair, top to bottom starting at {@code top},
     * and publishes their hit-boxes/callbacks (as a single atomic array swap each) for the next
     * mouse click to test against -- see clickAdapter's own doc for why this is a fresh array
     * rather than a mutation of the previous frame's. */
    protected void drawOptionRows(Graphics2D g, List<String> labels, List<Runnable> callbacks, int top)
    {
        Rectangle[] rects = new Rectangle[labels.size()];
        net.runelite.api.Point mouse = client.getMouseCanvasPosition();

        Font font = FontManager.getRunescapeFont();
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();

        int y = top;
        for (int i = 0; i < labels.size(); i++)
        {
            Rectangle row = new Rectangle(bounds.x + TEXT_LEFT, y - fm.getAscent(),
                bounds.width - TEXT_LEFT - TEXT_RIGHT_MARGIN, OPTION_ROW_HEIGHT);
            rects[i] = row;

            boolean hovered = mouse != null && row.contains(mouse.getX(), mouse.getY());
            g.setColor(hovered ? OPTION_HOVER_COLOR : OPTION_COLOR);
            g.drawString(labels.get(i), row.x, y);

            y += OPTION_ROW_HEIGHT;
        }

        optionBounds = rects;
        optionCallbacks = callbacks.toArray(new Runnable[0]);
    }

    /** Plain greedy word-wrap against {@code maxWidth}, drawn top-down from {@code top} -- good
     * enough for the short, fixed lines these dialogues ever show (no need for the real client's
     * own bitmap-font metrics the way a pixel-perfect port would -- see this class's own doc for
     * why that's out of scope here). */
    protected void drawWrappedText(Graphics2D g, String text, int x, int top, int maxWidth)
    {
        g.setFont(FontManager.getRunescapeFont());
        g.setColor(BODY_COLOR);
        FontMetrics fm = g.getFontMetrics();

        StringBuilder line = new StringBuilder();
        int y = top;
        for (String word : text.split(" "))
        {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(candidate) > maxWidth && line.length() > 0)
            {
                g.drawString(line.toString(), x, y);
                y += fm.getHeight();
                line = new StringBuilder(word);
            }
            else
            {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) g.drawString(line.toString(), x, y);
    }
}
