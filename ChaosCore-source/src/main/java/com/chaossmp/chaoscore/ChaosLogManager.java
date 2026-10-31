package com.chaossmp.chaoscore;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ChaosLogManager {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration logs;

    public ChaosLogManager(JavaPlugin plugin) {
        this.plugin = plugin;

        String fileName = plugin.getConfig().getString(
                "logging.file",
                "logs.yml"
        );

        this.file = new File(plugin.getDataFolder(), fileName);

        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning(
                    "Не удалось создать папку для журнала действий."
            );
        }

        load();
    }

    private void load() {
        if (!file.exists()) {
            try {
                if (!file.createNewFile()) {
                    plugin.getLogger().warning(
                            "Не удалось создать файл logs.yml."
                    );
                }
            } catch (IOException exception) {
                plugin.getLogger().severe(
                        "Ошибка создания logs.yml: " + exception.getMessage()
                );
            }
        }

        logs = YamlConfiguration.loadConfiguration(file);

        if (!logs.contains("entries")) {
            logs.set("entries", new ArrayList<>());
            save();
        }
    }

    public void log(
            String administrator,
            String target,
            String action,
            String reason
    ) {
        if (!plugin.getConfig().getBoolean("logging.enabled", true)) {
            return;
        }

        List<Map<?, ?>> entries = logs.getMapList("entries");
        List<Map<String, Object>> updated = new ArrayList<>();

        for (Map<?, ?> entry : entries) {
            Map<String, Object> copy = new HashMap<>();

            for (Map.Entry<?, ?> item : entry.entrySet()) {
                copy.put(String.valueOf(item.getKey()), item.getValue());
            }

            updated.add(copy);
        }

        Map<String, Object> record = new HashMap<>();
        record.put("time", LocalDateTime.now().format(FORMATTER));
        record.put("administrator", administrator);
        record.put("target", target == null ? "-" : target);
        record.put("action", action);
        record.put("reason", reason == null || reason.isBlank() ? "-" : reason);

        updated.add(record);

        logs.set("entries", updated);
        save();
    }

    public List<Map<?, ?>> getRecentEntries(int limit) {
        List<Map<?, ?>> entries = logs.getMapList("entries");
        List<Map<?, ?>> result = new ArrayList<>();

        if (limit <= 0) {
            return result;
        }

        for (int i = entries.size() - 1;
             i >= 0 && result.size() < limit;
             i--) {
            result.add(entries.get(i));
        }

        return result;
    }

    public void save() {
        try {
            logs.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe(
                    "Не удалось сохранить журнал действий: "
                            + exception.getMessage()
            );
        }
    }
}
