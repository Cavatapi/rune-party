package gay.runescape.runeparty.overlays;

import gay.runescape.runeparty.minigames.MageArenaPresentation;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.awt.RenderingHints;

public class MageArenaOverlay extends Overlay
{
    private static final Color ARENA_BORDER =
            new Color(255, 140, 0, 230);

    private static final int SHADOW_SEGMENTS = 48;
    private static final double SHADOW_RADIUS = Perspective.LOCAL_TILE_SIZE * 0.44;

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

        // Draw ONE outside orange perimeter.
        drawArenaPerimeter(graphics);

        // Only targeted tiles get individually drawn.
        for (WorldPoint point : presentation.getArenaTiles())
        {
            if (presentation.isWarningTile(point))
            {
                drawWarningShadow(graphics, point);
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

    private void drawWarningShadow(Graphics2D graphics, WorldPoint worldPoint)
    {
        if (worldPoint.getPlane() != client.getPlane())
        {
            return;
        }
        LocalPoint center = LocalPoint.fromWorld(client, worldPoint);
        if (center == null)
        {
            return;
        }

        Graphics2D shadow = (Graphics2D) graphics.create();
        try
        {
            shadow.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Nested translucent circles give the shadow a soft edge. Project the circle
            // from the ground, so it follows camera pitch/rotation and terrain height.
            for (int layer = 0; layer < 6; layer++)
            {
                double radius = SHADOW_RADIUS * (1.0 - layer * 0.09);
                Polygon circle = new Polygon();
                for (int segment = 0; segment < SHADOW_SEGMENTS; segment++)
                {
                    double angle = 2.0 * Math.PI * segment / SHADOW_SEGMENTS;
                    LocalPoint edge = new LocalPoint(
                            center.getX() + (int) Math.round(Math.cos(angle) * radius),
                            center.getY() + (int) Math.round(Math.sin(angle) * radius));
                    net.runelite.api.Point projected = Perspective.localToCanvas(client, edge, worldPoint.getPlane());
                    if (projected == null)
                    {
                        return;
                    }
                    circle.addPoint(projected.getX(), projected.getY());
                }
                shadow.setColor(new Color(0, 0, 0, 28));
                shadow.fillPolygon(circle);
            }
        }
        finally
        {
            shadow.dispose();
        }
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
