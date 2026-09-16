package gay.runescape.runeparty.models;

import java.awt.Shape;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

public final class KingBooModel
{
    private static final int KING_BOO_MODEL_ID = 39681;

    private final Client client;

    private RuneLiteObject kingBooObject;
    private WorldPoint kingBooPoint;

    public KingBooModel(Client client)
    {
        this.client = client;
    }

    public void spawn(WorldPoint point)
    {
        if (point == null)
        {
            return;
        }

        Model model = client.loadModel(KING_BOO_MODEL_ID);
        if (model == null)
        {
            System.out.println("KING BOO: Model 187 failed to load");
            return;
        }

        LocalPoint localPoint = LocalPoint.fromWorld(
                client.getTopLevelWorldView(),
                point
        );

        if (localPoint == null)
        {
            System.out.println("KING BOO: LocalPoint was null for " + point);
            return;
        }

        clear();

        RuneLiteObject obj = client.createRuneLiteObject();

        obj.setModel(model);
        obj.setLocation(localPoint, point.getPlane());
        obj.setActive(true);

        kingBooObject = obj;
        kingBooPoint = point;

        System.out.println("KING BOO: Spawn successful at " + point);
    }

    public boolean isUnderMouse(Point canvasPoint)
    {
        if (canvasPoint == null)
        {
            return false;
        }

        if (kingBooObject == null || !kingBooObject.isActive())
        {
            return false;
        }

        if (kingBooPoint == null)
        {
            return false;
        }

        Model model = kingBooObject.getModel();
        LocalPoint localPoint = kingBooObject.getLocation();

        if (model == null || localPoint == null)
        {
            return false;
        }

        int height = Perspective.getTileHeight(
                client,
                localPoint,
                kingBooPoint.getPlane()
        );

        Shape clickbox = Perspective.getClickbox(
                client,
                client.getTopLevelWorldView(),
                model,
                kingBooObject.getOrientation(),
                localPoint.getX(),
                localPoint.getY(),
                height
        );

        return clickbox != null
                && clickbox.contains(canvasPoint.getX(), canvasPoint.getY());
    }

    public void clear()
    {
        if (kingBooObject != null)
        {
            kingBooObject.setActive(false);
            kingBooObject = null;
        }

        kingBooPoint = null;
    }

    public boolean isSpawned()
    {
        return kingBooObject != null && kingBooObject.isActive();
    }

    public WorldPoint getPoint()
    {
        return kingBooPoint;
    }
}