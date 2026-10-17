import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/**
 * Makes every leaf texture in a resource pack "mipmap safe".
 *
 * <p>Fully transparent pixels keep alpha 0 but get their RGB replaced with the average colour of the
 * visible texels in the same texture. At mip level 0 those pixels are discarded by the alpha test,
 * so nothing changes up close. At higher mip levels the averaged RGB is now the leaf colour instead
 * of black or white, which is what stops distant leaves from darkening or acquiring a white frost.</p>
 */
public final class MipmapFix {

    private static int fixed;
    private static int scanned;

    public static void main(String[] args) throws Exception {
        Path source = Paths.get(args[0]);
        Path dest = Paths.get(args[1]);

        try (ZipInputStream in = new ZipInputStream(new BufferedInputStream(Files.newInputStream(source)));
             ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(dest)))) {

            ZipEntry entry;
            byte[] buffer = new byte[8192];

            while ((entry = in.getNextEntry()) != null) {
                ByteArrayOutputStream collector = new ByteArrayOutputStream();
                int read;
                while ((read = in.read(buffer)) > 0) {
                    collector.write(buffer, 0, read);
                }
                byte[] data = collector.toByteArray();

                if (isLeafTexture(entry.getName())) {
                    scanned++;
                    byte[] fixedData = fixLeaf(data, entry.getName());
                    if (fixedData != null) {
                        data = fixedData;
                    }
                }

                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                out.putNextEntry(copy);
                out.write(data);
                out.closeEntry();
            }
        }

        System.out.printf("leaf textures scanned: %d, rewritten: %d%n", scanned, fixed);
        System.out.println("written -> " + dest);
    }

    private static boolean isLeafTexture(String name) {
        return name.endsWith(".png")
                && (name.matches("assets/minecraft/textures/block/.*leaves.*\\.png")
                 || name.matches("assets/minecraft/optifine/ctm/leaves/.*\\.png"));
    }

    private static byte[] fixLeaf(byte[] png, String name) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null) {
                return null;
            }

            long sumR = 0, sumG = 0, sumB = 0, count = 0;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int argb = img.getRGB(x, y);
                    if (((argb >>> 24) & 0xFF) == 255) {
                        sumR += (argb >> 16) & 0xFF;
                        sumG += (argb >> 8) & 0xFF;
                        sumB += argb & 0xFF;
                        count++;
                    }
                }
            }
            if (count == 0) {
                return null;
            }

            int avg = ((int) (sumR / count) << 16) | ((int) (sumG / count) << 8) | (int) (sumB / count);

            boolean changed = false;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int argb = img.getRGB(x, y);
                    if (((argb >>> 24) & 0xFF) == 0) {
                        int current = argb & 0xFFFFFF;
                        int r = (current >> 16) & 0xFF;
                        int g = (current >> 8) & 0xFF;
                        int b = current & 0xFF;
                        // Only rewrite pixels that would visibly contaminate a mipmap: black or
                        // already close to the leaf colour are both fine to leave alone.
                        int avgR = (avg >> 16) & 0xFF, avgG = (avg >> 8) & 0xFF, avgB = avg & 0xFF;
                        int drift = Math.abs(r - avgR) + Math.abs(g - avgG) + Math.abs(b - avgB);
                        if (drift > 90) {
                            img.setRGB(x, y, avg); // alpha 0 preserved
                            changed = true;
                        }
                    }
                }
            }

            if (!changed) {
                return null;
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            fixed++;
            System.out.printf("  fixed %-72s -> transparent RGB %06X%n", name, avg);
            return out.toByteArray();
        } catch (Exception e) {
            System.out.printf("  SKIP  %-72s (%s)%n", name, e.getMessage());
            return null;
        }
    }
}
