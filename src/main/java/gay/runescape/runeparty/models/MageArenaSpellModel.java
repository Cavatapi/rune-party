package gay.runescape.runeparty.models;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.AnimationController;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/** One-shot Flames of Zamorak effects. All methods run on the client thread. */
public final class MageArenaSpellModel
{
    // Spot animation 78: model 2267, sequence 901, ambient/contrast offsets 75.
    private static final int MODEL_ID = 2267;
    private static final int ANIMATION_ID = 901;
    private final Client client;
    private final List<RuneLiteObject> active = new ArrayList<>();
    private Model model;

    public MageArenaSpellModel(Client client)
    {
        this.client = client;
    }

    public void update(boolean arenaBuilt)
    {
        active.removeIf(object -> !object.isActive());
        // Request the model during the warning stage, before the first detonation.
        if (arenaBuilt && model == null)
        {
            ModelData data = client.loadModelData(MODEL_ID);
            if (data != null)
            {
                model = data.light(64 + 75, 850 + 75, -30, -50, -30);
            }
        }
    }

    public void spawn(WorldPoint point)
    {
        if (model == null || client.getTopLevelWorldView() == null
                || point.getPlane() != client.getPlane())
        {
            return;
        }
        LocalPoint local = LocalPoint.fromWorld(client.getTopLevelWorldView(), point);
        if (local == null)
        {
            return;
        }
        RuneLiteObject object = client.createRuneLiteObject();
        object.setModel(model);
        AnimationController animation = new AnimationController(client, ANIMATION_ID);
        animation.setOnFinished(controller -> object.setActive(false));
        object.setAnimationController(animation);
        object.setLocation(local, point.getPlane());
        object.setActive(true);
        active.add(object);
    }

    public void clear()
    {
        for (RuneLiteObject object : active)
        {
            object.setActive(false);
        }
        active.clear();
        model = null;
    }
}
