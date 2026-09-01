package pl.gildie.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pl.gildie.model.Guild;

import java.util.*;

public class DigManager {

    private final JavaPlugin plugin;
    private final GuildManager guildManager;

    private final Map<UUID, BukkitTask> active = new HashMap<>();
    private int activeCount = 0;
    private final int maxActive = 2;

    private final Map<UUID, DigInfo> playerDigs = new HashMap<>();

    private static class DigInfo {
        final Location center;
        final int radius;
        final int maxY;

        DigInfo(Location center, int radius, int maxY) {
            this.center = center.clone();
            this.radius = radius;
            this.maxY = maxY;
        }
    }

    public DigManager(JavaPlugin plugin, GuildManager guildManager) {
        this.plugin = plugin;
        this.guildManager = guildManager;
    }

    // ==================== KOPANIE FOSY ====================
    public void start(Player p, int radius, int startY) {
        if (active.containsKey(p.getUniqueId())) {
            p.sendMessage("§cJuż kopiesz!");
            return;
        }
        if (activeCount >= maxActive) {
            p.sendMessage("§cLimit aktywnych kopań osiągnięty!");
            return;
        }
        Guild guild = guildManager.getGuildByPlayer(p.getUniqueId());

        if (guild == null) {
            p.sendMessage("§cNie jesteś w żadnej gildii!");
            return;
        }

        Location center = guild.getCenter();

        if (center == null) {
            p.sendMessage("§cNie można pobrać środka gildii (świat nie istnieje)!");
            return;
        }

        activeCount++;

        int outer = radius + 9;

        Queue<Location> queue = new LinkedList<>();

        new BukkitRunnable() {
            @Override
            public void run() {
                for (int y = startY; y >= 5; y--) {
                    for (int x = center.getBlockX() - outer; x <= center.getBlockX() + outer; x++) {
                        for (int z = center.getBlockZ() - outer; z <= center.getBlockZ() + outer; z++) {
                            int dist = Math.max(Math.abs(x - center.getBlockX()), Math.abs(z - center.getBlockZ()));
                            if (dist > radius && dist <= outer) {
                                queue.add(new Location(center.getWorld(), x, y, z));
                            }
                        }
                    }
                }
                startBreak(p, queue, center, radius, startY);
            }
        }.runTaskAsynchronously(plugin);
    }

    private void startBreak(Player p, Queue<Location> queue, Location center, int radius, int startY) {
        long startTime = System.currentTimeMillis();
        int totalBlocks = queue.size();

        BossBar bar = Bukkit.createBossBar("§eKopanie fosy...", BarColor.GREEN, BarStyle.SOLID);
        bar.addPlayer(p);
        bar.setProgress(0);

        BukkitTask task = new BukkitRunnable() {
            int processed = 0;

            @Override
            public void run() {
                for (int i = 0; i < 300; i++) {//ile blokow kopie
                    Location loc = queue.poll();
                    if (loc == null) break;
                    Block b = loc.getBlock();
                    if (b.getType() != Material.AIR && b.getType() != Material.BEDROCK) {
                        b.setType(Material.AIR);
                    }
                    processed++;
                }

                if (processed > 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    double speed = processed / (elapsed / 1000.0);
                    int remaining = totalBlocks - processed;
                    long eta = speed > 0 ? (long) (remaining / speed) : 0;

                    double progress = (double) processed / totalBlocks;
                    int percent = (int) (progress * 100);

                    if (percent < 33) bar.setColor(BarColor.RED);
                    else if (percent < 66) bar.setColor(BarColor.YELLOW);
                    else bar.setColor(BarColor.GREEN);

                    bar.setProgress(Math.min(1.0, progress));
                    bar.setTitle("§ePostęp: §f" + percent + "% §7| §ePozostało: §f" + formatTime(eta));
                }

                if (queue.isEmpty()) {
                    bar.setProgress(1.0);
                    bar.setColor(BarColor.GREEN);
                    bar.setTitle("§a✔ Fosa wykopana!");
                    Bukkit.getScheduler().runTaskLater(plugin, bar::removeAll, 40L);

                    activeCount--;
                    active.remove(p.getUniqueId());

                    playerDigs.put(p.getUniqueId(), new DigInfo(center, radius, startY));
                    p.sendMessage("§a§lFosa wykopana! Możesz teraz zbudować ściany.");
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);//co ile tick

        active.put(p.getUniqueId(), task);
    }

    // ==================== BUDOWANIE ŚCIAN ====================
    public void buildWalls(Player p, Material wallMaterial, int requestedRadius, int height) {
        DigInfo info = playerDigs.get(p.getUniqueId());
        if (info == null) {
            p.sendMessage("§cNajpierw wykop fosę!");
            return;
        }

        int minRadius = info.radius + 1;
        int maxRadius = info.radius + 9;   // dokładnie tak jak chciałeś

        int wallRadius = Math.max(minRadius, Math.min(maxRadius, requestedRadius));

        p.sendMessage("§eBuduję ściany z §f" + wallMaterial.name() + " §7(Promień: " + wallRadius + ")");

        Queue<Location> queue = new LinkedList<>();
        World world = info.center.getWorld();
        int cx = info.center.getBlockX();
        int cz = info.center.getBlockZ();

        for (int y = 5; y <= height; y++) {
            // Północ i Południe
            for (int x = cx - wallRadius; x <= cx + wallRadius; x++) {
                Location locN = new Location(world, x, y, cz - wallRadius);
                Location locS = new Location(world, x, y, cz + wallRadius);
                if (locN.getBlock().getType() == Material.AIR) queue.add(locN);
                if (locS.getBlock().getType() == Material.AIR) queue.add(locS);
            }
            // Zachód i Wschód
            for (int z = cz - wallRadius; z <= cz + wallRadius; z++) {
                Location locW = new Location(world, cx - wallRadius, y, z);
                Location locE = new Location(world, cx + wallRadius, y, z);
                if (locW.getBlock().getType() == Material.AIR) queue.add(locW);
                if (locE.getBlock().getType() == Material.AIR) queue.add(locE);
            }
        }

        if (queue.isEmpty()) {
            p.sendMessage("§cNie znaleziono miejsca na ściany.");
            return;
        }

        startWallBuilding(p, queue, wallMaterial);
    }

    private void startWallBuilding(Player p, Queue<Location> queue, Material wallMaterial) {
        long startTime = System.currentTimeMillis();
        int total = queue.size();

        BossBar bar = Bukkit.createBossBar("§eBudowanie ścian...", BarColor.GREEN, BarStyle.SOLID);
        bar.addPlayer(p);
        bar.setProgress(0);

        new BukkitRunnable() {
            int processed = 0;

            @Override
            public void run() {
                for (int i = 0; i < 200; i++) {
                    Location loc = queue.poll();
                    if (loc == null) break;
                    loc.getBlock().setType(wallMaterial);
                    processed++;
                }

                if (processed > 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    double progress = (double) processed / total;
                    int percent = (int) (progress * 100);
                    long eta = (long) ((total - processed) / (processed / (elapsed / 1000.0 + 0.001)));

                    if (percent < 33) bar.setColor(BarColor.RED);
                    else if (percent < 66) bar.setColor(BarColor.YELLOW);
                    else bar.setColor(BarColor.GREEN);

                    bar.setProgress(Math.min(1.0, progress));
                    bar.setTitle("§eŚciany: §f" + percent + "% §7| §ePozostało: §f" + formatTime(eta));
                }

                if (queue.isEmpty()) {
                    bar.setProgress(1.0);
                    bar.setColor(BarColor.GREEN);
                    bar.setTitle("§a✔ Ściany zbudowane!");
                    Bukkit.getScheduler().runTaskLater(plugin, bar::removeAll, 60L);
                    cancel();
                    p.sendMessage("§a§lŚciany zostały zbudowane pomyślnie!");
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    public int getPlayerDigRadius(Player p) {
        DigInfo info = playerDigs.get(p.getUniqueId());
        return info != null ? info.radius : 0;
    }

    private String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    public void stop(Player p) {
        BukkitTask t = active.remove(p.getUniqueId());
        if (t != null) {
            t.cancel();
            activeCount--;
        }
    }
}