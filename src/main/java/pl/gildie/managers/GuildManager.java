package pl.gildie.managers;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import pl.gildie.model.Guild;

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

    public Collection<Guild> getAll() {
        return guilds.values();
    }
}
