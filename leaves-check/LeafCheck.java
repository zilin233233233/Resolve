import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Reports whether a leaf texture is "mipmap safe".
 *
 * <p>The classic Minecraft leaf artefact: pixels that are fully transparent carry black RGB instead
 * of the surrounding leaf colour. Mipmap generation averages RGB regardless of alpha, so those black
 * pixels bleed into the visible colour and leaves darken with distance.</p>
 */
public final class LeafCheck {

    public static void main(String[] args) throws Exception {
        System.out.printf("%-28s %-11s %7s %7s %7s  %s%n",
                "file", "size", "opaque", "clear", "partial", "avg RGB of fully transparent pixels");
        System.out.println("-".repeat(112));

        for (String path : args) {
            BufferedImage img = ImageIO.read(new File(path));
            if (img == null) {
                System.out.printf("%-28s  (unreadable)%n", new File(path).getName());
                continue;
            }

            long opaque = 0;
            long clear = 0;
            long partial = 0;
            long sumR = 0;
            long sumG = 0;
            long sumB = 0;
            long sumOpaqueR = 0;
            long sumOpaqueG = 0;
            long sumOpaqueB = 0;

            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int argb = img.getRGB(x, y);
                    int a = (argb >>> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;

                    if (a == 0) {
                        clear++;
                        sumR += r;
                        sumG += g;
                        sumB += b;
                    } else if (a == 255) {
                        opaque++;
                        sumOpaqueR += r;
                        sumOpaqueG += g;
                        sumOpaqueB += b;
                    } else {
                        partial++;
                    }
                }
            }

            String clearAvg = clear == 0
                    ? "n/a"
                    : String.format("R=%3d G=%3d B=%3d",
                            sumR / clear, sumG / clear, sumB / clear);
            String opaqueAvg = opaque == 0
                    ? "n/a"
                    : String.format("(opaque avg R=%3d G=%3d B=%3d)",
                            sumOpaqueR / opaque, sumOpaqueG / opaque, sumOpaqueB / opaque);

            System.out.printf("%-28s %-11s %7d %7d %7d  %s %s%n",
                    new File(path).getName(),
                    img.getWidth() + "x" + img.getHeight(),
                    opaque, clear, partial, clearAvg, opaqueAvg);
        }

        System.out.println();
        System.out.println("verdict: a fully-transparent average close to R=0 G=0 B=0 means the texture");
        System.out.println("         stores BLACK in its transparent pixels -> mipmaps will darken it with distance.");
    }
}
