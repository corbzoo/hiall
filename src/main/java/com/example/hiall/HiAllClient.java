package com.example.hiall;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

public class HiAllClient implements ClientModInitializer {
    private static final int DELAY_TICKS = 60;            // 3 seconds at 20 tps
    private static final boolean AUTO_START_ON_JOIN = false;

    private static final Set<String> done = new HashSet<>();
    private static boolean running = false;
    private static int cooldown = 0;
    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.hiall.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.hiall"));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
            done.clear();
            cooldown = 100; // short wait after joining so the player list can fill in
            running = AUTO_START_ON_JOIN;
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (toggleKey.wasPressed()) {
                running = !running;
                if (mc.player != null) {
                    mc.player.sendMessage(Text.literal("HiAll: " + (running ? "running" : "paused")), false);
                }
            }

            if (!running || mc.player == null || mc.getNetworkHandler() == null) return;
            if (cooldown > 0) { cooldown--; return; }

            String self = mc.player.getGameProfile().getName();
            for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                String name = entry.getProfile().getName();
                if (name.equals(self) || done.contains(name)) continue;

                done.add(name);
                mc.getNetworkHandler().sendChatCommand("msg " + name + " hi lol");
                cooldown = DELAY_TICKS;
                return; // one player per delay
            }
        });
    }
}
