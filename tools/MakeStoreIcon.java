import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Renders the 512x512 Play Store listing icon (ROADMAP F14).
 *
 * Run with the single-file source launcher, no build step:
 *
 *     java tools/MakeStoreIcon.java [output.png]
 *
 * It reads the app's **own** adaptive-icon resources rather than repeating the
 * geometry:
 *
 *   res/drawable/ic_launcher_foreground.xml   the mark
 *   res/values/ic_launcher_background.xml     the background colour
 *
 * Copying those numbers here would have been shorter, and would have gone stale
 * the first time the icon changed — leaving a store listing that no longer
 * matched the installed app. This way there is one source of truth, and the
 * tool fails loudly on a path command it does not understand instead of drawing
 * something subtly wrong.
 *
 * Why a generator at all: this machine has no SVG rasteriser (no rsvg-convert,
 * no Inkscape, no ImageMagick), and the icon is a vector. Java's own AWT can do
 * it with nothing installed.
 *
 * The 108-unit viewport is mapped across the whole 512 canvas, so the artwork
 * lands at the same proportion the launcher shows (the mark occupies the central
 * 72 units). Play wants a full-bleed square with no rounded corners and no
 * shadow, which is what this produces.
 */
public final class MakeStoreIcon {

    private static final int SIZE = 512;
    private static final String FOREGROUND =
        "app/src/main/res/drawable/ic_launcher_foreground.xml";
    private static final String BACKGROUND_VALUES =
        "app/src/main/res/values/ic_launcher_background.xml";

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "store/icon-512.png");

        String vectorXml = new String(Files.readAllBytes(new File(FOREGROUND).toPath()),
                                      StandardCharsets.UTF_8);
        String valuesXml = new String(Files.readAllBytes(new File(BACKGROUND_VALUES).toPath()),
                                      StandardCharsets.UTF_8);

        Color background = parseBackground(valuesXml);
        double viewport = parseViewport(vectorXml);
        List<Layer> layers = parseLayers(vectorXml);

        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                           RenderingHints.VALUE_STROKE_PURE);

        g.setColor(background);
        g.fillRect(0, 0, SIZE, SIZE);

        double scale = SIZE / viewport;

        // Scale through the graphics transform rather than the geometry: one
        // transform, and the path data stays in the vector's own units.
        g.scale(scale, scale);
        for (Layer layer : layers) {
            g.setColor(layer.color);
            g.fill(layer.shape);
        }
        g.dispose();

        File parent = out.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        if (!ImageIO.write(image, "png", out)) {
            throw new IllegalStateException("no PNG writer available");
        }
        System.out.println("wrote " + out + "  " + image.getWidth() + "x" + image.getHeight()
                           + "  " + layers.size() + " paths");
    }

    // ---------------------------------------------------------------- parsing

    /** `android:viewportWidth="108"` */
    private static double parseViewport(String xml) {
        Matcher m = Pattern.compile("android:viewportWidth\\s*=\\s*\"([0-9.]+)\"").matcher(xml);
        if (!m.find()) {
            throw new IllegalArgumentException("no android:viewportWidth in the vector");
        }
        return Double.parseDouble(m.group(1));
    }

    /** `ic_launcher_background">#1F2430<` */
    private static Color parseBackground(String xml) {
        Matcher m = Pattern.compile("ic_launcher_background\">\\s*(#[0-9A-Fa-f]{6,8})")
                             .matcher(xml);
        if (!m.find()) {
            throw new IllegalArgumentException("no ic_launcher_background colour found");
        }
        return parseColor(m.group(1), "background");
    }

    private static Color parseColor(String value, String where) {
        String hex = value.startsWith("#") ? value.substring(1) : value;
        long v = Long.parseLong(hex, 16);
        if (hex.length() == 6) {
            return new Color((int) v | 0xFF000000, true);   // opaque
        }
        if (hex.length() == 8) {
            return new Color((int) v, true);                // #AARRGGBB
        }
        throw new IllegalArgumentException("unsupported colour " + value + " in " + where);
    }

    private static final class Layer {
        final Color color;
        final Path2D.Double shape;
        Layer(Color color, Path2D.Double shape) {
            this.color = color;
            this.shape = shape;
        }
    }

    private static List<Layer> parseLayers(String xml) {
        List<Layer> layers = new ArrayList<>();
        Matcher path = Pattern.compile("<path\\b(.*?)/>", Pattern.DOTALL).matcher(xml);
        while (path.find()) {
            String attrs = path.group(1);
            String fill = attribute(attrs, "android:fillColor");
            String data = attribute(attrs, "android:pathData");
            if (fill == null || data == null) {
                continue;
            }
            layers.add(new Layer(parseColor(fill, "fillColor"), parsePath(data)));
        }
        if (layers.isEmpty()) {
            throw new IllegalArgumentException("no <path> with fillColor and pathData found");
        }
        return layers;
    }

    private static String attribute(String attrs, String name) {
        Matcher m = Pattern.compile(Pattern.quote(name) + "\\s*=\\s*\"([^\"]*)\"").matcher(attrs);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Parses the subset of SVG path syntax a vector drawable needs here:
     * M/m, L/l, H/h, V/v and Z/z.
     *
     * Deliberately not a complete parser. Anything else — curves, arcs — throws,
     * because a store icon that silently mis-draws a curve is worse than a tool
     * that refuses to run.
     */
    private static Path2D.Double parsePath(String data) {
        Path2D.Double shape = new Path2D.Double();
        Matcher tokens = Pattern.compile("([MmLlHhVvZz])|(-?[0-9]*\\.?[0-9]+)").matcher(data);
        char command = 0;
        double x = 0;
        double y = 0;
        double startX = 0;
        double startY = 0;
        List<Double> args = new ArrayList<>();

        while (tokens.find()) {
            String letter = tokens.group(1);
            if (letter != null) {
                command = letter.charAt(0);
                if (command == 'Z' || command == 'z') {
                    shape.closePath();
                    x = startX;
                    y = startY;
                }
                continue;
            }
            args.add(Double.parseDouble(tokens.group(2)));
            int needed = arity(command);
            if (needed == 0 || args.size() < needed) {
                continue;
            }
            double a = args.get(0);
            double b = needed > 1 ? args.get(1) : 0;
            args.clear();

            switch (command) {
                // After a moveto, further coordinate pairs are implicit linetos
                // (SVG's rule); without this a multi-point move would be silently
                // collapsed onto one point.
                case 'M': x = a; y = b; shape.moveTo(x, y); startX = x; startY = y; command = 'L'; break;
                case 'm': x += a; y += b; shape.moveTo(x, y); startX = x; startY = y; command = 'l'; break;
                case 'L': x = a; y = b; shape.lineTo(x, y); break;
                case 'l': x += a; y += b; shape.lineTo(x, y); break;
                case 'H': x = a; shape.lineTo(x, y); break;
                case 'h': x += a; shape.lineTo(x, y); break;
                case 'V': y = a; shape.lineTo(x, y); break;
                case 'v': y += a; shape.lineTo(x, y); break;
                default:
                    throw new IllegalArgumentException(
                        "unsupported path command '" + command + "' in " + data);
            }
        }
        return shape;
    }

    private static int arity(char command) {
        switch (command) {
            case 'M': case 'm': case 'L': case 'l': return 2;
            case 'H': case 'h': case 'V': case 'v': return 1;
            case 'Z': case 'z': return 0;
            default:
                throw new IllegalArgumentException("unsupported path command '" + command + "'");
        }
    }

    private MakeStoreIcon() {
    }
}
