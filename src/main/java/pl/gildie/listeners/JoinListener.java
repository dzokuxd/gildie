package pl.gildie.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import pl.gildie.GildiePlugin;
import pl.gildie.managers.GuildManager;
import pl.gildie.model.Guild;
import pl.gildie.util.WaypointHook;

public class JoinListener implements Listener {

    private final GildiePlugin plugin;
    private final GuildManager guildManager;

    public JoinListener(GildiePlugin plugin, GuildManager guildManager) {
        this.plugin = plugin;
        this.guildManager = guildManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
            if (guild == null) return;

            if (guild.getGuildWaypointId() != null) {
                Location center = guild.getCenterAtY(70);
                if (center != null) {
                    WaypointHook.addGuildWaypoint(player, "Gildia " + guild.getTag(), center, 0x55FF55);
                }
            }

            if (guild.hasActiveRaidBase()) {
                Location raid = guild.getRaidBase();
                if (raid != null) {
                    WaypointHook.addGuildWaypoint(player, "Baza wypadowa", raid, 0xFF5555);
                }
            }
        }, 40L);
    }
}
