package com.spotyhud.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.spotyhud.client.SpotyHudClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Persistent settings, stored as {@code .minecraft/config/spotyhud.json}. */
public final class SpotyConfig {
    public static final float MIN_SCALE = 0.5f, MAX_SCALE = 2.0f;

    public boolean enabled = true;
    /** Position as a 0..1 fraction of the free screen space, so it survives resolution / GUI-scale changes. */
    public float posX = 0.01f;
    public float posY = 0.02f;
    public float scale = 1.0f;
    public float opacity = 0.80f;
    /** Frosted-glass styling (soft glow, edge highlight, drop shadow). */
    public boolean frostedGlass = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SpotyConfig instance = new SpotyConfig();

    public static SpotyConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("spotyhud.json");
    }

    public static synchronized void load() {
        Path p = path();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                SpotyConfig loaded = GSON.fromJson(r, SpotyConfig.class);
                if (loaded != null) instance = loaded;
            } catch (Exception e) {
                SpotyHudClient.LOGGER.warn("Could not read spotyhud.json, using defaults: {}", e.toString());
            }
        }
        instance.sanitize();
        save();
    }

    public static synchronized void save() {
        instance.sanitize();
        Path p = path();
        try {
            Files.createDirectories(p.getParent());
            Path tmp = p.resolveSibling("spotyhud.json.tmp");
            Files.writeString(tmp, GSON.toJson(instance), StandardCharsets.UTF_8);
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            SpotyHudClient.LOGGER.warn("Could not save spotyhud.json: {}", e.toString());
        }
    }

    private void sanitize() {
        posX = clamp(posX, 0f, 1f);
        posY = clamp(posY, 0f, 1f);
        scale = clamp(scale, MIN_SCALE, MAX_SCALE);
        opacity = clamp(opacity, 0.2f, 1f);
    }

    private static float clamp(float v, float lo, float hi) {
        return Float.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }
}
