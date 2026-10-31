package com.chaossmp.chaoscore;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
    this.file = new File(plugin.getDataFolder(), "players.yml");
    load();
}

public void load() {
    if (!plugin.getDataFolder().exists()
            && !plugin.getDataFolder().mkdirs()) {
        plugin.getLogger().severe("Не удалось создать папку плагина.");
        return;
    }

    if (!file.exists()) {
        try {
            if (!file.createNewFile()) {
                plugin.getLogger().warning("Не удалось создать players.yml.");
            }
        } catch (IOException exception) {
            plugin.getLogger().severe(
                    "Ошибка создания players.yml: " + exception.getMessage()
            );
            return;
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

    for (String key : players.getKeys(false)) {
        try {
            UUID uuid = UUID.fromString(key);
            String path = "players." + key;

            int max = Math.max(0,
                    plugin.getConfig().getInt("settings.max-chaos", 25));

            chaosCache.put(uuid, Math.max(0,
                    Math.min(data.getInt(path + ".chaos", 0), max)));

            killsCache.put(uuid, Math.max(0,
                    data.getInt(path + ".kills", 0)));

            deathsCache.put(uuid, Math.max(0,
                    data.getInt(path + ".deaths", 0)));

        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning(
                    "Пропущена некорректная запись игрока: " + key
            );
        }
    }
}

public int getChaos(UUID uuid) {
    return chaosCache.getOrDefault(uuid, 0);
}

public int getKills(UUID uuid) {
    return killsCache.getOrDefault(uuid, 0);
}

public int getDeaths(UUID uuid) {
    return deathsCache.getOrDefault(uuid, 0);
}

public int getPowerLevel(UUID uuid) {
    if (!plugin.getConfig().getBoolean("power-levels.enabled", true)) {
        return 0;
    }

    int chaos = getChaos(uuid);
    int level = 0;

    for (int i = 1; i <= 5; i++) {
        int required = plugin.getConfig().getInt(
                "power-levels.thresholds." + i,
                i * 5
        );

        if (chaos >= required) {
            level = i;
        }
    }

    return level;
}

public void setChaos(UUID uuid, int amount) {
    int max = Math.max(0,
            plugin.getConfig().getInt("settings.max-chaos", 25));

    chaosCache.put(uuid, Math.max(0, Math.min(amount, max)));
}

public void addChaos(UUID uuid, int amount) {
    if (amount <= 0) {
        return;
    }

    setChaos(uuid, getChaos(uuid) + amount);
}

public void removeChaos(UUID uuid, int amount) {
    if (amount <= 0) {
        return;
    }

    setChaos(uuid, getChaos(uuid) - amount);
}

public void addKill(UUID uuid) {
    killsCache.merge(uuid, 1, Integer::sum);
}

public void addDeath(UUID uuid) {
    deathsCache.merge(uuid, 1, Integer::sum);
}

public Map<UUID, Integer> getAllChaos() {
    return Collections.unmodifiableMap(new HashMap<>(chaosCache));
}

public void save() {
    if (data == null) {
        return;
    }

    data.set("players", null);

    for (UUID uuid : chaosCache.keySet()) {
        String path = "players." + uuid;
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

        data.set(path + ".name", player.getName());
        data.set(path + ".chaos", getChaos(uuid));
        data.set(path + ".kills", getKills(uuid));
        data.set(path + ".deaths", getDeaths(uuid));
    }

    try {
        data.save(file);
    } catch (IOException exception) {
        plugin.getLogger().severe(
                "Не удалось сохранить players.yml: " + exception.getMessage()
        );
    }
}
  
}
