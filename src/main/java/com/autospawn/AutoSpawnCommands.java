package com.autospawn;

import com.autospawn.ui.WatchListScreen;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Клієнтські команди /autospawn — дубль меню на випадок, коли зручніше з чату.
 */
public final class AutoSpawnCommands {

    private AutoSpawnCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                ClientCommandManager.literal("autospawn")
                        .executes(ctx -> {
                            openMenu();
                            return 1;
                        })
                        .then(ClientCommandManager.literal("add")
                                .then(ClientCommandManager.argument("nick", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String nick = StringArgumentType.getString(ctx, "nick");
                                            boolean added = AutoSpawnConfig.get().addPlayer(nick);
                                            feedback(ctx.getSource(), added
                                                    ? Text.literal("Додано: " + nick).formatted(Formatting.GREEN)
                                                    : Text.literal(nick + " вже у списку").formatted(Formatting.YELLOW));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("nick", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String nick = StringArgumentType.getString(ctx, "nick");
                                            boolean removed = AutoSpawnConfig.get().removePlayer(nick);
                                            feedback(ctx.getSource(), removed
                                                    ? Text.literal("Видалено: " + nick).formatted(Formatting.GREEN)
                                                    : Text.literal(nick + " немає у списку").formatted(Formatting.YELLOW));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("list")
                                .executes(ctx -> {
                                    AutoSpawnConfig cfg = AutoSpawnConfig.get();
                                    if (cfg.players.isEmpty()) {
                                        feedback(ctx.getSource(), Text.literal("Список порожній").formatted(Formatting.GRAY));
                                    } else {
                                        feedback(ctx.getSource(), Text.literal("У списку (" + cfg.players.size() + "): ")
                                                .formatted(Formatting.GOLD)
                                                .append(Text.literal(String.join(", ", cfg.players)).formatted(Formatting.WHITE)));
                                    }
                                    return 1;
                                }))
                        .then(ClientCommandManager.literal("toggle")
                                .executes(ctx -> {
                                    AutoSpawnConfig cfg = AutoSpawnConfig.get();
                                    cfg.enabled = !cfg.enabled;
                                    cfg.save();
                                    feedback(ctx.getSource(), cfg.enabled
                                            ? Text.literal("AutoSpawn увімкнено").formatted(Formatting.GREEN)
                                            : Text.literal("AutoSpawn вимкнено").formatted(Formatting.RED));
                                    return 1;
                                }))
                        .then(ClientCommandManager.literal("run")
                                .executes(ctx -> {
                                    AutoSpawnClient client = AutoSpawnClient.instance();
                                    if (client != null) client.triggerManually();
                                    return 1;
                                }))
                        .then(ClientCommandManager.literal("menu")
                                .executes(ctx -> {
                                    openMenu();
                                    return 1;
                                }))));
    }

    private static void feedback(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, Text text) {
        source.sendFeedback(Text.literal("[AutoSpawn] ").formatted(Formatting.GOLD).append(text));
    }

    /** Екран не можна відкрити прямо під час виконання команди — ставимо в чергу клієнта. */
    private static void openMenu() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> client.setScreen(new WatchListScreen(null)));
    }
}
