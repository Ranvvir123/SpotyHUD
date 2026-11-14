package com.spotyhud.config;

import com.spotyhud.hud.HudLayout;
import com.spotyhud.hud.HudRenderer;
import com.spotyhud.hud.RenderUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.DoubleConsumer;

/**
 * HUD editor: drag the live HUD anywhere, tune scale / opacity / frosted glass. The real HUD is drawn by
 * {@link HudRenderer} underneath (in preview mode); this screen only draws the outline and the controls.
 * Dragging polls GLFW directly so it does not depend on version-specific mouse event classes.
 */
public final class ConfigScreen extends Screen {
    private static final int PANEL_H = 82;

    private boolean dragging;
    private boolean wasDown;
    private double grabDx, grabDy;

    public ConfigScreen() {
        super(Text.literal("SpotyHUD"));
    }

    @Override
    protected void init() {
        HudRenderer.setPreview(true);
        SpotyConfig cfg = SpotyConfig.get();
        int bw = 150, gap = 4;
        int x0 = width / 2 - bw - gap / 2, x1 = x0 + bw + gap;
        int y = height - PANEL_H + 6;

        addDrawableChild(new ValueSlider(x0, y, bw, 20, "Scale", SpotyConfig.MIN_SCALE, SpotyConfig.MAX_SCALE,
                cfg.scale, v -> cfg.scale = (float) v, "x"));
        addDrawableChild(new ValueSlider(x1, y, bw, 20, "Opacity", 0.2, 1.0,
                cfg.opacity, v -> cfg.opacity = (float) v, ""));
        addDrawableChild(ButtonWidget.builder(toggleLabel("HUD", cfg.enabled), b -> {
            cfg.enabled = !cfg.enabled;
            b.setMessage(toggleLabel("HUD", cfg.enabled));
        }).dimensions(x0, y + 24, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(toggleLabel("Frosted glass", cfg.frostedGlass), b -> {
            cfg.frostedGlass = !cfg.frostedGlass;
            b.setMessage(toggleLabel("Frosted glass", cfg.frostedGlass));
        }).dimensions(x1, y + 24, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset position"), b -> {
            cfg.posX = 0.01f;
            cfg.posY = 0.02f;
        }).dimensions(x0, y + 48, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(x1, y + 48, bw, 20).build());
    }

    private static Text toggleLabel(String name, boolean on) {
        return Text.literal(name + ": " + (on ? "ON" : "OFF"));
    }

    /** No blur / darkening: the HUD must stay clearly visible behind the editor. */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, height - PANEL_H, width, height, 0xC0000000);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        SpotyConfig cfg = SpotyConfig.get();
        double mx = client.mouse.getX() * width / (double) client.getWindow().getWidth();
        double my = client.mouse.getY() * height / (double) client.getWindow().getHeight();
        boolean down = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        HudLayout.Bounds b = HudLayout.bounds(cfg, width, height);
        if (down && !wasDown && b.contains(mx, my) && my < height - PANEL_H) {
            dragging = true;
            grabDx = mx - b.x();
            grabDy = my - b.y();
        }
        if (dragging) {
            if (down) {
                float freeW = width - b.w(), freeH = height - b.h();
                cfg.posX = freeW > 0 ? clamp01((float) ((mx - grabDx) / freeW)) : 0f;
                cfg.posY = freeH > 0 ? clamp01((float) ((my - grabDy) / freeH)) : 0f;
            } else {
                dragging = false;
                SpotyConfig.save();
            }
        }
        wasDown = down;

        b = HudLayout.bounds(cfg, width, height);
        boolean hot = dragging || b.contains(mx, my);
        RenderUtil.roundedOutline(ctx, Math.round(b.x()) - 2, Math.round(b.y()) - 2, Math.round(b.w()) + 4, Math.round(b.h()) + 4,
                Math.round(HudLayout.RADIUS * cfg.scale) + 2, hot ? 0xFF1DB954 : 0x99FFFFFF);

        Text title = Text.literal("SpotyHUD  -  drag the HUD to move it");
        ctx.drawText(textRenderer, title, width / 2 - textRenderer.getWidth(title) / 2, height - PANEL_H - 12, 0xFFFFFFFF, true);
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void removed() {
        HudRenderer.setPreview(false);
        SpotyConfig.save();
    }

    /** Slider with a label and a 0.05 step. */
    private static final class ValueSlider extends SliderWidget {
        private final String label, suffix;
        private final double min, max;
        private final DoubleConsumer onChange;

        ValueSlider(int x, int y, int w, int h, String label, double min, double max, double current,
                    DoubleConsumer onChange, String suffix) {
            super(x, y, w, h, Text.empty(), (current - min) / (max - min));
            this.label = label;
            this.suffix = suffix;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            updateMessage();
        }

        private double current() {
            return Math.round((min + value * (max - min)) * 20.0) / 20.0;
        }

        @Override
        protected void updateMessage() {
            if (label == null) return;
            setMessage(Text.literal(String.format(Locale.ROOT, "%s: %.2f%s", label, current(), suffix)));
        }

        @Override
        protected void applyValue() {
            onChange.accept(current());
        }
    }
}
