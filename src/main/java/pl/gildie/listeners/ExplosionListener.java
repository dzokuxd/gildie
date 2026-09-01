package pl.gildie.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import pl.gildie.managers.GuildManager;
import pl.gildie.managers.RegenManager;
import pl.gildie.model.Guild;

import java.util.ArrayList;
import java.util.List;

public class ExplosionListener implements Listener {
    private final GuildManager guildManager;
    private final RegenManager regenManager;

    public ExplosionListener(GuildManager guildManager, RegenManager regenManager) {
        this.guildManager = guildManager;
        this.regenManager = regenManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!isTnt(event.getEntityType())) {
            return;
        }

        List<String> autoKeys = new ArrayList<>();
        boolean any = false;

        for (Block block : event.blockList()) {
            Material type = block.getType();
            if (type == Material.AIR || type == Material.TNT || type == Material.FIRE || type.isAir()) {
                continue;
            }
            Guild guild = guildManager.getGuildAt(block.getLocation());
            if (guild == null) {
                continue;
            }
            String data = block.getBlockData().getAsString();
            String world = block.getWorld().getName();
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            regenManager.recordBlock(world, x, y, z, data);
            any = true;
            if (y > RegenManager.Y_SPLIT) {
                autoKeys.add(RegenManager.key(world, x, y, z));
            }
        }

        if (!any) {
            return;
        }
        regenManager.save();
        regenManager.scheduleAutoRegenAbove(autoKeys);
    }

    private boolean isTnt(EntityType type) {
        String name = type.name();
        return name.equals("PRIMED_TNT")
                || name.equals("TNT")
                || name.equals("MINECART_TNT")
                || name.equals("TNT_MINECART");
    }
}
