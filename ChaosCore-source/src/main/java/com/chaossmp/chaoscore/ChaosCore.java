package com.chaossmp.chaoscore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ChaosCore extends JavaPlugin implements Listener {

    private File dataFile;
    private YamlConfiguration data;
    private final Map<String, Long> lastKillTime = new ConcurrentHashMap<>();

    private static final int MAX_CHAOS = 25;
    private static final long KILL_COOLDOWN_MS = 300_000L;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Не удалось создать папку данных.");
        }

        dataFile = new File(getDataFolder(), "players.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);

        getServer().getPluginManager().registerEvents(this, this);

        // Восстанавливаем постоянную силу после входа игрока.
        Bukkit.getOnlinePlayers().forEach(this::restorePower);

        getLogger().info("ChaosCore включён. Максимум хаоса: 25.");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null || killer.equals(victim)) {
            return;
        }

        String pair = killer.getUniqueId() + ":" + victim.getUniqueId();
        long now = System.currentTimeMillis();

        Long last = lastKillTime.get(pair);

        if (last != null && now - last < KILL_COOLDOWN_MS) {
            long seconds = Math.max(1, (KILL_COOLDOWN_MS - (now - last)) / 1000);

            killer.sendMessage(color(
                "&8[&bChaos&8] &cПовторное убийство не приносит хаос. Осталось: "
                    + seconds + " сек."
            ));

            return;
        }

        lastKillTime.put(pair, now);

        String killerPath = "players." + killer.getUniqueId();
        String victimPath = "players." + victim.getUniqueId();

        int killerBalance = data.getInt(killerPath + ".balance", 0);
        int victimBalance = data.getInt(victimPath + ".balance", 0);

        int killerLifetime = data.getInt(killerPath + ".lifetime", 0);
        int victimLifetime = data.getInt(victimPath + ".lifetime", 0);

        // Убийца получает +1, но не больше 25.
        int newKillerBalance = Math.min(MAX_CHAOS, killerBalance + 1);
        int newKillerLifetime = Math.min(MAX_CHAOS, killerLifetime + 1);

        // Жертва теряет 1 хаос, но не опускается ниже 0.
        int newVictimBalance = Math.max(0, victimBalance - 1);
        int newVictimLifetime = Math.max(0, victimLifetime - 1);

        data.set(killerPath + ".balance", newKillerBalance);
        data.set(killerPath + ".lifetime", newKillerLifetime);

        data.set(victimPath + ".balance", newVictimBalance);
        data.set(victimPath + ".lifetime", newVictimLifetime);

        saveData();

        killer.sendMessage(color(
            "&8[&bChaos&8] &aВы получили +1 хаос! Баланс: &b"
                + newKillerBalance + "&7/&b25"
        ));

        victim.sendMessage(color(
            "&8[&bChaos&8] &cВы потеряли 1 хаос. Баланс: &b"
                + newVictimBalance + "&7/&b25"
        ));

        updatePower(killer, newKillerLifetime);
        updatePower(victim, newVictimLifetime);
    }

    private int getPowerLevel(int chaos) {
        if (chaos >= 25) return 5;
        if (chaos >= 20) return 4;
        if (chaos >= 15) return 3;
        if (chaos >= 10) return 2;
        if (chaos >= 5) return 1;
        return 0;
    }

    private void updatePower(Player player, int chaos) {
        int level = getPowerLevel(chaos);
        String path = "players." + player.getUniqueId() + ".power";

        data.set(path, level);
        saveData();

        player.removePotionEffect(PotionEffectType.STRENGTH);

        if (level > 0) {
            player.addPotionEffect(new PotionEffect(
                PotionEffectType.STRENGTH,
                PotionEffect.INFINITE_DURATION,
                level - 1,
                false,
                false,
                true
            ));
        }

        if (level > 0) {
            player.sendMessage(color(
                "&8[&bChaos&8] &fВаш уровень силы: &b" + level
                    + " &7(Strength " + level + ")"
            ));
        }
    }

    private void restorePower(Player player) {
        String path = "players." + player.getUniqueId();
        int chaos = data.getInt(path + ".lifetime", 0);
        updatePower(player, chaos);
    }

    @Override
    public boolean onCommand(
        CommandSender sender,
        Command command,
        String label,
        String[] args
    ) {
        if (!command.getName().equalsIgnoreCase("chaos")) {
            return false;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("top")) {
            List<Map.Entry<String, Integer>> ranking = new ArrayList<>();

            if (data.getConfigurationSection("players") != null) {
                for (String key :
                    data.getConfigurationSection("players").getKeys(false)) {

                    ranking.add(Map.entry(
                        key,
                        data.getInt("players." + key + ".lifetime", 0)
                    ));
                }
            }

            ranking.sort((a, b) ->
                Integer.compare(b.getValue(), a.getValue())
            );

            sender.sendMessage(color(
                "&8&m----------&r &bТоп ChaosSMP &8&m----------"
            ));

            int limit = Math.min(10, ranking.size());

            for (int i = 0; i < limit; i++) {
                OfflinePlayer offline =
                    Bukkit.getOfflinePlayer(UUID.fromString(ranking.get(i).getKey()));

                String name = offline.getName() == null
                    ? "Неизвестно"
                    : offline.getName();

                sender.sendMessage(color(
                    "&b" + (i + 1) + ". &f" + name + " &7- &b"
                        + ranking.get(i).getValue() + "/25 хаоса"
                ));
            }

            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Используйте: /chaos top");
            return true;
        }

        String path = "players." + player.getUniqueId();

        int balance = data.getInt(path + ".balance", 0);
        int lifetime = data.getInt(path + ".lifetime", 0);
        int power = data.getInt(path + ".power", 0);

        player.sendMessage(color(
            "&8&m----------&r &bChaosSMP &8&m----------"
        ));
        player.sendMessage(color(
            "&fВаш хаос: &b" + balance + "&7/&b25"
        ));
        player.sendMessage(color(
            "&fВсего хаоса: &b" + lifetime + "&7/&b25"
        ));
        player.sendMessage(color(
            "&fУровень силы: &b" + power + "&7/&b5"
        ));

        return true;
    }

    private void saveData() {
        if (data == null || dataFile == null) {
            return;
        }

        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().severe(
                "Не удалось сохранить players.yml: " + e.getMessage()
            );
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
