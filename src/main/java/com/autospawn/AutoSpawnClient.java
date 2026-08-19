package com.autospawn;

import com.autospawn.ui.WatchListScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Точка входу мода. Стежить за списком гравців і виконує команду, щойно ціль з'являється.
 *
 * Детекція йде з трьох незалежних джерел, щоб гарантовано не пропустити гравця:
 *   1) таблиця гравців сервера (tab-list) — і за ніком профілю, і за відображуваним ім'ям;
 *   2) сутності гравців у завантаженому світі (працює навіть коли сервер ховає tab-list);
 *   3) системні повідомлення чату про вхід (працює навіть коли гравця немає в tab-list).
 */
public class AutoSpawnClient implements ClientModInitializer {

    public static final String MOD_ID = "autospawn";
    public static final Logger LOGGER = LoggerFactory.getLogger("AutoSpawn");

    private static AutoSpawnClient instance;
    private static KeyBinding openMenuKey;

    /** Ніки зі списку (нижній регістр), які були онлайн на минулій перевірці. */
    private final Set<String> onlineWatched = new HashSet<>();
    /** Буфер поточної перевірки — щоб не алокувати новий сет кожні кілька тіків. */
    private final Set<String> scanBuffer = new HashSet<>();
    /** Час останнього спрацювання по кожному гравцю. */
    private final Map<String, Long> lastTriggerPerPlayer = new HashMap<>();

    private long lastGlobalTrigger;
    private long warmupEndsAt;
    private int tickCounter;

    /** Черга відправки: команда йде лише тоді, коли клієнт справді може її надіслати. */
    private int pendingSends;
    private long nextSendAt;
    private String pendingWho = "";
    private boolean pendingAlreadyOnline;
    private boolean pendingAnnounce;

    public static AutoSpawnClient instance() {
        return instance;
    }

    public static KeyBinding openMenuKey() {
        return openMenuKey;
    }

    /** Для індикаторів у меню: хто зі списку зараз онлайн. */
    public Set<String> onlineWatched() {
        return onlineWatched;
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        AutoSpawnConfig.get();

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autospawn.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_BRACKET,
                "key.categories.autospawn"
        ));

        AutoSpawnCommands.register();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            onlineWatched.clear();
            lastTriggerPerPlayer.clear();
            lastGlobalTrigger = 0L;
            tickCounter = 0;
            clearPending();
            // Поки триває "розігрів", поява гравця вважається "він уже був тут":
            // tab-list і чанки з гравцями приходять із затримкою в кілька секунд.
            warmupEndsAt = System.currentTimeMillis() + AutoSpawnConfig.get().warmupSeconds * 1000L;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            onlineWatched.clear();
            clearPending();
            warmupEndsAt = 0L;
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                onSystemMessage(message.getString());
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        LOGGER.info("[AutoSpawn] завантажено");
    }

    // ------------------------------------------------------------------- тік

    private void onClientTick(MinecraftClient client) {
        while (openMenuKey.wasPressed()) {
            client.setScreen(new WatchListScreen(client.currentScreen));
        }

        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (client.player == null || client.world == null || handler == null) {
            if (!onlineWatched.isEmpty()) onlineWatched.clear();
            return;
        }

        // Спершу дошлемо те, що не встигли надіслати раніше.
        flushPending(client, handler);

        AutoSpawnConfig cfg = AutoSpawnConfig.get();
        if (!cfg.enabled || cfg.watchedLower().isEmpty()) {
            return;
        }

        if (++tickCounter < cfg.pollIntervalTicks) {
            return;
        }
        tickCounter = 0;

        scan(client, handler, cfg);
    }

    /** Повна перевірка присутності: tab-list + сутності світу. */
    private void scan(MinecraftClient client, ClientPlayNetworkHandler handler, AutoSpawnConfig cfg) {
        Set<String> watched = cfg.watchedLower();
        String self = selfName(client);

        scanBuffer.clear();
        collectFromTabList(handler, watched, self, scanBuffer);
        collectFromWorld(client, watched, self, scanBuffer);

        List<String> appeared = null;
        for (String name : scanBuffer) {
            if (!onlineWatched.contains(name)) {
                if (appeared == null) appeared = new ArrayList<>(2);
                appeared.add(name);
            }
        }

        onlineWatched.clear();
        onlineWatched.addAll(scanBuffer);

        if (appeared != null) {
            boolean alreadyOnline = System.currentTimeMillis() < warmupEndsAt;
            if (alreadyOnline && cfg.triggerOnlyOnJoin) {
                return; // під час розігріву це не "зайшов", а "вже був тут"
            }
            queueTrigger(cfg, appeared, alreadyOnline);
        }
    }

    private void collectFromTabList(ClientPlayNetworkHandler handler, Set<String> watched, String self, Set<String> out) {
        for (PlayerListEntry entry : handler.getPlayerList()) {
            String profileName = entry.getProfile() == null ? null : entry.getProfile().getName();
            if (profileName != null && !profileName.isBlank()) {
                String lower = profileName.toLowerCase(Locale.ROOT);
                if (!lower.equals(self) && watched.contains(lower)) {
                    out.add(lower);
                    continue;
                }
            }
            // Сервери з кастомним tab-list можуть віддавати нік лише у display name,
            // ще й з префіксом ранга: "[VIP] Nick".
            Text display = entry.getDisplayName();
            if (display != null) {
                matchInText(display.getString(), watched, self, out);
            }
        }
    }

    private void collectFromWorld(MinecraftClient client, Set<String> watched, String self, Set<String> out) {
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            String name = player.getGameProfile() == null ? null : player.getGameProfile().getName();
            if (name == null || name.isBlank()) {
                name = player.getName().getString();
            }
            String lower = name.toLowerCase(Locale.ROOT);
            if (!lower.equals(self) && watched.contains(lower)) {
                out.add(lower);
            }
        }
    }

    /** Шукає будь-який нік зі списку як окреме слово всередині рядка. */
    private void matchInText(String raw, Set<String> watched, String self, Set<String> out) {
        if (raw == null || raw.isEmpty()) return;
        String lower = raw.toLowerCase(Locale.ROOT);
        for (String name : watched) {
            if (!name.equals(self) && containsWord(lower, name)) {
                out.add(name);
            }
        }
    }

    /** Входження ніка як цілого слова — щоб "Bob" не спрацював усередині "Bobby". */
    static boolean containsWord(String haystackLower, String needleLower) {
        int from = 0;
        while (true) {
            int idx = haystackLower.indexOf(needleLower, from);
            if (idx < 0) return false;
            boolean leftOk = idx == 0 || !isNameChar(haystackLower.charAt(idx - 1));
            int end = idx + needleLower.length();
            boolean rightOk = end >= haystackLower.length() || !isNameChar(haystackLower.charAt(end));
            if (leftOk && rightOk) return true;
            from = idx + 1;
        }
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static String selfName(MinecraftClient client) {
        if (client.player != null && client.player.getGameProfile() != null) {
            String n = client.player.getGameProfile().getName();
            if (n != null && !n.isBlank()) return n.toLowerCase(Locale.ROOT);
        }
        return client.getSession().getUsername().toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ чат

    private void onSystemMessage(String raw) {
        AutoSpawnConfig cfg = AutoSpawnConfig.get();
        if (!cfg.enabled || !cfg.detectViaChat || cfg.watchedLower().isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) return;

        String lower = raw.toLowerCase(Locale.ROOT);
        boolean joinLike = false;
        for (String pattern : cfg.chatJoinPatterns) {
            if (pattern != null && !pattern.isBlank() && lower.contains(pattern.toLowerCase(Locale.ROOT))) {
                joinLike = true;
                break;
            }
        }
        if (!joinLike) return;

        Set<String> found = new LinkedHashSet<>();
        matchInText(raw, cfg.watchedLower(), selfName(client), found);
        if (found.isEmpty()) return;

        onlineWatched.addAll(found);
        queueTrigger(cfg, new ArrayList<>(found), false);
    }

    // --------------------------------------------------------------- тригер

    private void queueTrigger(AutoSpawnConfig cfg, List<String> names, boolean alreadyOnline) {
        long now = System.currentTimeMillis();

        List<String> due = new ArrayList<>(names.size());
        for (String name : names) {
            long last = lastTriggerPerPlayer.getOrDefault(name, 0L);
            if (now - last >= cfg.perPlayerCooldownSeconds * 1000L) {
                due.add(name);
            }
        }
        if (due.isEmpty()) return;
        if (now - lastGlobalTrigger < cfg.cooldownSeconds * 1000L) return;

        lastGlobalTrigger = now;
        for (String name : due) {
            lastTriggerPerPlayer.put(name, now);
        }

        pendingSends = cfg.resendCount;
        nextSendAt = now;
        pendingWho = String.join(", ", due);
        pendingAlreadyOnline = alreadyOnline;
        pendingAnnounce = cfg.announce;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.getNetworkHandler() != null) {
            flushPending(client, client.getNetworkHandler());
        }
    }

    /**
     * Відправляє команду. Якщо клієнт зараз не може її надіслати — завдання лишається
     * в черзі й піде наступного тіка, тож спрацювання не губиться.
     */
    private void flushPending(MinecraftClient client, ClientPlayNetworkHandler handler) {
        if (pendingSends <= 0) return;
        long now = System.currentTimeMillis();
        if (now < nextSendAt) return;

        AutoSpawnConfig cfg = AutoSpawnConfig.get();
        String command = cfg.normalizedCommand();

        handler.sendChatCommand(command);
        pendingSends--;
        nextSendAt = now + 900L;
        LOGGER.info("[AutoSpawn] {} -> /{}", pendingWho, command);

        if (pendingAnnounce && client.player != null) {
            pendingAnnounce = false;
            String reason = pendingAlreadyOnline ? "вже на сервері" : "зайшов на сервер";
            client.player.sendMessage(
                    Text.literal("[AutoSpawn] ").formatted(Formatting.GOLD)
                            .append(Text.literal(pendingWho).formatted(Formatting.RED))
                            .append(Text.literal(" " + reason + " — виконую /" + command).formatted(Formatting.GRAY)),
                    false);
        }

        if (pendingSends <= 0) {
            pendingWho = "";
        }
    }

    private void clearPending() {
        pendingSends = 0;
        pendingWho = "";
        pendingAnnounce = false;
    }

    /** Дозволяє спрацювати повторно, не перезаходячи на сервер. */
    public void resetCooldowns() {
        lastGlobalTrigger = 0L;
        lastTriggerPerPlayer.clear();
    }

    /** Ручний запуск команди з меню або з /autospawn run. */
    public void triggerManually() {
        AutoSpawnConfig cfg = AutoSpawnConfig.get();
        resetCooldowns();
        pendingSends = 1;
        nextSendAt = 0L;
        pendingWho = "ручний запуск";
        pendingAlreadyOnline = false;
        pendingAnnounce = cfg.announce;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.getNetworkHandler() != null) {
            flushPending(client, client.getNetworkHandler());
        }
    }
}
