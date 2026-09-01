package pl.gildie;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import pl.gildie.commands.GCommand;
import pl.gildie.listeners.ExplosionListener;
import pl.gildie.listeners.InventoryListener;
import pl.gildie.listeners.InviteWandListener;
import pl.gildie.listeners.JoinListener;
import pl.gildie.listeners.PeriscopeListener;
import pl.gildie.listeners.ProtectionListener;
import pl.gildie.listeners.TerritoryListener;
import pl.gildie.managers.DigManager;
import pl.gildie.managers.GuildManager;
import pl.gildie.managers.PeriscopeManager;
import pl.gildie.managers.RegenManager;
import pl.gildie.managers.TerritoryBarManager;
import pl.gildie.model.Guild;
import pl.gildie.util.ItemCost;
import pl.gildie.util.WaypointHook;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GildiePlugin extends JavaPlugin {
    private GuildManager guildManager;
    private RegenManager regenManager;
    private TerritoryBarManager territoryBarManager;
    private DigManager digManager;
    private PeriscopeManager periscopeManager;
    private ItemCost inviteCost;
    private final Map<UUID, Long> inviteWandUsers = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        saveDefaultConfig();
        reloadInviteCost();

        guildManager = new GuildManager(this);
        regenManager = new RegenManager(this);
        territoryBarManager = new TerritoryBarManager(this, guildManager, regenManager);
        territoryBarManager.start();
        digManager = new DigManager(this, guildManager);
        periscopeManager = new PeriscopeManager(this, guildManager);

        GCommand gCommand = new GCommand(this, guildManager, regenManager, territoryBarManager);
        getCommand("g").setExecutor(gCommand);
        getCommand("g").setTabCompleter(gCommand);

        getServer().getPluginManager().registerEvents(new ProtectionListener(guildManager), this);
        getServer().getPluginManager().registerEvents(new ExplosionListener(guildManager, regenManager), this);
        getServer().getPluginManager().registerEvents(new TerritoryListener(territoryBarManager, regenManager), this);
        getServer().getPluginManager().registerEvents(new InventoryListener(digManager), this);
        getServer().getPluginManager().registerEvents(new InviteWandListener(this, gCommand), this);
        getServer().getPluginManager().registerEvents(new JoinListener(this, guildManager), this);
        getServer().getPluginManager().registerEvents(new PeriscopeListener(periscopeManager), this);

        getServer().getScheduler().runTaskTimer(this, () -> guildManager.tickRaidBases(), 20L * 30, 20L * 30);

        getServer().getScheduler().runTaskTimer(this, () -> {
            long now = System.currentTimeMillis();
            for (Guild g : guildManager.getAll()) {
                g.getPendingAlliance().entrySet().removeIf(e -> e.getValue() < now);
            }
        }, 20L * 30, 20L * 30);

        getServer().getScheduler().runTaskLater(this, WaypointHook::reset, 40L);

        getLogger().info("Gildie 1.2 włączone. Peryskop + Sojusze + WP");
    }

    @Override
    public void onDisable() {
        if (territoryBarManager != null) territoryBarManager.shutdown();
        if (guildManager != null) guildManager.save();
        if (regenManager != null) regenManager.save();
        inviteWandUsers.clear();
    }

    public void reloadInviteCost() {
        reloadConfig();
        Map<Material, Integer> defaults = new LinkedHashMap<>();
        defaults.put(Material.DIAMOND, 4);
        inviteCost = ItemCost.fromConfig(getConfig().getConfigurationSection("invite-cost"), defaults);
    }

    public ItemCost getInviteCost() { return inviteCost; }
    public Map<UUID, Long> getInviteWandUsers() { return inviteWandUsers; }
    public GuildManager getGuildManager() { return guildManager; }
    public DigManager getDigManager() { return digManager; }

    public PeriscopeManager getPeriscopeManager() {
        return periscopeManager;
    }
}
