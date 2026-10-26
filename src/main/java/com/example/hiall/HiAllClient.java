package com.example.hiall;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;

public class HiAllClient implements ClientModInitializer {
    static String text = "hi lol";
    static double delaySeconds = 3.0;
    static String command = "msg";
    static boolean autoStart = false;
    static final Set<String> blacklist = new TreeSet<>(); // stored lowercase

    static final Set<String> done = new HashSet<>();
    static boolean running = false;
    static int cooldown = 0;

    private static KeyBinding toggleKey;
    private static KeyBinding openKey;

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("hiall.properties");
    }

    static void load() {
        try {
            Path p = configPath();
            if (!Files.exists(p)) return;
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(p)) { props.load(in); }
            text = props.getProperty("text", text);
            command = props.getProperty("command", command);
            delaySeconds = Double.parseDouble(props.getProperty("delay", String.valueOf(delaySeconds)));
            autoStart = Boolean.parseBoolean(props.getProperty("autoStart", "false"));
            for (String n : props.getProperty("blacklist", "").split(",")) {
                if (!n.trim().isEmpty()) blacklist.add(n.trim().toLowerCase(Locale.ROOT));
            }
        } catch (Exception ignored) {}
    }

    static void save() {
        try {
            Properties props = new Properties();
            props.setProperty("text", text);
            props.setProperty("command", command);
            props.setProperty("delay", String.valueOf(delaySeconds));
            props.setProperty("autoStart", String.valueOf(autoStart));
            props.setProperty("blacklist", String.join(",", blacklist));
            try (OutputStream out = Files.newOutputStream(configPath())) { props.store(out, "HiAll settings"); }
        } catch (Exception ignored) {}
    }

    static int delayTicks() {
        return (int) Math.max(1, Math.round(delaySeconds * 20));
    }

    static String blacklistText() {
        return blacklist.isEmpty() ? "Blacklist is empty." : "Blacklist: " + String.join(", ", blacklist);
    }

    static int addBlacklist(String raw) {
        int added = 0;
        for (String n : raw.trim().split("[\\s,]+")) {
            if (!n.isEmpty() && blacklist.add(n.toLowerCase(Locale.ROOT))) added++;
        }
        save();
        return added;
    }

    static int removeBlacklist(String raw) {
        int removed = 0;
        for (String n : raw.trim().split("[\\s,]+")) {
            if (blacklist.remove(n.toLowerCase(Locale.ROOT))) removed++;
        }
        save();
        return removed;
    }

    static String preview() {
        return "/" + command + " Steve " + text.replace("{player}", "Steve");
    }

    static String toggleKeyName() {
        return toggleKey == null ? "K" : toggleKey.getBoundKeyLocalizedText().getString();
    }

    static void setRunning(boolean value) {
        running = value;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("HiAll: " + (running ? "running" : "paused")), false);
        }
    }

    @Override
    public void onInitializeClient() {
        load();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.hiall.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.hiall"));
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.hiall.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.hiall"));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("hiall")
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(Text.literal(
                        "HiAll | text: \"" + text + "\" | delay: " + delaySeconds + "s | command: /" + command
                        + " | blacklisted: " + blacklist.size()
                        + " | " + (running ? "running" : "paused")));
                    return 1;
                })
                .then(ClientCommandManager.literal("gui").executes(ctx -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.send(() -> mc.setScreen(new HiAllScreen()));
                    return 1;
                }))
                .then(ClientCommandManager.literal("text")
                    .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            text = StringArgumentType.getString(ctx, "message");
                            save();
                            ctx.getSource().sendFeedback(Text.literal("Text set to: " + text));
                            return 1;
                        })))
                .then(ClientCommandManager.literal("delay")
                    .then(ClientCommandManager.argument("seconds", DoubleArgumentType.doubleArg(0.5, 600))
                        .executes(ctx -> {
                            delaySeconds = DoubleArgumentType.getDouble(ctx, "seconds");
                            save();
                            ctx.getSource().sendFeedback(Text.literal("Delay set to " + delaySeconds + "s"));
                            return 1;
                        })))
                .then(ClientCommandManager.literal("command")
                    .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .executes(ctx -> {
                            command = StringArgumentType.getString(ctx, "name").replace("/", "");
                            save();
                            ctx.getSource().sendFeedback(Text.literal("Command set to /" + command));
                            return 1;
                        })))
                .then(ClientCommandManager.literal("blacklist")
                    .executes(ctx -> {
                        ctx.getSource().sendFeedback(Text.literal(blacklistText()));
                        return 1;
                    })
                    .then(ClientCommandManager.argument("names", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            int added = addBlacklist(StringArgumentType.getString(ctx, "names"));
                            ctx.getSource().sendFeedback(Text.literal("Blacklisted " + added + ". " + blacklistText()));
                            return 1;
                        })))
                .then(ClientCommandManager.literal("unblacklist")
                    .then(ClientCommandManager.argument("names", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            int removed = removeBlacklist(StringArgumentType.getString(ctx, "names"));
                            ctx.getSource().sendFeedback(Text.literal("Removed " + removed + ". " + blacklistText()));
                            return 1;
                        })))
                .then(ClientCommandManager.literal("reset")
                    .executes(ctx -> {
                        done.clear();
                        ctx.getSource().sendFeedback(Text.literal("Cleared the already-messaged list."));
                        return 1;
                    }))
            )
        );

        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
            done.clear();
            cooldown = 100; // short wait after joining so the player list can fill in
            running = autoStart;
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.wasPressed()) mc.setScreen(new HiAllScreen());
            while (toggleKey.wasPressed()) setRunning(!running);

            if (!running || mc.player == null || mc.getNetworkHandler() == null) return;
            if (cooldown > 0) { cooldown--; return; }
            if (text.isBlank()) {
                running = false;
                mc.player.sendMessage(Text.literal("HiAll: set a message first"), false);
                return;
            }

            String self = mc.player.getGameProfile().getName();
            List<String> candidates = new ArrayList<>();
            for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                String name = entry.getProfile().getName();
                if (name.equalsIgnoreCase(self)) continue;
                if (done.contains(name)) continue;
                if (blacklist.contains(name.toLowerCase(Locale.ROOT))) continue;
                candidates.add(name);
            }
            if (candidates.isEmpty()) return; // nobody left; waits for new players

            String name = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            done.add(name);
            String message = text.replace("{player}", name);
            mc.getNetworkHandler().sendChatCommand(command + " " + name + " " + message);
            cooldown = delayTicks();
        });
    }
}
