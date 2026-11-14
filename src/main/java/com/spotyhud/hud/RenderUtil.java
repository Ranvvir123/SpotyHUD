package com.spotyhud.hud;

import net.minecraft.client.gui.DrawContext;

/** Small 2D helpers: rounded rectangles, outlines, transport icons. All colours are ARGB. */
public final class RenderUtil {
    private RenderUtil() {}

    public static int argb(int rgb, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static int lerpArgb(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int ca = (a >>> shift) & 0xFF, cb = (b >>> shift) & 0xFF;
            out |= (Math.round(ca + (cb - ca) * t) & 0xFF) << shift;
        }
        return out;
    }

    /** Horizontal inset of corner row i (0 = outermost) for radius r. */
    private static int inset(int r, int i) {
        double dy = r - i - 0.5;
        return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
    }

    public static void roundedRect(DrawContext c, int x, int y, int w, int h, int r, int color) {
        roundedRect(c, x, y, w, h, r, color, color);
    }

    /** Rounded rectangle with a vertical gradient. */
    public static void roundedRect(DrawContext c, int x, int y, int w, int h, int r, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            c.fill(x + in, y + i, x + w - in, y + i + 1, lerpArgb(top, bottom, (i + 0.5f) / h));
            c.fill(x + in, y + h - 1 - i, x + w - in, y + h - i, lerpArgb(top, bottom, (h - i - 0.5f) / h));
        }
        if (h - 2 * r > 0) {
            int cs = lerpArgb(top, bottom, (float) r / h), ce = lerpArgb(top, bottom, (float) (h - r) / h);
            if (cs == ce) c.fill(x, y + r, x + w, y + h - r, cs);
            else c.fillGradient(x, y + r, x + w, y + h - r, cs, ce);
        }
    }

    /** 1px rounded outline (drawn so that nothing overlaps - safe with translucent colours). */
    public static void roundedOutline(DrawContext c, int x, int y, int w, int h, int r, int color) {
        if (w <= 1 || h <= 1) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) {
            c.fill(x, y, x + w, y + 1, color);
            c.fill(x, y + h - 1, x + w, y + h, color);
            c.fill(x, y + 1, x + 1, y + h - 1, color);
            c.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
            return;
        }
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            int yTop = y + i, yBot = y + h - 1 - i;
            if (i == 0) {
                c.fill(x + in, yTop, x + w - in, yTop + 1, color);
                c.fill(x + in, yBot, x + w - in, yBot + 1, color);
            } else {
                int span = Math.max(in + 1, inset(r, i - 1));
                c.fill(x + in, yTop, x + span, yTop + 1, color);
                c.fill(x + w - span, yTop, x + w - in, yTop + 1, color);
                c.fill(x + in, yBot, x + span, yBot + 1, color);
                c.fill(x + w - span, yBot, x + w - in, yBot + 1, color);
            }
        }
        if (h - 2 * r > 0) {
            c.fill(x, y + r, x + 1, y + h - r, color);
            c.fill(x + w - 1, y + r, x + w, y + h - r, color);
        }
    }

    // ---- transport icons (size x size box) ----

    private static int triangleWidth(int i, int size, int maxW) {
        float t = 1f - Math.abs((i + 0.5f) - size / 2f) / (size / 2f);
        return Math.max(1, Math.round(t * maxW));
    }

    public static void iconPlay(DrawContext c, int x, int y, int size, int color) {
        for (int i = 0; i < size; i++) c.fill(x, y + i, x + triangleWidth(i, size, size - 1), y + i + 1, color);
    }

    public static void iconPause(DrawContext c, int x, int y, int size, int color) {
        int bw = Math.max(2, size * 3 / 8);
        c.fill(x, y, x + bw, y + size, color);
        c.fill(x + size - bw, y, x + size, y + size, color);
    }

    public static void iconNext(DrawContext c, int x, int y, int size, int color) {
        for (int i = 0; i < size; i++) c.fill(x, y + i, x + triangleWidth(i, size, size - 3), y + i + 1, color);
        c.fill(x + size - 2, y, x + size, y + size, color);
    }

    public static void iconPrevious(DrawContext c, int x, int y, int size, int color) {
        c.fill(x, y, x + 2, y + size, color);
        for (int i = 0; i < size; i++) c.fill(x + size - triangleWidth(i, size, size - 3), y + i, x + size, y + i + 1, color);
    }
}
