package com.autospawn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Конфіг мода. Файл: config/autospawn.json
 */
public class AutoSpawnConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("autospawn.json");
    private static AutoSpawnConfig INSTANCE;

    /** Головний вимикач. */
    public boolean enabled = true;
    /** Команда без слеша. */
    public String command = "spawn";
    /** Ніки, за якими стежимо. */
    public List<String> players = new ArrayList<>();

    /** Як часто перевіряти список гравців (тіки; 4 = 5 разів на секунду). */
    public int pollIntervalTicks = 4;
    /** Скільки секунд після заходу на сервер поява гравця вважається "він уже був тут". */
    public int warmupSeconds = 12;
    /** Мінімальний інтервал між будь-якими двома відправками команди (секунди). */
    public int cooldownSeconds = 3;
    /** Мінімальний інтервал між спрацюваннями по одному й тому ж гравцю (секунди). */
    public int perPlayerCooldownSeconds = 20;
    /** Скільки разів продублювати команду (1–3). Страхує від загубленого пакета. */
    public int resendCount = 1;

    /** Писати в чат про спрацювання. */
    public boolean announce = true;
    /** true — реагувати лише на вхід гравця; false — також якщо він уже онлайн, коли заходиш ти. */
    public boolean triggerOnlyOnJoin = false;

    /** Додаткове джерело: системні повідомлення чату про вхід. */
    public boolean detectViaChat = true;
    /** Фрагменти повідомлень, які означають "гравець зайшов". */
    public List<String> chatJoinPatterns = new ArrayList<>(Arrays.asList(
            "joined the game", "joined the server", "connected",
            "приєднався", "приєдналася", "зайшов", "зайшла", "увійшов", "підключився",
            "зашёл", "зашел", "присоединился", "подключился"
    ));

    /** Кеш ніків у нижньому регістрі. */
    private transient Set<String> watchedLower;

    public static AutoSpawnConfig get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    private static AutoSpawnConfig load() {
        if (Files.exists(PATH)) {
            try {
                AutoSpawnConfig cfg = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), AutoSpawnConfig.class);
                if (cfg != null) {
                    cfg.sanitize();
                    return cfg;
                }
            } catch (Exception e) {
                AutoSpawnClient.LOGGER.error("[AutoSpawn] не вдалося прочитати autospawn.json, беру дефолт", e);
            }
        }
        AutoSpawnConfig cfg = new AutoSpawnConfig();
        cfg.save();
        return cfg;
    }

    private void sanitize() {
        if (players == null) players = new ArrayList<>();
        if (chatJoinPatterns == null) chatJoinPatterns = new ArrayList<>();
        if (command == null || command.isBlank()) command = "spawn";
        pollIntervalTicks = clamp(pollIntervalTicks, 1, 40);
        warmupSeconds = clamp(warmupSeconds, 0, 120);
        cooldownSeconds = clamp(cooldownSeconds, 0, 300);
        perPlayerCooldownSeconds = clamp(perPlayerCooldownSeconds, 0, 3600);
        resendCount = clamp(resendCount, 1, 3);
        players.removeIf(p -> p == null || p.isBlank());
        watchedLower = null;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public void save() {
        sanitize();
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            AutoSpawnClient.LOGGER.error("[AutoSpawn] не вдалося зберегти autospawn.json", e);
        }
    }

    /** Ніки у нижньому регістрі (кешовано — без алокацій на кожен тік). */
    public Set<String> watchedLower() {
        if (watchedLower == null) {
            Set<String> set = new HashSet<>(Math.max(4, players.size() * 2));
            for (String p : players) {
                if (p != null && !p.isBlank()) set.add(p.trim().toLowerCase(Locale.ROOT));
            }
            watchedLower = set;
        }
        return watchedLower;
    }

    public boolean isWatched(String name) {
        return name != null && watchedLower().contains(name.trim().toLowerCase(Locale.ROOT));
    }

    public boolean addPlayer(String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        if (trimmed.isEmpty() || isWatched(trimmed)) return false;
        players.add(trimmed);
        watchedLower = null;
        save();
        return true;
    }

    public boolean removePlayer(String name) {
        boolean removed = players.removeIf(p -> p.equalsIgnoreCase(name.trim()));
        if (removed) {
            watchedLower = null;
            save();
        }
        return removed;
    }

    /** Команда без початкових слешів. */
    public String normalizedCommand() {
        String c = command == null ? "" : command.trim();
        while (c.startsWith("/")) c = c.substring(1);
        return c.isEmpty() ? "spawn" : c;
    }
}
