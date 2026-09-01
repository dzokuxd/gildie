package pl.gildie.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import pl.gildie.managers.GuildManager;
import pl.gildie.model.Guild;

public class ProtectionListener implements Listener {
    private final GuildManager guildManager;

    public ProtectionListener(GuildManager guildManager) {
        this.guildManager = guildManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Guild guild = guildManager.getGuildAt(event.getBlock().getLocation());
        if (guild == null) {
            return;
        }
        if (!guild.isMember(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cNie mozesz budowac na terenie gildii §e" + guild.getTag() + "§c!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Guild guild = guildManager.getGuildAt(event.getBlock().getLocation());
        if (guild == null) {
            return;
        }
        if (!guild.isMember(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cNie mozesz niszczyc na terenie gildii §e" + guild.getTag() + "§c!");
        }
    }
}
