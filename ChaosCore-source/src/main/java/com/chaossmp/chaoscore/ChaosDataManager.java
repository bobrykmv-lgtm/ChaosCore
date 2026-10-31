package com.chaossmp.chaoscore;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ChaosDataManager {

    private final JavaPlugin plugin;
    private final File file;

    private YamlConfiguration data;

    private final Map<UUID, Integer> chaosCache = new HashMap<>();
    private final Map<UUID, Integer> killsCache = new HashMap<>();
    private final Map<UUID, Integer> deathsCache = new HashMap<>();

    public ChaosDataManager(JavaPlugin plugin) {
        this.plugin = plugin;

        String fileName = plugin.getConfig().getString(
                "storage.file",
                "players.yml"
        );

        this.file = new File(plugin.getDataFolder(), fileName);

        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().severe(
                    "Не удалось создать папку для файла игроков."
            );
        }

        load();
    }

    private void load() {
        if (!file.exists()) {
            try {
                if (!file.createNewFile()) {
                    plugin.getLogger().warning(
                            "Не удалось создать файл players.yml."
                    );
                }
            } catch (IOException exception) {
                plugin.getLogger().severe(
                        "Ошибка создания players.yml: "
                                + exception.getMessage()
                );
            }
        }

        data = YamlConfiguration.loadConfiguration(file);

        chaosCache.clear();
        killsCache.clear();
        deathsCache.clear();

        ConfigurationSection players = data.getConfigurationSection("players");

        if (players == null) {
            return;
        }

        int maxChaos = getMaxChaos();

        for (String key : players.getKeys(false)) {
            UUID uuid;

            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning(
                        "Пропущена запись с некорректным UUID: " + key
                );
                continue;
            }

            String path = "players." + key;

            int chaos = Math.max(
                    0,
                    Math.min(maxChaos, data.getInt(path + ".chaos", 0))
            );

            int kills = Math.max(0, data.getInt(path + ".kills", 0));
            int deaths = Math.max(0, data.getInt(path + ".deaths", 0));

            chaosCache.put(uuid, chaos);
            killsCache.put(uuid, kills);
            deathsCache.put(uuid, deaths);
        }

        plugin.getLogger().info(
                "Загружена статистика игроков: " + chaosCache.size()
        );
    }

    private int getMaxChaos() {
        return Math.max(
                0,
                plugin.getConfig().getInt("settings.max-chaos", 25)
        );
    }

    private void ensurePlayer(UUID uuid) {
        chaosCache.putIfAbsent(uuid, 0);
        killsCache.putIfAbsent(uuid, 0);
        deathsCache.putIfAbsent(uuid, 0);
    }

    public int getChaos(UUID uuid) {
        ensurePlayer(uuid);
        return chaosCache.get(uuid);
    }

    public int getKills(UUID uuid) {
        ensurePlayer(uuid);
        return killsCache.get(uuid);
    }

    public int getDeaths(UUID uuid) {
        ensurePlayer(uuid);
        return deathsCache.get(uuid);
    }

    public int getPowerLevel(UUID uuid) {
        int chaos = getChaos(uuid);

        if (!plugin.getConfig().getBoolean("power-levels.enabled", true)) {
            return 0;
        }

        ConfigurationSection thresholds =
                plugin.getConfig().getConfigurationSection(
                        "power-levels.thresholds"
                );

        if (thresholds == null) {
            return 0;
        }

        int power = 0;

        for (String key : thresholds.getKeys(false)) {
            try {
                int level = Integer.parseInt(key);
                int requiredChaos = thresholds.getInt(key);

                if (chaos >= requiredChaos && level > power) {
                    power = level;
                }
            } catch (NumberFormatException exception) {
                plugin.getLogger().warning(
                        "Некорректный уровень силы в config.yml: " + key
                );
            }
        }

        return power;
    }

    public void setChaos(UUID uuid, int amount) {
        ensurePlayer(uuid);

        int value = Math.max(0, Math.min(getMaxChaos(), amount));
        chaosCache.put(uuid, value);
    }

    public void addChaos(UUID uuid, int amount) {
        if (amount <= 0) {
            ensurePlayer(uuid);
            return;
        }

        ensurePlayer(uuid);

        long result = (long) chaosCache.get(uuid) + amount;
        chaosCache.put(uuid, (int) Math.min(getMaxChaos(), result));
    }

    public void removeChaos(UUID uuid, int amount) {
        if (amount <= 0) {
            ensurePlayer(uuid);
            return;
        }

        ensurePlayer(uuid);

        int result = Math.max(0, chaosCache.get(uuid) - amount);
        chaosCache.put(uuid, result);
    }

    public void addKill(UUID uuid) {
        ensurePlayer(uuid);
        killsCache.put(uuid, killsCache.get(uuid) + 1);
    }

    public void addDeath(UUID uuid) {
        ensurePlayer(uuid);
        deathsCache.put(uuid, deathsCache.get(uuid) + 1);
    }

    public Map<UUID, Integer> getAllChaos() {
        return Collections.unmodifiableMap(
                new HashMap<>(chaosCache)
        );
    }

    public void save() {
        if (data == null) {
            data = new YamlConfiguration();
        }

        data.set("players", null);

        for (UUID uuid : chaosCache.keySet()) {
            ensurePlayer(uuid);

            String path = "players." + uuid;

            data.set(path + ".chaos", chaosCache.get(uuid));
            data.set(path + ".kills", killsCache.get(uuid));
            data.set(path + ".deaths", deathsCache.get(uuid));
        }

        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe(
                    "Не удалось сохранить статистику игроков: "
                            + exception.getMessage()
            );
        }
    }
}
