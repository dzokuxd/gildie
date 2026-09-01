package pl.gildie;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import pl.gildie.commands.GCommand;
import pl.gildie.listeners.ExplosionListener;
import pl.gildie.listeners.InventoryListener;
import pl.gildie.listeners.ProtectionListener;
import pl.gildie.listeners.TerritoryListener;
import pl.gildie.managers.DigManager;
import pl.gildie.managers.GuildManager;
import pl.gildie.managers.RegenManager;
import pl.gildie.managers.TerritoryBarManager;

public class GildiePlugin extends JavaPlugin {
    private GuildManager guildManager;
    private RegenManager regenManager;
    private TerritoryBarManager territoryBarManager;
    private DigManager digManager;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        guildManager = new GuildManager(this);
        regenManager = new RegenManager(this);
        territoryBarManager = new TerritoryBarManager(this, guildManager, regenManager);
        territoryBarManager.start();
        digManager = new DigManager(this, guildManager);

        getCommand("g").setExecutor(new GCommand(guildManager, regenManager, territoryBarManager));
        getServer().getPluginManager().registerEvents(new ProtectionListener(guildManager), this);
        getServer().getPluginManager().registerEvents(new ExplosionListener(guildManager, regenManager), this);
        getServer().getPluginManager().registerEvents(new TerritoryListener(territoryBarManager, regenManager), this);
        getServer().getPluginManager().registerEvents(new InventoryListener(digManager), this);
        getLogger().info("Gildie 1.1 wlaczone. Zapis flat: gildie.yml + regen.yml");
    }

    @Override
    public void onDisable() {
        if (territoryBarManager != null) {
            territoryBarManager.shutdown();
        }
        if (guildManager != null) {
            guildManager.save();
        }
        if (regenManager != null) {
            regenManager.save();
        }
    }
    public DigManager getDigManager() {
        return digManager;
    }
}
