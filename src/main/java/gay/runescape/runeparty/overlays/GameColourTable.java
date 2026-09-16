package gay.runescape.runeparty.overlays;

/** The client's 65,536-entry colour table, ported line for line from {@code Pix3D.initColourTable}
 * (see the Follower Buddy plugin's own com.follower.ui.GameColourTable, the source of this port --
 * https://github.com/MikeSpatol/follower-buddy). The game never converts packed HSL to RGB with a
 * formula at draw time: it precomputes this table -- a bespoke HSL-to-RGB with half-step
 * hue/saturation offsets, truncating arithmetic, and a gamma curve from the brightness setting
 * (0.8 at the default "Normal") -- and rasterises by interpolating the PACKED index across each
 * triangle, looking up the table per pixel. A plain linear HSL conversion produces visibly
 * different tones, which is why a naively-converted chathead never quite matches a real one's
 * colours. See ChatheadRenderer, the only consumer.
 * <p>
 * The client also jitters brightness by up to +-0.015 per login; that noise is deliberately
 * omitted here for determinism. Fixed at the default "Normal" (0.8) gamma -- unlike Follower
 * Buddy's own version, this doesn't track the local player's own brightness config setting, since
 * Wise Old Man's chathead is a fixed, real NPC portrait, not something that needs to visually
 * match the player's own current in-game lighting preference. */
final class GameColourTable
{
    private static final int[] TABLE = build(0.8);

    private GameColourTable()
    {
    }

    /** RGB for a packed HSL value, through the game's own table. */
    static int rgb(int packed)
    {
        return TABLE[packed & 0xFFFF];
    }

    private static int[] build(double brightness)
    {
        int[] table = new int[65536];
        int offset = 0;

        for (int y = 0; y < 512; y++)
        {
            double hue = (y / 8) / 64.0 + 0.0078125;
            double saturation = (y & 0x7) / 8.0 + 0.0625;

            for (int x = 0; x < 128; x++)
            {
                double lightness = x / 128.0;
                double r = lightness;
                double g = lightness;
                double b = lightness;

                if (saturation != 0.0)
                {
                    double q;
                    if (lightness < 0.5)
                    {
                        q = lightness * (saturation + 1.0);
                    }
                    else
                    {
                        q = lightness + saturation - lightness * saturation;
                    }

                    double p = lightness * 2.0 - q;

                    double t = hue + (1.0 / 3.0);
                    if (t > 1.0)
                    {
                        t--;
                    }
                    double d = hue - (1.0 / 3.0);
                    if (d < 0.0)
                    {
                        d++;
                    }

                    r = channel(p, q, t);
                    g = channel(p, q, hue);
                    b = channel(p, q, d);
                }

                int rgb = ((int) (r * 256.0) << 16)
                    + ((int) (g * 256.0) << 8)
                    + (int) (b * 256.0);
                table[offset++] = gammaCorrect(rgb, brightness);
            }
        }
        return table;
    }

    private static double channel(double p, double q, double t)
    {
        if (t * 6.0 < 1.0)
        {
            return p + (q - p) * 6.0 * t;
        }
        if (t * 2.0 < 1.0)
        {
            return q;
        }
        if (t * 3.0 < 2.0)
        {
            return p + (q - p) * ((2.0 / 3.0) - t) * 6.0;
        }
        return p;
    }

    private static int gammaCorrect(int rgb, double gamma)
    {
        double r = (rgb >> 16) / 256.0;
        double g = ((rgb >> 8) & 0xFF) / 256.0;
        double b = (rgb & 0xFF) / 256.0;

        return ((int) (Math.pow(r, gamma) * 256.0) << 16)
            + ((int) (Math.pow(g, gamma) * 256.0) << 8)
            + (int) (Math.pow(b, gamma) * 256.0);
    }
}
