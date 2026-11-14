package com.spotyhud.hud;

import com.spotyhud.media.ArtData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * Owns the single GPU texture holding the current cover. It is rebuilt (and the old one destroyed) whenever the
 * track or the required pixel size changes, and destroyed on {@link #release()} so nothing leaks.
 * Must only be used from the render thread.
 */
public final class AlbumArtTexture {
    private static final Identifier ID = Identifier.of("spotyhud", "album_art");

    private NativeImageBackedTexture texture;
    private ArtData source;
    private int pixelSize;

    /** @return the texture id, or null when there is no cover. */
    public Identifier get(ArtData art, int px) {
        if (art == null) {
            release();
            return null;
        }
        px = Math.max(16, Math.min(px, ArtData.MAX_SIZE));
        if (art != source || px != pixelSize || texture == null) build(art, px);
        return texture == null ? null : ID;
    }

    private void build(ArtData art, int px) {
        release();
        int[] scaled = ArtData.resample(art.pixels(), art.size(), Math.min(px, art.size()));
        int size = (int) Math.sqrt(scaled.length);
        NativeImage image = new NativeImage(size, size, false);
        float radius = size * (float) HudLayout.RADIUS / HudLayout.ART;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int p = scaled[y * size + x];
                float cov = cornerCoverage(x + 0.5f, y + 0.5f, size, radius);
                int a = Math.round(((p >>> 24) & 0xFF) * cov);
                image.setColorArgb(x, y, (a << 24) | (p & 0xFFFFFF));
            }
        }
        texture = new NativeImageBackedTexture(() -> "SpotyHUD album art", image);
        texture.upload();
        MinecraftClient.getInstance().getTextureManager().registerTexture(ID, texture);
        source = art;
        pixelSize = px;
    }

    /** Anti-aliased rounded-corner coverage, 0..1. */
    private static float cornerCoverage(float px, float py, int size, float r) {
        float dx = Math.max(Math.max(r - px, px - (size - r)), 0f);
        float dy = Math.max(Math.max(r - py, py - (size - r)), 0f);
        if (dx == 0f || dy == 0f) return 1f;
        float d = (float) Math.sqrt(dx * dx + dy * dy) - r;
        return Math.max(0f, Math.min(1f, 0.5f - d));
    }

    public void release() {
        if (texture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(ID);   // closes texture + image
            texture = null;
        }
        source = null;
    }
}
