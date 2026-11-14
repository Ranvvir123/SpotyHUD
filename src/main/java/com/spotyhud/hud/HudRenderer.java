package com.spotyhud.hud;

import com.spotyhud.config.SpotyConfig;
import com.spotyhud.media.ArtData;
import com.spotyhud.media.MediaCommand;
import com.spotyhud.media.MediaInfo;
import com.spotyhud.media.MediaService;
import com.spotyhud.media.MediaState;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Draws the pill-card HUD and handles clicks on its transport buttons while the cursor is free. */
public final class HudRenderer implements HudElement {
    private static final int DEFAULT_TOP = 0x2A2A2E, DEFAULT_BOTTOM = 0x1B1B1F;
    private static final int WHITE = 0xFFFFFF, MUTED = 0xB4B4BC, TIME = 0xC8C8D0;
    private static volatile boolean preview;

    /** While true (HUD editor open) the HUD is shown even when nothing is playing. */
    public static void setPreview(boolean on) { preview = on; }

    private final AlbumArtTexture artTexture = new AlbumArtTexture();

    private float visibility;
    private float controlsAlpha;
    private long lastNanos = System.nanoTime();
    private MediaState shown;
    private String shownKey = "";
    private double displayPos;
    private int topColor = DEFAULT_TOP, bottomColor = DEFAULT_BOTTOM;
    private int targetTop = DEFAULT_TOP, targetBottom = DEFAULT_BOTTOM;
    private ArtData colorSource;
    private boolean mouseWasDown;

    private String titleSrc = "", titleFit = "", artistSrc = "", artistFit = "";

    @Override
    public void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        SpotyConfig cfg = SpotyConfig.get();
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;

        MediaState live = MediaService.get().state();
        boolean demo = preview && !live.info().active();
        MediaState st = demo ? MediaState.demo() : live;
        boolean want = (cfg.enabled || preview) && st.info().active();

        visibility = approach(visibility, want ? 1f : 0f, dt / 0.25f);
        if (want) shown = st;
        boolean mouseDown = isLeftDown(mc);
        if (!want && visibility <= 0f) {
            if (shown != null) {          // fully hidden: free the GPU texture
                artTexture.release();
                shown = null;
                shownKey = "";
                colorSource = null;
            }
            mouseWasDown = mouseDown;
            return;
        }
        if (shown == null) return;

        MediaInfo info = shown.info();
        String key = info.trackKey();
        double target = demo ? info.positionSec() : info.positionAt(now);
        if (!key.equals(shownKey)) {
            shownKey = key;
            displayPos = target;
        } else {
            double diff = target - displayPos;
            displayPos = Math.abs(diff) > 1.5 ? target : displayPos + diff * Math.min(1.0, dt * 8.0);
        }
        if (shown.art() != colorSource || shownKey.isEmpty()) {
            colorSource = shown.art();
            targetTop = colorSource == null ? DEFAULT_TOP : tone(colorSource.colorA());
            targetBottom = colorSource == null ? DEFAULT_BOTTOM : tone(colorSource.colorB());
        }
        float k = Math.min(1f, dt * 6f);
        topColor = RenderUtil.lerpArgb(0xFF000000 | topColor, 0xFF000000 | targetTop, k) & 0xFFFFFF;
        bottomColor = RenderUtil.lerpArgb(0xFF000000 | bottomColor, 0xFF000000 | targetBottom, k) & 0xFFFFFF;

        Window window = mc.getWindow();
        int sw = window.getScaledWidth(), sh = window.getScaledHeight();
        HudLayout.Bounds b = HudLayout.bounds(cfg, sw, sh);
        float s = cfg.scale;
        float slideY = (1f - visibility) * -8f;

        Screen screen = mc.currentScreen;
        boolean cursorFree = screen != null && !(screen instanceof HandledScreen<?>);
        controlsAlpha = approach(controlsAlpha, cursorFree ? 1f : 0f, dt / 0.15f);

        double mx = mc.mouse.getX() * sw / (double) window.getWidth();
        double my = mc.mouse.getY() * sh / (double) window.getHeight();
        double lx = (mx - b.x()) / s, ly = (my - (b.y() + slideY)) / s;
        int hover = cursorFree ? hitButton(lx, ly) : -1;

        boolean clicked = mouseDown && !mouseWasDown;
        mouseWasDown = mouseDown;
        if (clicked && hover >= 0 && !preview && visibility > 0.8f) {
            MediaService.get().send(switch (hover) {
                case 0 -> MediaCommand.PREVIOUS;
                case 1 -> MediaCommand.PLAY_PAUSE;
                default -> MediaCommand.NEXT;
            });
        }

        int px = Math.round(HudLayout.ART * s * (float) window.getScaleFactor());
        px = Math.max(32, Math.min(256, (px + 15) / 16 * 16));
        Identifier artId = artTexture.get(shown.art(), px);

        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(Math.round(b.x()), Math.round(b.y() + slideY));
        m.scale(s, s);
        draw(ctx, mc, cfg, info, artId, hover);
        m.popMatrix();
    }

    private void draw(DrawContext ctx, MinecraftClient mc, SpotyConfig cfg, MediaInfo info, Identifier artId, int hover) {
        TextRenderer tr = mc.textRenderer;
        float vis = visibility;
        int cx = HudLayout.CARD_X, cw = HudLayout.CARD_W, h = HudLayout.H, r = HudLayout.RADIUS;
        int top = RenderUtil.argb(topColor, cfg.opacity * vis);
        int bottom = RenderUtil.argb(bottomColor, cfg.opacity * vis);

        if (cfg.frostedGlass) {
            for (int i = 1; i <= 3; i++) {
                int glow = RenderUtil.argb(0x000000, (0.20f - i * 0.05f) * vis);
                RenderUtil.roundedOutline(ctx, cx - i, -i, cw + 2 * i, h + 2 * i, r + i, glow);
                RenderUtil.roundedOutline(ctx, -i, -i, HudLayout.ART + 2 * i, h + 2 * i, r + i, glow);
            }
        }
        RenderUtil.roundedRect(ctx, cx, 0, cw, h, r, top, bottom);
        if (cfg.frostedGlass) {
            RenderUtil.roundedOutline(ctx, cx, 0, cw, h, r, RenderUtil.argb(0xFFFFFF, 0.16f * vis));
        }

        // ---- cover art ----
        if (artId != null) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, artId, 0, 0, 0f, 0f,
                    HudLayout.ART, HudLayout.ART, HudLayout.ART, HudLayout.ART);
        } else {
            RenderUtil.roundedRect(ctx, 0, 0, HudLayout.ART, HudLayout.ART, r,
                    RenderUtil.argb(0x3A3A40, vis), RenderUtil.argb(0x24242A, vis));
            if (vis > 0.06f) {
                ctx.drawText(tr, "♪", HudLayout.ART / 2 - tr.getWidth("♪") / 2, HudLayout.ART / 2 - 4,
                        RenderUtil.argb(0xAAAAAA, vis), false);
            }
        }

        // ---- text ----
        int textX = cx + 10, textW = cw - 20;
        if (vis > 0.06f) {
            if (!info.title().equals(titleSrc)) { titleSrc = info.title(); titleFit = fit(tr, titleSrc, true, textW); }
            if (!info.artist().equals(artistSrc)) { artistSrc = info.artist(); artistFit = fit(tr, artistSrc, false, textW); }
            ctx.drawText(tr, Text.literal(titleFit).formatted(Formatting.BOLD), textX, 8, RenderUtil.argb(WHITE, vis), false);
            ctx.drawText(tr, artistFit, textX, 20, RenderUtil.argb(MUTED, vis), false);

            double dur = info.durationSec();
            String left = fmt(displayPos);
            String right = dur > 0 ? "-" + fmt(Math.max(0, dur - displayPos)) : "";
            ctx.drawText(tr, left, textX, 34, RenderUtil.argb(TIME, vis), false);
            if (!right.isEmpty()) {
                ctx.drawText(tr, right, cx + cw - 10 - tr.getWidth(right), 34, RenderUtil.argb(TIME, vis), false);
            }
        }

        // ---- transport buttons (only while the cursor is free) ----
        if (controlsAlpha > 0.02f) {
            for (int i = 0; i < 3; i++) {
                int bx = HudLayout.BTN_CENTER_X + (i - 1) * HudLayout.BTN_SPACING;
                float a = controlsAlpha * vis * (hover == i ? 1f : 0.72f);
                if (hover == i) {
                    RenderUtil.roundedRect(ctx, bx - HudLayout.BTN_W / 2, HudLayout.BTN_Y, HudLayout.BTN_W, HudLayout.BTN_H, 4,
                            RenderUtil.argb(0xFFFFFF, 0.14f * controlsAlpha * vis));
                }
                int color = RenderUtil.argb(WHITE, a);
                int ix = bx - 4, iy = HudLayout.BTN_Y + (HudLayout.BTN_H - 8) / 2;
                switch (i) {
                    case 0 -> RenderUtil.iconPrevious(ctx, ix, iy, 8, color);
                    case 1 -> { if (info.playing()) RenderUtil.iconPause(ctx, ix, iy, 8, color); else RenderUtil.iconPlay(ctx, ix, iy, 8, color); }
                    default -> RenderUtil.iconNext(ctx, ix, iy, 8, color);
                }
            }
        }

        // ---- progress bar ----
        int barX = cx + 10, barW = cw - 20, barY = 47, barH = 4;
        RenderUtil.roundedRect(ctx, barX, barY, barW, barH, 2, RenderUtil.argb(0xFFFFFF, 0.22f * vis));
        double frac = info.durationSec() > 0 ? Math.max(0, Math.min(1, displayPos / info.durationSec())) : 0;
        int fillW = (int) Math.round(barW * frac);
        if (fillW > 0) RenderUtil.roundedRect(ctx, barX, barY, Math.max(fillW, 2), barH, 2, RenderUtil.argb(0xFFFFFF, 0.95f * vis));
    }

    /** @return 0 previous, 1 play/pause, 2 next, or -1 */
    private static int hitButton(double lx, double ly) {
        if (ly < HudLayout.BTN_Y || ly > HudLayout.BTN_Y + HudLayout.BTN_H) return -1;
        for (int i = 0; i < 3; i++) {
            double bx = HudLayout.BTN_CENTER_X + (i - 1) * HudLayout.BTN_SPACING;
            if (Math.abs(lx - bx) <= HudLayout.BTN_W / 2.0) return i;
        }
        return -1;
    }

    private static boolean isLeftDown(MinecraftClient mc) {
        return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    private static float approach(float v, float target, float step) {
        return v < target ? Math.min(target, v + step) : Math.max(target, v - step);
    }

    /** Darkens a colour so white text always stays readable on the card. */
    private static int tone(int rgb) {
        float r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        float lum = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f;
        float f = lum > 0.30f ? 0.30f / lum : 1f;
        return (Math.round(r * f) << 16) | (Math.round(g * f) << 8) | Math.round(b * f);
    }

    private static String fmt(double sec) {
        int t = (int) Math.max(0, Math.floor(sec));
        int h = t / 3600, m = (t % 3600) / 60, s = t % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    private static String fit(TextRenderer tr, String s, boolean bold, int maxW) {
        if (width(tr, s, bold) <= maxW) return s;
        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (width(tr, s.substring(0, mid) + "…", bold) <= maxW) lo = mid; else hi = mid - 1;
        }
        return s.substring(0, lo) + "…";
    }

    private static int width(TextRenderer tr, String s, boolean bold) {
        return tr.getWidth(bold ? Text.literal(s).formatted(Formatting.BOLD) : Text.literal(s));
    }

    /** Frees GPU resources (called on shutdown). */
    public void release() {
        artTexture.release();
    }
}
