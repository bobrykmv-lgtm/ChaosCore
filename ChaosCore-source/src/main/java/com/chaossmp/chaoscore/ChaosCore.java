package com.chaossmp.chaoscore;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class ChaosCore extends JavaPlugin implements Listener {

private static final MiniMessage MINI = MiniMessage.miniMessage();

private ChaosDataManager dataManager;

// Время последнего вознаграждения за убийство конкретной цели
private final Map<String, Long> killCooldowns = new ConcurrentHashMap<>();

@Override
public void onEnable() {
    saveDefaultConfig();

    dataManager = new ChaosDataManager(this);

    Bukkit.getPluginManager().registerEvents(this, this);

    long interval = Math.max(
            20L,
            getConfig().getLong("settings.save-interval", 60L) * 20L
    );

    if (getConfig().getBoolean("settings.auto-save", true)) {
        Bukkit.getScheduler().runTaskTimer(
                this,
                () -> dataManager.save(),
                interval,
                interval
        );
    }

    getLogger().info("ChaosCore успешно запущен!");
}

@Override
public void onDisable() {
    if (dataManager != null) {
        dataManager.save();
    }

    Bukkit.getScheduler().cancelTasks(this);
    getLogger().info("ChaosCore выключен. Данные сохранены.");
}

private void send(CommandSender sender, String path) {
    String prefix = getConfig().getString("messages.prefix", "");
    String message = getConfig().getString(path, "<red>Сообщение не найдено.");

    sender.sendMessage(MINI.deserialize(prefix + message));
}

private void sendRaw(CommandSender sender, String message) {
    String prefix = getConfig().getString("messages.prefix", "");
    sender.sendMessage(MINI.deserialize(prefix + message));
}

private void sendFormatted(CommandSender sender, String path,
                           Map<String, String> placeholders) {
    String prefix = getConfig().getString("messages.prefix", "");
    String message = getConfig().getString(path, "<red>Сообщение не найдено.");

    for (Map.Entry<String, String> entry : placeholders.entrySet()) {
        message = message.replace(
                "{" + entry.getKey() + "}",
                entry.getValue()
        );
    }

    sender.sendMessage(MINI.deserialize(prefix + message));
}

private boolean hasAdminPermission(CommandSender sender) {
    if (!sender.hasPermission("chaoscore.admin")) {
        send(sender, "messages.no-permission");
        return false;
    }
    return true;
}

private Player findOnlinePlayer(String name) {
    return Bukkit.getPlayerExact(name);
}

private int getMaxChaos() {
    return Math.max(0, getConfig().getInt("settings.max-chaos", 25));
}

private boolean validAmount(String value) {
    try {
        return Integer.parseInt(value) >= 0;
    } catch (NumberFormatException exception) {
        return false;
    }
}

private void showStats(CommandSender sender, String name) {
    Player online = findOnlinePlayer(name);

    OfflinePlayer offline = online != null
            ? online
            : Bukkit.getOfflinePlayer(name);

    UUID uuid = offline.getUniqueId();

    if (uuid == null || (!offline.hasPlayedBefore() && online == null)) {
        send(sender, "messages.player-not-found");
        return;
    }

    sendFormatted(sender, "messages.check", Map.of(
            "player", offline.getName() == null ? name : offline.getName(),
            "chaos", String.valueOf(dataManager.getChaos(uuid)),
            "max", String.valueOf(getMaxChaos()),
            "power", String.valueOf(dataManager.getPowerLevel(uuid))
    ));

    sendRaw(sender,
            "<gray>Убийства: <green>" + dataManager.getKills(uuid)
                    + " <gray>| Смерти: <red>"
                    + dataManager.getDeaths(uuid)
    );
}

private void showTop(CommandSender sender) {
    var entries = dataManager.getAllChaos().entrySet()
            .stream()
            .sorted(Map.Entry.<UUID, Integer>comparingByValue(
                    Comparator.reverseOrder()
            ))
            .limit(10)
            .toList();

    sendRaw(sender, "<gradient:#5B8CFF:#C084FC><bold>ТОП-10 CHAOS</bold>");

    if (entries.isEmpty()) {
        sendRaw(sender, "<gray>Пока нет данных.");
        return;
    }

    int position = 1;

    for (Map.Entry<UUID, Integer> entry : entries) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());

        String name = player.getName() == null
                ? entry.getKey().toString().substring(0, 8)
                : player.getName();

        sendRaw(sender,
                "<gray>" + position + ". <white>" + name
                        + " <dark_gray>— <aqua>" + entry.getValue()
                        + " <gray>хаосов"
        );

        position++;
    }
}

@Override
public boolean onCommand(
        CommandSender sender,
        Command command,
        String label,
        String[] args
) {
    if (args.length == 0) {
        if (!(sender instanceof Player player)) {
            send(sender, "messages.player-only");
            return true;
        }

        showStats(player, player.getName());
        return true;
    }

    String subcommand = args[0].toLowerCase();

    switch (subcommand) {
        case "top" -> showTop(sender);

        case "stats", "check" -> {
            if (args.length < 2) {
                send(sender, "messages.invalid-player");
                return true;
            }
            showStats(sender, args[1]);
        }

        case "give", "take", "set" -> {
            if (!hasAdminPermission(sender)) return true;

            if (args.length < 3 || !validAmount(args[2])) {
                send(sender, "messages.invalid-amount");
                return true;
            }

            Player target = findOnlinePlayer(args[1]);

            if (target == null) {
                send(sender, "messages.player-not-found");
                return true;
            }

            int amount = Integer.parseInt(args[2]);
            UUID uuid = target.getUniqueId();

            switch (subcommand) {
                case "give" -> {
                    int before = dataManager.getChaos(uuid);
                    dataManager.addChaos(uuid, amount);
                    int added = dataManager.getChaos(uuid) - before;

                    sendFormatted(sender, "messages.given", Map.of(
                            "player", target.getName(),
                            "amount", String.valueOf(added)
                    ));
                }

                case "take" -> {
                    int before = dataManager.getChaos(uuid);
                    dataManager.removeChaos(uuid, amount);
                    int removed = before - dataManager.getChaos(uuid);

                    sendFormatted(sender, "messages.taken", Map.of(
                            "player", target.getName(),
                            "amount", String.valueOf(removed)
                    ));
                }

                case "set" -> {
                    dataManager.setChaos(uuid, amount);

                    sendFormatted(sender, "messages.set", Map.of(
                            "player", target.getName(),
                            "amount", String.valueOf(dataManager.getChaos(uuid))
                    ));
                }
            }

            dataManager.save();
        }

        case "reload" -> {
            if (!hasAdminPermission(sender)) return true;

            reloadConfig();
            send(sender, "messages.reloaded");
        }

        default -> sendRaw(sender,
                "<red>Использование: /chaos [top|stats|check|give|take|set|reload]"
        );
    }

    return true;
}

@EventHandler
public void onPlayerDeath(PlayerDeathEvent event) {
    Player victim = event.getEntity();
    Player killer = victim.getKiller();

    dataManager.addDeath(victim.getUniqueId());

    if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
        return;
    }

    dataManager.addKill(killer.getUniqueId());

    long now = System.currentTimeMillis();
    String key = killer.getUniqueId() + ":" + victim.getUniqueId();

    long cooldown = getConfig().getLong("settings.kill-cooldown", 300);
    long lastKill = killCooldowns.getOrDefault(key, 0L);

    if (now - lastKill < TimeUnit.SECONDS.toMillis(cooldown)) {
        return;
    }

    killCooldowns.put(key, now);

    int reward = Math.max(0,
            getConfig().getInt("settings.chaos-per-kill", 1));

    int before = dataManager.getChaos(killer.getUniqueId());
    dataManager.addChaos(killer.getUniqueId(), reward);
    int received = dataManager.getChaos(killer.getUniqueId()) - before;

    if (received > 0) {
        sendFormatted(killer, "messages.kill-reward",
                Map.of("amount", String.valueOf(received)));
    }
}
}
