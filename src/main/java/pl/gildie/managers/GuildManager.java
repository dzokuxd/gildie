package pl.gildie.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import pl.gildie.model.Guild;
import pl.gildie.util.WaypointHook;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GuildManager {
    private final JavaPlugin plugin;
    private final Map<String, Guild> guilds = new HashMap<>();
    private final File file;

    public GuildManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "gildie.yml");
        load();
    }

    public void load() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Nie mozna utworzyc gildie.yml: " + e.getMessage());
            }
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        guilds.clear();

        ConfigurationSection section = config.getConfigurationSection("guilds");
        if (section == null) {
            return;
        }

        for (String tag : section.getKeys(false)) {
            String path = "guilds." + tag;
            UUID owner = UUID.fromString(config.getString(path + ".owner"));
            String world = config.getString(path + ".world");
            double x = config.getDouble(path + ".x");
            double y = config.getDouble(path + ".y");
            double z = config.getDouble(path + ".z");
            int radius = config.getInt(path + ".radius", 50);

            Guild guild = new Guild(tag, owner, world, x, y, z, radius);
            List<String> members = config.getStringList(path + ".members");
            for (String member : members) {
                guild.addMember(UUID.fromString(member));
            }
            List<String> deputies = config.getStringList(path + ".deputies");
            for (String d : deputies) {
                try {
                    guild.addDeputy(UUID.fromString(d));
                } catch (Exception ignored) {
                }
            }

            if (config.contains(path + ".home.world")) {
                guild.loadHome(
                        config.getString(path + ".home.world"),
                        config.getDouble(path + ".home.x"),
                        config.getDouble(path + ".home.y"),
                        config.getDouble(path + ".home.z")
                );
            }

            if (config.contains(path + ".raid.world")) {
                long exp = config.getLong(path + ".raid.expires", 0);
                UUID wpId = null;
                String wpStr = config.getString(path + ".raid.waypoint");
                if (wpStr != null && !wpStr.isBlank()) {
                    try {
                        wpId = UUID.fromString(wpStr);
                    } catch (Exception ignored) {
                    }
                }
                guild.loadRaidBase(
                        config.getString(path + ".raid.world"),
                        config.getDouble(path + ".raid.x"),
                        config.getDouble(path + ".raid.y"),
                        config.getDouble(path + ".raid.z"),
                        exp,
                        wpId
                );
            }

            String gwp = config.getString(path + ".guild-waypoint");
            if (gwp != null && !gwp.isBlank()) {
                try {
                    guild.setGuildWaypointId(UUID.fromString(gwp));
                } catch (Exception ignored) {
                }
            }

            java.util.List<String> alliesList = config.getStringList(path + ".allies");
            guild.loadAllies(alliesList);

            guilds.put(tag.toUpperCase(), guild);
        }
    }

    public void save() {
        FileConfiguration config = new YamlConfiguration();
        for (Guild guild : guilds.values()) {
            String path = "guilds." + guild.getTag();
            config.set(path + ".owner", guild.getOwner().toString());
            config.set(path + ".world", guild.getWorldName());
            config.set(path + ".x", guild.getX());
            config.set(path + ".y", guild.getY());
            config.set(path + ".z", guild.getZ());
            config.set(path + ".radius", guild.getRadius());
            List<String> members = new ArrayList<>();
            for (UUID uuid : guild.getMembers()) {
                members.add(uuid.toString());
            }
            config.set(path + ".members", members);
            List<String> deputies = new ArrayList<>();
            for (UUID uuid : guild.getDeputies()) {
                deputies.add(uuid.toString());
            }
            config.set(path + ".deputies", deputies);

            if (guild.hasHome()) {
                config.set(path + ".home.world", guild.getHomeWorld());
                config.set(path + ".home.x", guild.getHomeX());
                config.set(path + ".home.y", guild.getHomeY());
                config.set(path + ".home.z", guild.getHomeZ());
            }

            if (guild.getRaidWorld() != null && guild.getRaidExpiresAt() > 0) {
                config.set(path + ".raid.world", guild.getRaidWorld());
                config.set(path + ".raid.x", guild.getRaidX());
                config.set(path + ".raid.y", guild.getRaidY());
                config.set(path + ".raid.z", guild.getRaidZ());
                config.set(path + ".raid.expires", guild.getRaidExpiresAt());
                if (guild.getRaidWaypointId() != null) {
                    config.set(path + ".raid.waypoint", guild.getRaidWaypointId().toString());
                }
            }

            if (guild.getGuildWaypointId() != null) {
                config.set(path + ".guild-waypoint", guild.getGuildWaypointId().toString());
            }

            config.set(path + ".allies", new java.util.ArrayList<>(guild.getAllies()));
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie mozna zapisac gildie.yml: " + e.getMessage());
        }
    }

    public boolean createGuild(String tag, UUID owner, Location center, int radius) {
        tag = tag.toUpperCase();
        if (guilds.containsKey(tag)) {
            return false;
        }
        if (getGuildByPlayer(owner) != null) {
            return false;
        }
        Guild guild = new Guild(tag, owner, center, radius);
        guilds.put(tag, guild);
        save();

        // WP gildyjny na środku, Y=70
        Location wpLoc = guild.getCenterAtY(70);
        Player leader = Bukkit.getPlayer(owner);
        if (wpLoc != null && leader != null && leader.isOnline()) {
            WaypointHook.addGuildWaypoint(leader, "Gildia " + tag, wpLoc, 0x55FF55)
                    .ifPresent(guild::setGuildWaypointId);
        } else if (wpLoc != null) {
            WaypointHook.addGuildWaypoint(tag, "Gildia " + tag, wpLoc, 0x55FF55)
                    .ifPresent(guild::setGuildWaypointId);
        }
        save();
        return true;
    }

    public boolean disband(Guild guild) {
        for (String allyTag : new java.util.HashSet<>(guild.getAllies())) {
            Guild ally = getGuild(allyTag);
            if (ally != null) {
                ally.removeAlly(guild.getTag());
            }
        }
        guild.getAllies().clear();
        if (guild.getGuildWaypointId() != null) {
            WaypointHook.removeGuildWaypoint(guild.getGuildWaypointId());
        }
        if (guild.getRaidWaypointId() != null) {
            WaypointHook.removeGuildWaypoint(guild.getRaidWaypointId());
        }
        guilds.remove(guild.getTag());
        save();
        return true;
    }

    public Guild getGuild(String tag) {
        return guilds.get(tag.toUpperCase());
    }

    public Guild getGuildByPlayer(UUID uuid) {
        for (Guild guild : guilds.values()) {
            if (guild.isMember(uuid)) {
                return guild;
            }
        }
        return null;
    }

    public Guild getGuildAt(Location loc) {
        for (Guild guild : guilds.values()) {
            if (guild.isInTerritory(loc)) {
                return guild;
            }
        }
        return null;
    }

    /** Najbliższa obca gildia (po dystansie do granicy). */
    public Guild getNearestEnemyGuild(Location loc, Guild own) {
        Guild best = null;
        double bestDist = Double.MAX_VALUE;
        for (Guild g : guilds.values()) {
            if (own != null && g.getTag().equals(own.getTag())) {
                continue;
            }
            double d = g.distanceToBorder(loc);
            // d może być ujemne (wewnątrz) — interesuje nas bliskość
            double abs = Math.abs(d);
            if (abs < bestDist) {
                bestDist = abs;
                best = g;
            }
        }
        return best;
    }

    /** Czy lokacja jest w pobliżu obcego terenu (w promieniu maxNear od granicy). */
    public boolean isNearEnemyTerritory(Location loc, Guild own, double maxNear) {
        for (Guild g : guilds.values()) {
            if (own != null && g.getTag().equals(own.getTag())) {
                continue;
            }
            double d = g.distanceToBorder(loc);
            // tylko NA ZEWNĄTRZ, w pasie maxNear od granicy
            if (d > 0 && d <= maxNear) {
                return true;
            }
        }
        return false;
    }

    public Guild getRaidBaseOwnerAt(Location loc) {
        for (Guild g : guilds.values()) {
            if (g.isRaidBaseBlock(loc)) {
                return g;
            }
        }
        return null;
    }


    public void checkAllianceLimit(Guild guild) {
        if (guild == null) return;
        boolean changed = false;
        for (String allyTag : new java.util.HashSet<>(guild.getAllies())) {
            Guild ally = getGuild(allyTag);
            if (ally == null) {
                guild.removeAlly(allyTag);
                changed = true;
                continue;
            }
            int total = guild.getMembers().size() + ally.getMembers().size();
            if (total > 15) {
                guild.removeAlly(allyTag);
                ally.removeAlly(guild.getTag());
                changed = true;
                for (java.util.UUID id : guild.getMembers()) {
                    org.bukkit.entity.Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOnline()) {
                        p.sendMessage("§cSojusz z §e" + allyTag + " §czostał automatycznie zerwany (limit 15 osób).");
                    }
                }
                for (java.util.UUID id : ally.getMembers()) {
                    org.bukkit.entity.Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOnline()) {
                        p.sendMessage("§cSojusz z §e" + guild.getTag() + " §czostał automatycznie zerwany (limit 15 osób).");
                    }
                }
            }
        }
        if (changed) save();
    }

    public Collection<Guild> getAll() {
        return guilds.values();
    }

    public void tickRaidBases() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Guild g : guilds.values()) {
            if (g.getRaidExpiresAt() > 0 && g.getRaidExpiresAt() <= now) {
                if (g.getRaidWaypointId() != null) {
                    WaypointHook.removeGuildWaypoint(g.getRaidWaypointId());
                }
                g.clearRaidBase();
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }
}
