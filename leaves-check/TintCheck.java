import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Tests the hypothesis "pack texture == vanilla texture with the vanilla tint already baked in".
 *
 * <p>If true, the pack author pre-applied the colour that vanilla applies at runtime, and vanilla
 * multiplying by that colour a second time squares it and turns leaves almost black.</p>
 */
public final class TintCheck {

    public static void main(String[] args) throws Exception {
        String vanillaPath = args[0];
        String packPath = args[1];
        String label = args[2];
        int tintR = Integer.parseInt(args[3]);
        int tintG = Integer.parseInt(args[4]);
        int tintB = Integer.parseInt(args[5]);

        BufferedImage vanilla = ImageIO.read(new File(vanillaPath));
        BufferedImage pack = ImageIO.read(new File(packPath));

        long alphaMismatch = 0;
        long compared = 0;
        long predictedSum = 0;
        long naiveSum = 0;
        int sampleV = 0;
        int sampleP = 0;
        int samplePredicted = 0;

        for (int y = 0; y < vanilla.getHeight(); y++) {
            for (int x = 0; x < vanilla.getWidth(); x++) {
                int v = vanilla.getRGB(x, y);
                int p = pack.getRGB(x, y);

                int va = (v >>> 24) & 0xFF;
                int pa = (p >>> 24) & 0xFF;
                if (va != pa) {
                    alphaMismatch++;
                }
                if (va == 0) {
                    continue; // compare only visible texels
                }

                int vr = (v >> 16) & 0xFF, vg = (v >> 8) & 0xFF, vb = v & 0xFF;
                int pr = (p >> 16) & 0xFF, pg = (p >> 8) & 0xFF, pb = p & 0xFF;

                // What the pack texel WOULD be if the author baked vanilla's tint into it.
                int predR = vr * tintR / 255;
                int predG = vg * tintG / 255;
                int predB = vb * tintB / 255;

                int dPred = Math.abs(pr - predR) + Math.abs(pg - predG) + Math.abs(pb - predB);
                int dNaive = Math.abs(pr - vr) + Math.abs(pg - vg) + Math.abs(pb - vb);

                predictedSum += dPred;
                naiveSum += dNaive;
                compared++;

                if (compared <= 3) {
                    sampleV = (vr << 16) | (vg << 8) | vb;
                    sampleP = (pr << 16) | (pg << 8) | pb;
                    samplePredicted = (predR << 16) | (predG << 8) | predB;
                }
            }
        }

        double meanPredicted = compared == 0 ? 0 : (double) predictedSum / compared;
        double meanNaive = compared == 0 ? 0 : (double) naiveSum / compared;

        System.out.printf("%s%n", label);
        System.out.printf("  visible texels compared : %d   alpha mismatches: %d%n", compared, alphaMismatch);
        System.out.printf("  sample texel  vanilla=%06X  pack=%06X  vanilla*tint=%06X%n",
                sampleV, sampleP, samplePredicted);
        System.out.printf("  mean |pack - vanilla*tint| = %6.2f   <-- lower means the tint is baked in%n", meanPredicted);
        System.out.printf("  mean |pack - vanilla|      = %6.2f%n", meanNaive);
        System.out.printf("  => %s%n%n",
                meanPredicted < meanNaive
                        ? "CONFIRMED: pack texture is vanilla with the tint already applied"
                        : "not confirmed: pack texture is not a simple tint of vanilla");
    }
}
