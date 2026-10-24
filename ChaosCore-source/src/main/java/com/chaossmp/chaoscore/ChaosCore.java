package com.chaossmp.chaoscore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ChaosCore extends JavaPlugin implements Listener {
    private File dataFile;
    private YamlConfiguration data;
    private final Map<String, Long> lastKillTime = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create plugin data folder.");
        }
        dataFile = new File(getDataFolder(), "players.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("ChaosCore enabled.");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        String pair = killer.getUniqueId() + ":" + victim.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldown = getConfig().getLong("chaos.repeat-kill-cooldown-seconds", 300) * 1000L;
        Long last = lastKillTime.get(pair);
        if (last != null && now - last < cooldown) {
            killer.sendMessage(color("&8[&bChaos&8] &7Повторне вбивство не дає хаосу ще " +
                Math.max(1, (cooldown - (now - last)) / 1000) + " с."));
            return;
        }
        lastKillTime.put(pair, now);

        int reward = Math.max(0, getConfig().getInt("chaos.kill-reward", 1));
        int lifetime = data.getInt("players." + killer.getUniqueId() + ".lifetime", 0) + reward;
        int balance = data.getInt("players." + killer.getUniqueId() + ".balance", 0) + reward;
        data.set("players." + killer.getUniqueId() + ".lifetime", lifetime);
        data.set("players." + killer.getUniqueId() + ".balance", balance);
        saveData();

        killer.sendMessage(color("&8[&bChaos&8] &f+" + reward + " хаос &7| Баланс: &b" + balance));
        applyPower(killer, lifetime);
    }

    private void applyPower(Player player, int lifetimeChaos) {
        int level = 0;
        if (lifetimeChaos >= getConfig().getInt("power.levels.2", 10)) level = 2;
        else if (lifetimeChaos >= getConfig().getInt("power.levels.1", 5)) level = 1;

        String path = "players." + player.getUniqueId() + ".power";
        int oldLevel = data.getInt(path, 0);
        if (level > oldLevel) {
            data.set(path, level);
            saveData();
            player.sendMessage(color("&8[&bChaos&8] &fТвій рівень сили підвищено до &b" + level + "&f!"));
        }
        // Reapply the permanent effect while online; amplifier 0 = Strength I.
        if (level > 0) {
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH);
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.STRENGTH,
                20 * 60 * 60 * 24 * 365,
                level - 1,
                false, false, true
            ));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("chaos")) return false;
        if (args.length > 0 && args[0].equalsIgnoreCase("top")) {
            List<Map.Entry<String, Integer>> ranking = new ArrayList<>();
            for (String key : data.getConfigurationSection("players") == null
                    ? Collections.<String>emptySet()
                    : data.getConfigurationSection("players").getKeys(false)) {
                ranking.add(Map.entry(key, data.getInt("players." + key + ".lifetime", 0)));
            }
            ranking.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            sender.sendMessage(color("&8&m----------&r &bChaosSMP TOP &8&m----------"));
            int limit = Math.min(10, ranking.size());
            for (int i = 0; i < limit; i++) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(ranking.get(i).getKey()));
                sender.sendMessage(color("&b" + (i + 1) + ". &f" +
                    (op.getName() == null ? "Unknown" : op.getName()) + " &7- &b" +
                    ranking.get(i).getValue() + " хаосу"));
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Chaos: use /chaos top from console.");
            return true;
        }
        String base = "players." + player.getUniqueId();
        int balance = data.getInt(base + ".balance", 0);
        int lifetime = data.getInt(base + ".lifetime", 0);
        int power = data.getInt(base + ".power", 0);
        player.sendMessage(color("&8&m----------&r &bChaosSMP &8&m----------"));
        player.sendMessage(color("&fВільний хаос: &b" + balance));
        player.sendMessage(color("&fЗагальний хаос: &b" + lifetime));
        player.sendMessage(color("&fПостійна сила: &b" + power));
        return true;
    }

    private void saveData() {
        if (data == null || dataFile == null) return;
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save players.yml: " + e.getMessage());
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
