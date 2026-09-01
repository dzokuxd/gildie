package pl.gildie.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RegenManager {
    public static final int Y_SPLIT = 60;
    private static final int BLOCKS_PER_TICK = 5;
    private static final long TICK_PERIOD = 2L;

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, SavedBlock> pending = new HashMap<>();
    private final Map<UUID, BossBar> regenBars = new HashMap<>();

    public RegenManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "regen.yml");
        load();
    }

    public void load() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException ignored) {
            }
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        pending.clear();
        ConfigurationSection section = config.getConfigurationSection("blocks");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String path = "blocks." + key;
            SavedBlock block = new SavedBlock(
                    config.getString(path + ".world"),
                    config.getInt(path + ".x"),
                    config.getInt(path + ".y"),
                    config.getInt(path + ".z"),
                    config.getString(path + ".data")
            );
            pending.put(key, block);
        }
    }

    public void save() {
        FileConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, SavedBlock> entry : pending.entrySet()) {
            SavedBlock block = entry.getValue();
            String path = "blocks." + entry.getKey();
            config.set(path + ".world", block.world);
            config.set(path + ".x", block.x);
            config.set(path + ".y", block.y);
            config.set(path + ".z", block.z);
            config.set(path + ".data", block.data);
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie mozna zapisac regen.yml: " + e.getMessage());
        }
    }

    public void recordBlock(String world, int x, int y, int z, String data) {
        // Nie zapisujemy wody i lawy do regeneracji
        if (isFluid(data)) {
            return;
        }
        String key = key(world, x, y, z);
        pending.put(key, new SavedBlock(world, x, y, z, data));
    }

    public void scheduleAutoRegenAbove(List<String> keys) {
        if (keys.isEmpty()) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                List<SavedBlock> blocks = new ArrayList<>();
                for (String key : keys) {
                    SavedBlock block = pending.get(key);
                    if (block != null && block.y > Y_SPLIT) {
                        blocks.add(block);
                    }
                }

                // od dołu do góry
                blocks.sort((a, b) -> Integer.compare(a.y, b.y));

                for (SavedBlock block : blocks) {
                    String k = key(block.world, block.x, block.y, block.z);
                    if (restore(block)) {
                        pending.remove(k);
                    }
                }
                save();
            }
        }.runTaskLater(plugin, 20 * 20L);
    }

    public boolean isRegenerating(UUID uuid) {
        return regenBars.containsKey(uuid);
    }

    public void startManualRegen(Player player) {
        if (isRegenerating(player.getUniqueId())) {
            player.sendMessage("§cRegeneracja juz trwa!");
            return;
        }

        List<Map.Entry<String, SavedBlock>> toRegen = new ArrayList<>();
        for (Map.Entry<String, SavedBlock> entry : pending.entrySet()) {
            if (entry.getValue().y <= Y_SPLIT) {
                toRegen.add(entry);
            }
        }

        if (toRegen.isEmpty()) {
            player.sendMessage("§cBrak blokow do regeneracji ponizej Y=" + Y_SPLIT + ".");
            return;
        }

        // regeneracja od dołu
        toRegen.sort((a, b) -> Integer.compare(a.getValue().y, b.getValue().y));

        int total = toRegen.size();
        BossBar bar = Bukkit.createBossBar("§aRegeneracja gildii...", BarColor.GREEN, BarStyle.SOLID);
        bar.addPlayer(player);
        bar.setProgress(0);
        regenBars.put(player.getUniqueId(), bar);

        player.sendMessage("§aRozpoczeto regeneracje §e" + total + " §ablokow ponizej Y=" + Y_SPLIT + ".");

        new BukkitRunnable() {
            int index = 0;
            int done = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    bar.removeAll();
                    regenBars.remove(player.getUniqueId());
                    save();
                    cancel();
                    return;
                }

                if (index >= total) {
                    bar.removeAll();
                    regenBars.remove(player.getUniqueId());
                    player.sendMessage("§aRegeneracja zakonczona! Zregenerowano §e" + done + " §ablokow.");
                    save();
                    cancel();
                    return;
                }

                for (int i = 0; i < BLOCKS_PER_TICK && index < total; i++, index++) {
                    Map.Entry<String, SavedBlock> entry = toRegen.get(index);
                    if (restore(entry.getValue())) {
                        pending.remove(entry.getKey());
                        done++;
                    }
                }

                int remainingBlocks = total - done;
                long remainingTicks = (long) Math.ceil(remainingBlocks / (double) BLOCKS_PER_TICK) * TICK_PERIOD;
                double remainingSeconds = remainingTicks / 20.0;
                double progress = total == 0 ? 1.0 : (double) done / total;
                bar.setProgress(Math.min(1.0, progress));
                bar.setTitle("§aZregenerowano: §e" + done + "/" + total
                        + " §7(" + (int) (progress * 100) + "%)"
                        + " §8| §fCzas: §e" + formatTime(remainingSeconds));
            }
        }.runTaskTimer(plugin, 0L, TICK_PERIOD);
    }

    public void clearBar(Player player) {
        BossBar bar = regenBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }

    private boolean restore(SavedBlock block) {
        World world = Bukkit.getWorld(block.world);
        if (world == null || block.data == null) {
            return false;
        }

        // nie stawiamy z powrotem samej wody/lawy
        if (isFluid(block.data)) {
            return true;
        }

        try {
            Block b = world.getBlockAt(block.x, block.y, block.z);

            // jeśli w miejscu jest woda lub lawa – najpierw usuwamy
            Material current = b.getType();
            if (current == Material.WATER || current == Material.LAVA) {
                b.setType(Material.AIR, false);
            }

            // stawiamy oryginalny blok (skrzynka, kamień itd.)
            b.setBlockData(Bukkit.createBlockData(block.data), false);
            return true;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Nie mozna przywrocic bloku " + block.data + ": " + e.getMessage());
            return false;
        }
    }

    private boolean isFluid(String data) {
        if (data == null) return false;
        try {
            Material mat = Bukkit.createBlockData(data).getMaterial();
            return mat == Material.WATER || mat == Material.LAVA;
        } catch (IllegalArgumentException e) {
            String d = data.toLowerCase();
            return d.startsWith("minecraft:water")
                    || d.startsWith("minecraft:lava")
                    || d.startsWith("water")
                    || d.startsWith("lava");
        }
    }

    public static String key(String world, int x, int y, int z) {
        return world + ";" + x + ";" + y + ";" + z;
    }

    private String formatTime(double seconds) {
        if (seconds < 1) {
            return "0s";
        }
        int total = (int) Math.ceil(seconds);
        int min = total / 60;
        int sec = total % 60;
        if (min > 0) {
            return min + "m " + sec + "s";
        }
        return sec + "s";
    }

    public static class SavedBlock {
        public final String world;
        public final int x;
        public final int y;
        public final int z;
        public final String data;

        public SavedBlock(String world, int x, int y, int z, String data) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.data = data;
        }
    }
}