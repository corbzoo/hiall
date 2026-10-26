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
    private static final boolean AUTO_START_ON_JOIN = false;

    private static String text = "hi lol";
    private static double delaySeconds = 3.0;
    private static String command = "msg";
    private static final Set<String> blacklist = new TreeSet<>(); // stored lowercase

    private static final Set<String> done = new HashSet<>();
    private static boolean running = false;
    private static int cooldown = 0;
    private static KeyBinding toggleKey;

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("hiall.properties");
    }

    private static void load() {
        try {
            Path p = configPath();
            if (!Files.exists(p)) return;
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(p)) { props.load(in); }
            text = props.getProperty("text", text);
            command = props.getProperty("command", command);
            delaySeconds = Double.parseDouble(props.getProperty("delay", String.valueOf(delaySeconds)));
            String bl = props.getProperty("blacklist", "");
            for (String n : bl.split(",")) {
                if (!n.trim().isEmpty()) blacklist.add(n.trim().toLowerCase(Locale.ROOT));
            }
        } catch (Exception ignored) {}
    }

    private static void save() {
        try {
            Properties props = new Properties();
            props.setProperty("text", text);
            props.setProperty("command", command);
            props.setProperty("delay", String.valueOf(delaySeconds));
            props.setProperty("blacklist", String.join(",", blacklist));
            try (OutputStream out = Files.newOutputStream(configPath())) { props.store(out, "HiAll settings"); }
        } catch (Exception ignored) {}
    }

    private static int delayTicks() {
        return (int) Math.max(1, Math.round(delaySeconds * 20));
    }

    private static String blacklistText() {
        return blacklist.isEmpty() ? "Blacklist is empty." : "Blacklist: " + String.join(", ", blacklist);
    }

    @Override
    public void onInitializeClient() {
        load();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.hiall.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.hiall"));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("hiall")
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(Text.literal(
                        "HiAll | text: \"" + text + "\" | delay: " + delaySeconds + "s | command: /" + command
                        + " | blacklisted: " + blacklist.size()
                        + " | " + (running ? "running" : "paused")));
                    return 1;
                })
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
                            int added = 0;
                            for (String n : StringArgumentType.getString(ctx, "names").trim().split("\\s+")) {
                                if (!n.isEmpty() && blacklist.add(n.toLowerCase(Locale.ROOT))) added++;
                            }
                            save();
                            ctx.getSource().sendFeedback(Text.literal("Blacklisted " + added + ". " + blacklistText()));
                            return 1;
                        })))
                .then(ClientCommandManager.literal("unblacklist")
                    .then(ClientCommandManager.argument("names", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            int removed = 0;
                            for (String n : StringArgumentType.getString(ctx, "names").trim().split("\\s+")) {
                                if (blacklist.remove(n.toLowerCase(Locale.ROOT))) removed++;
                            }
                            save();
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
