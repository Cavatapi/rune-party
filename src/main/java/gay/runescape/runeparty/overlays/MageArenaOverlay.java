package gay.runescape.runeparty.overlays;

import gay.runescape.runeparty.minigames.MageArenaPresentation;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;

public class MageArenaOverlay extends Overlay
{
    private static final Color ARENA_BORDER =
            new Color(255, 140, 0, 230);

    private static final Color WARNING_FILL =
            new Color(255, 220, 0, 110);

    private static final Color WARNING_BORDER =
            new Color(255, 230, 0, 255);

    private static final Color DANGER_FILL =
            new Color(220, 40, 40, 150);

    private static final Color DANGER_BORDER =
            new Color(255, 40, 40, 255);

    private final Client client;

    private MageArenaPresentation presentation;

    @Inject
    public MageArenaOverlay(Client client)
    {
        this.client = client;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    public void setPresentation(MageArenaPresentation presentation)
    {
        this.presentation = presentation;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (presentation == null || !presentation.isArenaBuilt())
        {
            return null;
        }

        presentation.updateSpells();

        // Draw ONE outside orange perimeter.
        drawArenaPerimeter(graphics);

        // Only targeted tiles get individually drawn.
        for (WorldPoint point : presentation.getArenaTiles())
        {
            if (presentation.isDangerTile(point))
            {
                drawFilledTile(
                        graphics,
                        point,
                        DANGER_FILL,
                        DANGER_BORDER
                );
            }
            else if (presentation.isWarningTile(point))
            {
                drawFilledTile(
                        graphics,
                        point,
                        WARNING_FILL,
                        WARNING_BORDER
                );
            }
        }

        return null;
    }

    private void drawArenaPerimeter(Graphics2D graphics)
    {
        if (presentation.getArenaTiles().isEmpty())
        {
            return;
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (WorldPoint point : presentation.getArenaTiles())
        {
            minX = Math.min(minX, point.getX());
            maxX = Math.max(maxX, point.getX());
            minY = Math.min(minY, point.getY());
            maxY = Math.max(maxY, point.getY());
        }

        Stroke oldStroke = graphics.getStroke();
        Color oldColor = graphics.getColor();

        graphics.setStroke(new BasicStroke(2.0f));
        graphics.setColor(ARENA_BORDER);

        for (WorldPoint point : presentation.getArenaTiles())
        {
            Polygon polygon = getTilePolygon(point);

            if (polygon == null || polygon.npoints < 4)
            {
                continue;
            }

            /*
             * RuneLite tile polygons normally contain four points.
             *
             * Instead of drawing the whole polygon, we only draw edges
             * belonging to tiles on the outside of the 6x5 arena.
             */

            if (point.getY() == minY)
            {
                drawEdge(graphics, polygon, 0, 1);
            }

            if (point.getX() == maxX)
            {
                drawEdge(graphics, polygon, 1, 2);
            }

            if (point.getY() == maxY)
            {
                drawEdge(graphics, polygon, 2, 3);
            }

            if (point.getX() == minX)
            {
                drawEdge(graphics, polygon, 3, 0);
            }
        }

        graphics.setStroke(oldStroke);
        graphics.setColor(oldColor);
    }

    private void drawEdge(
            Graphics2D graphics,
            Polygon polygon,
            int first,
            int second)
    {
        if (first >= polygon.npoints || second >= polygon.npoints)
        {
            return;
        }

        graphics.drawLine(
                polygon.xpoints[first],
                polygon.ypoints[first],
                polygon.xpoints[second],
                polygon.ypoints[second]
        );
    }

    private void drawFilledTile(
            Graphics2D graphics,
            WorldPoint worldPoint,
            Color fillColor,
            Color borderColor)
    {
        Polygon polygon = getTilePolygon(worldPoint);

        if (polygon == null)
        {
            return;
        }

        graphics.setColor(fillColor);
        graphics.fillPolygon(polygon);

        OverlayUtil.renderPolygon(
                graphics,
                polygon,
                borderColor
        );
    }

    private Polygon getTilePolygon(WorldPoint worldPoint)
    {
        if (worldPoint == null)
        {
            return null;
        }

        LocalPoint localPoint =
                LocalPoint.fromWorld(client, worldPoint);

        if (localPoint == null)
        {
            return null;
        }

        return Perspective.getCanvasTilePoly(
                client,
                localPoint
        );
    }
}