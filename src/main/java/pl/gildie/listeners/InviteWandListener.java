package pl.gildie.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.gildie.GildiePlugin;
import pl.gildie.commands.GCommand;

public class InviteWandListener implements Listener {

    private final GildiePlugin plugin;
    private final GCommand gCommand;

    public InviteWandListener(GildiePlugin plugin, GCommand gCommand) {
        this.plugin = plugin;
        this.gCommand = gCommand;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }
        if (!GCommand.WAND_NAME.equals(meta.getDisplayName())) {
            return;
        }

        event.setCancelled(true);

        Long expire = plugin.getInviteWandUsers().get(player.getUniqueId());
        if (expire == null || System.currentTimeMillis() > expire) {
            player.sendMessage("§cRóżdżka wygasła.");
            player.getInventory().setItemInMainHand(null);
            plugin.getInviteWandUsers().remove(player.getUniqueId());
            return;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§cNie możesz zaprosić siebie.");
            return;
        }

        gCommand.inviteFromWand(player, target);
    }
}
