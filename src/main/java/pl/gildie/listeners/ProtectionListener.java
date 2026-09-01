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
        // 1x1 baza wypadowa — nikt nie buduje
        Guild raid = guildManager.getRaidBaseOwnerAt(event.getBlock().getLocation());
        if (raid != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cNie możesz budować na bazie wypadowej gildii §e" + raid.getTag() + "§c!");
            return;
        }

        Guild guild = guildManager.getGuildAt(event.getBlock().getLocation());
        if (guild == null) {
            return;
        }
        if (!guild.isMember(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cNie możesz budować na terenie gildii §e" + guild.getTag() + "§c!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Guild raid = guildManager.getRaidBaseOwnerAt(event.getBlock().getLocation());
        if (raid != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cNie możesz niszczyć na bazie wypadowej gildii §e" + raid.getTag() + "§c!");
            return;
        }

        Guild guild = guildManager.getGuildAt(event.getBlock().getLocation());
        if (guild == null) {
            return;
        }
        if (!guild.isMember(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cNie możesz niszczyć na terenie gildii §e" + guild.getTag() + "§c!");
        }
    }
}
