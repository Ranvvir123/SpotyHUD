package com.spotyhud.input;

import com.spotyhud.client.SpotyHudClient;
import com.spotyhud.config.ConfigScreen;
import com.spotyhud.media.MediaCommand;
import com.spotyhud.media.MediaService;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** All rebindable keys (Options > Controls > SpotyHUD). */
public final class KeyBindings {
    private static KeyBinding playPause, next, previous, openConfig;

    private KeyBindings() {}

    public static void register() {
        // 'P' is already "Social Interactions" in vanilla, so ']' is the default for play/pause.
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(SpotyHudClient.MOD_ID, "main"));
        playPause = reg("key.spotyhud.play_pause", GLFW.GLFW_KEY_RIGHT_BRACKET, category);
        next = reg("key.spotyhud.next", GLFW.GLFW_KEY_PERIOD, category);
        previous = reg("key.spotyhud.previous", GLFW.GLFW_KEY_COMMA, category);
        openConfig = reg("key.spotyhud.open_config", GLFW.GLFW_KEY_O, category);
    }

    private static KeyBinding reg(String translationKey, int key, KeyBinding.Category category) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(translationKey, InputUtil.Type.KEYSYM, key, category));
    }

    public static void tick(MinecraftClient client) {
        while (playPause.wasPressed()) MediaService.get().send(MediaCommand.PLAY_PAUSE);
        while (next.wasPressed()) MediaService.get().send(MediaCommand.NEXT);
        while (previous.wasPressed()) MediaService.get().send(MediaCommand.PREVIOUS);
        while (openConfig.wasPressed()) {
            if (client.currentScreen == null) client.setScreen(new ConfigScreen());
        }
    }
}
