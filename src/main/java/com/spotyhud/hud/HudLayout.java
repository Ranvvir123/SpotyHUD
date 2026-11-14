package com.spotyhud.hud;

import com.spotyhud.config.SpotyConfig;

/** Geometry of the HUD in its own (unscaled) coordinate space, plus screen-space bounds. */
public final class HudLayout {
    public static final int ART = 58;
    public static final int GAP = 6;
    public static final int CARD_X = ART + GAP;
    public static final int CARD_W = 176;
    public static final int W = CARD_X + CARD_W;
    public static final int H = 58;
    public static final int RADIUS = 10;

    // Control buttons (centered row inside the card)
    public static final int BTN_Y = 31;
    public static final int BTN_W = 18;
    public static final int BTN_H = 13;
    public static final int BTN_CENTER_X = CARD_X + CARD_W / 2;
    public static final int BTN_SPACING = 26;

    private HudLayout() {}

    public record Bounds(float x, float y, float w, float h) {
        public boolean contains(double px, double py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    public static Bounds bounds(SpotyConfig cfg, int screenW, int screenH) {
        float w = W * cfg.scale, h = H * cfg.scale;
        return new Bounds(cfg.posX * Math.max(0f, screenW - w), cfg.posY * Math.max(0f, screenH - h), w, h);
    }
}
