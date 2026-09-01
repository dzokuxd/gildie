package pl.gildie.managers;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pl.gildie.GildiePlugin;
import pl.gildie.model.Guild;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PeriscopeManager {

    private static final int DURATION_SECONDS = 25;
    /** Wysokość nad terenem gildii (nie absolutne Y=130) — bliżej ziemi = widać particle */
    private static final int HEIGHT_ABOVE_CENTER = 45;
    private static final int BORDER_POINTS = 96;

    private final GildiePlugin plugin;
    private final GuildManager guildManager;

    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();
    private final Map<UUID, BukkitTask> borderTasks = new HashMap<>();

    public PeriscopeManager(GildiePlugin plugin, GuildManager guildManager) {
        this.plugin = plugin;
        this.guildManager = guildManager;
    }

    public boolean isInPeriscope(Player player) {
        return returnLocations.containsKey(player.getUniqueId());
    }

    private void sendActionBar(Player player, String msg) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
    }

    public void start(Player player) {
        if (isInPeriscope(player)) {
            player.sendMessage("§cJuż jesteś w peryskopie!");
            return;
        }

        Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (guild == null) {
            player.sendMessage("§cNie jesteś w gildii!");
            return;
        }

        Location center = guild.getCenter();
        if (center == null) {
            player.sendMessage("§cŚwiat gildii niedostępny.");
            return;
        }

        // Y = teren gildii + 45 (np. 70+45=115) — particle w zasięgu renderu
        Location view = center.clone();
        view.setY(center.getY() + HEIGHT_ABOVE_CENTER);
        view.setPitch(90f);

        returnLocations.put(player.getUniqueId(), player.getLocation().clone());
        player.teleport(view);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFlySpeed(0.12f);

        player.sendMessage("§aPeryskop aktywny! §7Kucnij, aby wyjść.");
        player.sendMessage("§eŻółty/biały pierścień = granica terenu (r=" + guild.getRadius() + ").");

        final double viewY = view.getY();

        BukkitTask countdown = new BukkitRunnable() {
            int left = DURATION_SECONDS;

            @Override
            public void run() {
                if (!player.isOnline() || !returnLocations.containsKey(player.getUniqueId())) {
                    cancel();
                    return;
                }
                sendActionBar(player, "§eAby wyjść z peryskopu kucnij §7— §c§l" + left + "s");
                if (left-- <= 0) {
                    stop(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        BukkitTask borderTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !returnLocations.containsKey(player.getUniqueId())) {
                    cancel();
                    return;
                }
                drawBorder(player, guild, viewY);
            }
        }.runTaskTimer(plugin, 0L, 2L);

        tasks.put(player.getUniqueId(), countdown);
        borderTasks.put(player.getUniqueId(), borderTask);
    }

    public void stop(Player player) {
        UUID id = player.getUniqueId();
        BukkitTask task = tasks.remove(id);
        if (task != null) task.cancel();
        BukkitTask border = borderTasks.remove(id);
        if (border != null) border.cancel();

        Location back = returnLocations.remove(id);
        if (back != null && player.isOnline()) {
            player.setFlying(false);
            player.setAllowFlight(false);
            player.setFlySpeed(0.1f);
            player.teleport(back);
            player.sendMessage("§aWyszedłeś z peryskopu.");
            sendActionBar(player, "");
        }
    }

    /**
     * Pierścień blisko kamery (viewY - 5 / -12 / -20) — w zasięgu particle render distance.
     * Dodatkowo cienki pierścień przy ziemi.
     */
    private void drawBorder(Player player, Guild guild, double viewY) {
        Location c = guild.getCenter();
        if (c == null) return;
        World world = c.getWorld();
        if (world == null) return;

        double r = guild.getRadius();
        double cx = c.getX() + 0.5;
        double cz = c.getZ() + 0.5;
        double groundY = c.getY() + 1.5;

        // warstwy BLISKO gracza (najważniejsze)
        double[] nearYs = {
                viewY - 4,
                viewY - 10,
                viewY - 18,
                groundY
        };

        for (int i = 0; i < BORDER_POINTS; i++) {
            double angle = 2 * Math.PI * i / BORDER_POINTS;
            double x = cx + r * Math.cos(angle);
            double z = cz + r * Math.sin(angle);

            for (double y : nearYs) {
                Location loc = new Location(world, x, y, z);
                player.spawnParticle(Particle.END_ROD, loc, 2, 0.05, 0.15, 0.05, 0);
                // co 2. punkt — FLAME (bardziej żółty, widać na zieleni)
                if (i % 2 == 0) {
                    player.spawnParticle(Particle.FLAME, loc, 1, 0.02, 0.08, 0.02, 0);
                }
            }
        }

        // środek — pionowa kolumna do kamery
        for (double y = groundY; y < viewY - 2; y += 3) {
            player.spawnParticle(Particle.END_ROD, new Location(world, cx, y, cz), 1, 0, 0, 0, 0);
        }
    }
}
