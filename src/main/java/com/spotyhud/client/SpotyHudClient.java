package com.spotyhud.client;

import com.spotyhud.config.SpotyConfig;
import com.spotyhud.hud.HudRenderer;
import com.spotyhud.input.KeyBindings;
import com.spotyhud.media.MediaService;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SpotyHudClient implements ClientModInitializer {
    public static final String MOD_ID = "spotyhud";
    public static final Logger LOGGER = LoggerFactory.getLogger("SpotyHUD");

    @Override
    public void onInitializeClient() {
        SpotyConfig.load();
        MediaService.start();
        KeyBindings.register();

        HudRenderer hud = new HudRenderer();
        // HudRenderCallback is deprecated; HudElementRegistry is the 1.21.6+ replacement.
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of(MOD_ID, "now_playing"), hud);

        ClientTickEvents.END_CLIENT_TICK.register(KeyBindings::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            MediaService.stop();
            hud.release();
            SpotyConfig.save();
        });
    }
}
