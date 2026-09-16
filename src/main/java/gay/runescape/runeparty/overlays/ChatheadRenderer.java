package gay.runescape.runeparty.overlays;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Model;

/** Renders a real NPC's own chathead {@link Model} (see RunePartyRender#loadNpcChatheadModel) to
 * an image, in software -- adapted from the Follower Buddy plugin's own com.follower.ui.
 * ChatheadRenderer (https://github.com/MikeSpatol/follower-buddy), which needs this because a
 * composed-in-memory appearance has no cacheable model id a real chathead widget could ever be
 * given. Wise Old Man doesn't have that problem -- NPC 2108 is a real, already-cached NPC with its
 * own real chathead model resource -- but the widget system still can't be handed that model
 * directly for a plugin-drawn dialogue (see WiseOldManDialogueOverlay's own doc for why the real
 * dialog interface isn't an option at all), so the same "read the model's own vertices/faces/lit
 * colours and rasterize them exactly the way the client does" approach is reused here, just
 * simplified: no head-fraction cropping or unposed-bounds framing (that exists upstream purely to
 * keep a custom composed head's own picture from rescaling as its jaw animates) -- the whole
 * chathead model IS the intended portrait here, so this always draws all of it.
 * <p>
 * Uses the client's own widget-model camera -- perspective, orbited to the dialog's own pitch at
 * its own zoom -- with painter's-algorithm depth sorting, and fills triangles through {@link
 * GouraudRasterizer} (also ported from Follower Buddy), so the pixels come out the way the game's
 * own renderer produces them. */
final class ChatheadRenderer
{
    /** The client's own NPC dialog chathead camera -- see the client's own drawInterface: an NPC
     * dialog widget's rotationX is 40 (pitch), rotationY is 0 (no yaw -- Wise Old Man faces the
     * camera square-on, exactly as his real in-game dialogue chathead does), rotationZ is 1882
     * (which drives yaw in THIS renderer's own projection, not a Z-roll -- see project's own
     * doc), and modelZoom is 796. */
    private static final int GAME_PITCH = 40;
    private static final int GAME_TURN_NPC = 1882;
    private static final int GAME_ZOOM = 796;

    /** The client's projection: screen = model * 512 / depth. */
    private static final double FOCAL_LENGTH = 512;

    private ChatheadRenderer()
    {
    }

    /** Renders {@code model} centered in a {@code width}x{@code height} image using the game's own
     * fixed NPC dialog chathead camera. Returns null if the model has no geometry to draw (should
     * only happen transiently, before the model's own data has fully loaded). */
    static BufferedImage render(Model model, int width, int height)
    {
        if (model == null || width <= 0 || height <= 0) return null;

        float[] vx = model.getVerticesX();
        float[] vy = model.getVerticesY();
        float[] vz = model.getVerticesZ();
        int[] fa = model.getFaceIndices1();
        int[] fb = model.getFaceIndices2();
        int[] fc = model.getFaceIndices3();
        int[] colors = model.getFaceColors1();
        int[] hidden = model.getFaceColors3();

        if (vx == null || vy == null || vz == null || fa == null || fb == null || fc == null || colors == null)
        {
            return null;
        }

        int verts = Math.min(model.getVerticesCount(), vx.length);

        // The client's exact widget-model camera, from its own drawInterface:
        //     eyeY = sin(rotationX) * zoom;  eyeZ = cos(rotationX) * zoom
        //     objRender(0, rotationY, 0, rotationX, 0, eyeY, eyeZ)
        // So the model is yawed, pushed out to `zoom`, and viewed by a camera ORBITED to pitch
        // rotationX -- with a perspective divide.
        double yawAngle = GAME_TURN_NPC / 2048.0 * 2.0 * Math.PI;
        double yawSin = Math.sin(yawAngle);
        double yawCos = Math.cos(yawAngle);
        double pitchAngle = GAME_PITCH / 2048.0 * 2.0 * Math.PI;
        double pitchSin = Math.sin(pitchAngle);
        double pitchCos = Math.cos(pitchAngle);
        double eyeY = pitchSin * GAME_ZOOM;
        double eyeZ = pitchCos * GAME_ZOOM;

        float[] rx = new float[verts];
        float[] ry = new float[verts];
        float[] rz = new float[verts];

        for (int i = 0; i < verts; i++)
        {
            double[] p = project(vx[i], vy[i], vz[i], yawSin, yawCos, pitchSin, pitchCos, eyeY, eyeZ);
            rx[i] = (float) p[0];
            ry[i] = (float) p[1];
            rz[i] = (float) p[2];
        }

        // Every face the client itself doesn't hide (-2 in faceColors3) -- no head-fraction
        // cropping here, unlike the upstream version this was adapted from (see this class's own
        // doc): the whole chathead model is the portrait.
        List<Integer> keep = new ArrayList<>();
        for (int i = 0; i < fa.length; i++)
        {
            if (hidden != null && i < hidden.length && hidden[i] == -2) continue;
            int a = fa[i];
            int b = fb[i];
            int c = fc[i];
            if (a >= verts || b >= verts || c >= verts || a < 0 || b < 0 || c < 0) continue;
            keep.add(i);
        }
        if (keep.isEmpty()) return null;

        // The client's own projection scale, not a fit-to-box -- widget models are drawn at a
        // fixed focal length about the model's ORIGIN and overflow their widget rectangle freely.
        int offsetX = width / 2;
        int offsetY = height / 2;

        int[] sx = new int[verts];
        int[] sy = new int[verts];
        int[] sz = new int[verts];
        double midZ = eyeY * pitchSin + eyeZ * pitchCos;
        for (int i = 0; i < verts; i++)
        {
            sx[i] = offsetX + (int) (rx[i] * FOCAL_LENGTH);
            sy[i] = offsetY + (int) (ry[i] * FOCAL_LENGTH);
            sz[i] = (int) (rz[i] - midZ);
        }

        // The client's calculateBoundsCylinder, for the depth-bucket range.
        double boundMaxY = 0;
        double boundMinY = 0;
        double radiusSqr = 0;
        for (int i = 0; i < verts; i++)
        {
            if (-vy[i] > boundMaxY) boundMaxY = -vy[i];
            if (vy[i] > boundMinY) boundMinY = vy[i];
            double r = vx[i] * vx[i] + vz[i] * vz[i];
            if (r > radiusSqr) radiusSqr = r;
        }
        int radius = (int) (Math.sqrt(radiusSqr) + 0.99);
        int minDepth = (int) (Math.sqrt(radius * radius + boundMaxY * boundMaxY) + 0.99);
        int maxDepth = minDepth + (int) (Math.sqrt(radius * radius + boundMinY * boundMinY) + 0.99);

        int[] colors2 = model.getFaceColors2();
        int[] colors3 = model.getFaceColors3();

        // The client's draw2 ordering: faces go into INTEGER average-depth buckets in face-index
        // order, emitted far to near with insertion order kept inside each bucket. Backface
        // culling happens here, before bucketing, on the same INTEGER screen coordinates the
        // rasterizer itself uses.
        int[] bucketCount = new int[maxDepth + 1];
        int[][] buckets = new int[maxDepth + 1][];
        for (int face : keep)
        {
            int a = fa[face];
            int b = fb[face];
            int c = fc[face];
            if (rz[a] < 0 || rz[b] < 0 || rz[c] < 0) continue;
            if ((sx[a] - sx[b]) * (sy[c] - sy[b]) - (sy[a] - sy[b]) * (sx[c] - sx[b]) <= 0) continue;

            int depthAverage = (sz[a] + sz[b] + sz[c]) / 3 + minDepth;
            if (depthAverage < 0) depthAverage = 0;
            else if (depthAverage > maxDepth) depthAverage = maxDepth;
            if (buckets[depthAverage] == null)
            {
                buckets[depthAverage] = new int[8];
            }
            else if (bucketCount[depthAverage] == buckets[depthAverage].length)
            {
                int[] grown = new int[buckets[depthAverage].length * 2];
                System.arraycopy(buckets[depthAverage], 0, grown, 0, bucketCount[depthAverage]);
                buckets[depthAverage] = grown;
            }
            buckets[depthAverage][bucketCount[depthAverage]++] = face;
        }

        int[] drawOrder = resolveDrawOrder(model.getFaceRenderPriorities(), buckets, bucketCount, maxDepth, fa.length);

        int[] pixels = new int[width * height];
        GouraudRasterizer raster = new GouraudRasterizer(pixels, width, height);

        for (int face : drawOrder)
        {
            int a = fa[face];
            int b = fb[face];
            int c = fc[face];

            boolean flat = colors3 == null || colors3[face] == -1;
            int colourA = colors[face] & 0xFFFF;
            int colourB = flat || colors2 == null ? colourA : colors2[face] & 0xFFFF;
            int colourC = flat ? colourA : colors3[face] & 0xFFFF;

            raster.gouraudTriangle(sx[a], sx[b], sx[c], sy[a], sy[b], sy[c], colourA, colourB, colourC);
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    /** The client's draw2 emission order, ported line for line -- see GouraudRasterizer's own doc
     * for why an exact port matters. Without per-face render priorities: buckets far to near,
     * insertion order within each. With priorities: the client's 12-class algorithm -- faces
     * collect into priority classes in depth order, classes 0..9 emit in class order, and the
     * special classes 10 and 11 interleave into the stream whenever their next face is deeper than
     * the running depth averages of classes 1+2 (checked before class 0), 3+4 (before class 3), and
     * 6+8 (before class 5), with the leftovers emitted at the end. */
    private static int[] resolveDrawOrder(byte[] priorities, int[][] buckets, int[] bucketCount, int maxDepth, int faceTotal)
    {
        int[] order = new int[faceTotal];
        int emitted = 0;

        if (priorities == null)
        {
            for (int depth = maxDepth; depth >= 0; depth--)
            {
                int count = bucketCount[depth];
                for (int i = 0; i < count; i++)
                {
                    order[emitted++] = buckets[depth][i];
                }
            }
            return java.util.Arrays.copyOf(order, emitted);
        }

        int[] priorityFaceCounts = new int[12];
        int[][] priorityFaceLists = new int[12][faceTotal];
        int[] priorityDepthSum = new int[12];
        int[] priority10FaceDepth = new int[faceTotal];
        int[] priority11FaceDepth = new int[faceTotal];

        for (int depth = maxDepth; depth >= 0; depth--)
        {
            int faceCount = bucketCount[depth];
            for (int i = 0; i < faceCount; i++)
            {
                int face = buckets[depth][i];
                int priorityClass = Math.max(0, Math.min(11, priorities[face]));
                int classCount = priorityFaceCounts[priorityClass]++;
                priorityFaceLists[priorityClass][classCount] = face;
                if (priorityClass < 10)
                {
                    priorityDepthSum[priorityClass] += depth;
                }
                else if (priorityClass == 10)
                {
                    priority10FaceDepth[classCount] = depth;
                }
                else
                {
                    priority11FaceDepth[classCount] = depth;
                }
            }
        }

        int averagePriorityDepthSum1_2 = 0;
        if (priorityFaceCounts[1] > 0 || priorityFaceCounts[2] > 0)
        {
            averagePriorityDepthSum1_2 = (priorityDepthSum[1] + priorityDepthSum[2]) / (priorityFaceCounts[1] + priorityFaceCounts[2]);
        }

        int averagePriorityDepthSum3_4 = 0;
        if (priorityFaceCounts[3] > 0 || priorityFaceCounts[4] > 0)
        {
            averagePriorityDepthSum3_4 = (priorityDepthSum[3] + priorityDepthSum[4]) / (priorityFaceCounts[3] + priorityFaceCounts[4]);
        }

        int averagePriorityDepthSum6_8 = 0;
        if (priorityFaceCounts[6] > 0 || priorityFaceCounts[8] > 0)
        {
            averagePriorityDepthSum6_8 = (priorityDepthSum[6] + priorityDepthSum[8]) / (priorityFaceCounts[6] + priorityFaceCounts[8]);
        }

        int priorityFace = 0;
        int priorityFaceCount = priorityFaceCounts[10];
        int[] priorityFaces = priorityFaceLists[10];
        int[] priorityFaceDepths = priority10FaceDepth;
        if (priorityFace == priorityFaceCount)
        {
            priorityFace = 0;
            priorityFaceCount = priorityFaceCounts[11];
            priorityFaces = priorityFaceLists[11];
            priorityFaceDepths = priority11FaceDepth;
        }

        int priorityDepth;
        if (priorityFace < priorityFaceCount)
        {
            priorityDepth = priorityFaceDepths[priorityFace];
        }
        else
        {
            priorityDepth = -1000;
        }

        for (int priority = 0; priority < 10; priority++)
        {
            while (priority == 0 && priorityDepth > averagePriorityDepthSum1_2)
            {
                order[emitted++] = priorityFaces[priorityFace++];
                if (priorityFace == priorityFaceCount && priorityFaces != priorityFaceLists[11])
                {
                    priorityFace = 0;
                    priorityFaceCount = priorityFaceCounts[11];
                    priorityFaces = priorityFaceLists[11];
                    priorityFaceDepths = priority11FaceDepth;
                }
                priorityDepth = priorityFace < priorityFaceCount ? priorityFaceDepths[priorityFace] : -1000;
            }

            while (priority == 3 && priorityDepth > averagePriorityDepthSum3_4)
            {
                order[emitted++] = priorityFaces[priorityFace++];
                if (priorityFace == priorityFaceCount && priorityFaces != priorityFaceLists[11])
                {
                    priorityFace = 0;
                    priorityFaceCount = priorityFaceCounts[11];
                    priorityFaces = priorityFaceLists[11];
                    priorityFaceDepths = priority11FaceDepth;
                }
                priorityDepth = priorityFace < priorityFaceCount ? priorityFaceDepths[priorityFace] : -1000;
            }

            while (priority == 5 && priorityDepth > averagePriorityDepthSum6_8)
            {
                order[emitted++] = priorityFaces[priorityFace++];
                if (priorityFace == priorityFaceCount && priorityFaces != priorityFaceLists[11])
                {
                    priorityFace = 0;
                    priorityFaceCount = priorityFaceCounts[11];
                    priorityFaces = priorityFaceLists[11];
                    priorityFaceDepths = priority11FaceDepth;
                }
                priorityDepth = priorityFace < priorityFaceCount ? priorityFaceDepths[priorityFace] : -1000;
            }

            int count = priorityFaceCounts[priority];
            int[] faces = priorityFaceLists[priority];
            for (int i = 0; i < count; i++)
            {
                order[emitted++] = faces[i];
            }
        }

        while (priorityDepth != -1000)
        {
            order[emitted++] = priorityFaces[priorityFace++];
            if (priorityFace == priorityFaceCount && priorityFaces != priorityFaceLists[11])
            {
                priorityFace = 0;
                priorityFaces = priorityFaceLists[11];
                priorityFaceCount = priorityFaceCounts[11];
                priorityFaceDepths = priority11FaceDepth;
            }
            priorityDepth = priorityFace < priorityFaceCount ? priorityFaceDepths[priorityFace] : -1000;
        }

        return java.util.Arrays.copyOf(order, emitted);
    }

    /** One vertex through the client's widget-model transform: yaw the model, push it to the
     * camera distance, orbit the camera to its pitch, then divide by depth. Returns screen x,
     * screen y and camera-space depth. */
    private static double[] project(double vx, double vy, double vz,
        double yawSin, double yawCos, double pitchSin, double pitchCos, double eyeY, double eyeZ)
    {
        double x = vz * yawSin + vx * yawCos;
        double z = vz * yawCos - vx * yawSin;
        double y = vy;

        y += eyeY;
        z += eyeZ;

        double cy = y * pitchCos - z * pitchSin;
        double cz = y * pitchSin + z * pitchCos;

        if (cz < 1) return new double[]{0, 0, -1};
        return new double[]{x / cz, cy / cz, cz};
    }
}
