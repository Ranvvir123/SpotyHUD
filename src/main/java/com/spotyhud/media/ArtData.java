package com.spotyhud.media;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;

/**
 * Decoded, square-cropped cover art (ARGB pixels, at most 256x256) plus two sampled palette colours.
 * Built on the media thread so the render thread never decodes images.
 */
public final class ArtData {
    public static final int MAX_SIZE = 256;

    private final int size;
    private final int[] pixels;
    private final int colorA;
    private final int colorB;

    private ArtData(int size, int[] pixels, int colorA, int colorB) {
        this.size = size;
        this.pixels = pixels;
        this.colorA = colorA;
        this.colorB = colorB;
    }

    public int size() { return size; }
    public int[] pixels() { return pixels; }
    /** Dominant colour as 0xRRGGBB. */
    public int colorA() { return colorA; }
    /** Second, distinct colour as 0xRRGGBB. */
    public int colorB() { return colorB; }

    public static ArtData decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try {
            var img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) return null;
            int w = img.getWidth(), h = img.getHeight();
            int side = Math.min(w, h);
            int[] crop = img.getRGB((w - side) / 2, (h - side) / 2, side, side, null, 0, side);
            int size = Math.min(MAX_SIZE, side);
            int[] px = resample(crop, side, size);
            int[] palette = palette(px);
            return new ArtData(size, px, palette[0], palette[1]);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /** Box-filter resample of a square ARGB image. */
    public static int[] resample(int[] src, int srcSize, int dstSize) {
        if (srcSize == dstSize) return src;
        int[] dst = new int[dstSize * dstSize];
        double scale = (double) srcSize / dstSize;
        for (int y = 0; y < dstSize; y++) {
            int y0 = (int) Math.floor(y * scale), y1 = Math.min(srcSize, Math.max(y0 + 1, (int) Math.ceil((y + 1) * scale)));
            for (int x = 0; x < dstSize; x++) {
                int x0 = (int) Math.floor(x * scale), x1 = Math.min(srcSize, Math.max(x0 + 1, (int) Math.ceil((x + 1) * scale)));
                long a = 0, r = 0, g = 0, b = 0;
                int n = 0;
                for (int yy = y0; yy < y1; yy++) {
                    for (int xx = x0; xx < x1; xx++) {
                        int p = src[yy * srcSize + xx];
                        a += p >>> 24; r += (p >> 16) & 0xFF; g += (p >> 8) & 0xFF; b += p & 0xFF;
                        n++;
                    }
                }
                dst[y * dstSize + x] = ((int) (a / n) << 24) | ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
            }
        }
        return dst;
    }

    /** Histogram in 4-bit-per-channel buckets, weighted towards saturated, not-too-dark/bright pixels. */
    private static int[] palette(int[] px) {
        double[] weight = new double[4096];
        double[] sr = new double[4096], sg = new double[4096], sb = new double[4096];
        for (int i = 0; i < px.length; i += 3) {
            int p = px[i];
            if ((p >>> 24) < 128) continue;
            int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
            int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
            double sat = max == 0 ? 0 : (max - min) / (double) max;
            double lum = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
            double w = 0.25 + sat;
            if (lum < 0.08 || lum > 0.95) w *= 0.3;
            int idx = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
            weight[idx] += w; sr[idx] += r * w; sg[idx] += g * w; sb[idx] += b * w;
        }
        int best = 0;
        for (int i = 1; i < 4096; i++) if (weight[i] > weight[best]) best = i;
        if (weight[best] <= 0) return new int[]{0x2A2A2E, 0x1C1C1F};
        int c1 = avg(sr, sg, sb, weight, best);
        int second = -1;
        for (int i = 0; i < 4096; i++) {
            if (weight[i] < weight[best] * 0.12) continue;
            if (distance(avg(sr, sg, sb, weight, i), c1) < 70) continue;
            if (second < 0 || weight[i] > weight[second]) second = i;
        }
        int c2 = second >= 0 ? avg(sr, sg, sb, weight, second) : scale(c1, 0.65f);
        return new int[]{c1, c2};
    }

    private static int avg(double[] sr, double[] sg, double[] sb, double[] w, int i) {
        return ((int) (sr[i] / w[i]) << 16) | ((int) (sg[i] / w[i]) << 8) | (int) (sb[i] / w[i]);
    }

    private static double distance(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF), dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF), db = (a & 0xFF) - (b & 0xFF);
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    private static int scale(int rgb, float f) {
        return ((int) (((rgb >> 16) & 0xFF) * f) << 16) | ((int) (((rgb >> 8) & 0xFF) * f) << 8) | (int) ((rgb & 0xFF) * f);
    }
}
