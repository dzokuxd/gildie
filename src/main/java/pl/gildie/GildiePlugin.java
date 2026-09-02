package pl.gildie;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import pl.gildie.commands.GCommand;
import pl.gildie.commands.WojnaCommand;
import pl.gildie.listeners.ExplosionListener;
import pl.gildie.listeners.InventoryListener;
import pl.gildie.listeners.InviteWandListener;
import pl.gildie.listeners.JoinListener;
import pl.gildie.listeners.PeriscopeListener;
import pl.gildie.listeners.ProtectionListener;
import pl.gildie.listeners.TerritoryListener;
import pl.gildie.listeners.WarListener;
import pl.gildie.managers.DigManager;
import pl.gildie.managers.GuildManager;
import pl.gildie.managers.PeriscopeManager;
import pl.gildie.managers.RegenManager;
import pl.gildie.managers.TerritoryBarManager;
import pl.gildie.model.Guild;
import pl.gildie.util.ItemCost;
import pl.gildie.util.WaypointHook;
import pl.gildie.war.EggHologram;
import pl.gildie.war.TntManager;
import pl.gildie.war.WarManager;

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
    private WarManager warManager;
    private EggHologram eggHologram;
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
        warManager = new WarManager(this, guildManager);
        eggHologram = new EggHologram(this);

        GCommand gCommand = new GCommand(this, guildManager, regenManager, territoryBarManager);
        getCommand("g").setExecutor(gCommand);
        getCommand("g").setTabCompleter(gCommand);

        WojnaCommand wojnaCommand = new WojnaCommand(guildManager, warManager);
        if (getCommand("wojna") != null) {
            getCommand("wojna").setExecutor(wojnaCommand);
            getCommand("wojna").setTabCompleter(wojnaCommand);
        }

        getServer().getPluginManager().registerEvents(new ProtectionListener(guildManager), this);
        getServer().getPluginManager().registerEvents(new ExplosionListener(guildManager, regenManager), this);
        getServer().getPluginManager().registerEvents(new TerritoryListener(territoryBarManager, regenManager), this);
        getServer().getPluginManager().registerEvents(new InventoryListener(digManager), this);
        getServer().getPluginManager().registerEvents(new InviteWandListener(this, gCommand), this);
        getServer().getPluginManager().registerEvents(new JoinListener(this, guildManager), this);
        getServer().getPluginManager().registerEvents(new PeriscopeListener(periscopeManager), this);
        getServer().getPluginManager().registerEvents(new WarListener(guildManager, warManager), this);

        TntManager.start(this);

        getServer().getScheduler().runTaskTimer(this, () -> guildManager.tickRaidBases(), 20L * 30, 20L * 30);
        getServer().getScheduler().runTaskTimer(this, () -> warManager.tick(), 20L * 20, 20L * 20);
        getServer().getScheduler().runTaskTimer(this, () -> warManager.tickEggRegen(), 20L * 10, 20L * 10);
        getServer().getScheduler().runTaskTimer(this, () -> warManager.flush(), 20L * 5, 20L * 5);

        getServer().getScheduler().runTaskTimer(this, () -> {
            long now = System.currentTimeMillis();
            for (Guild g : guildManager.getAll()) {
                g.getPendingAlliance().entrySet().removeIf(e -> e.getValue() < now);
            }
        }, 20L * 30, 20L * 30);

        getServer().getScheduler().runTaskLater(this, WaypointHook::reset, 40L);
        // Hologramy nad jajkami po załadowaniu światów
        getServer().getScheduler().runTaskLater(this, () -> {
            if (eggHologram != null) {
                eggHologram.cleanupWorldAndRespawn(guildManager.getAll());
            }
        }, 60L);

        getLogger().info("Gildie 1.3 + Wojny włączone. Jajo, hologram, regeneracja HP, sztandar, TNT 16-21, /wojna");
    }

    @Override
    public void onDisable() {
        TntManager.stop();
        if (eggHologram != null) eggHologram.removeAll();
        if (territoryBarManager != null) territoryBarManager.shutdown();
        if (guildManager != null) guildManager.save();
        if (regenManager != null) regenManager.save();
        if (warManager != null) warManager.save();
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
    public WarManager getWarManager() { return warManager; }
    public EggHologram getEggHologram() { return eggHologram; }

    public PeriscopeManager getPeriscopeManager() {
        return periscopeManager;
    }
}
