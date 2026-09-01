package pl.gildie.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Guild {
    private final String tag;
    private final UUID owner;
    private final Set<UUID> members = new HashSet<>();
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final int radius;

    public Guild(String tag, UUID owner, Location center, int radius) {
        this.tag = tag.toUpperCase();
        this.owner = owner;
        this.members.add(owner);
        this.worldName = center.getWorld().getName();
        this.x = center.getX();
        this.y = center.getY();
        this.z = center.getZ();
        this.radius = radius;
    }

    public Guild(String tag, UUID owner, String worldName, double x, double y, double z, int radius) {
        this.tag = tag.toUpperCase();
        this.owner = owner;
        this.members.add(owner);
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
    }

    public String getTag() {
        return tag;
    }

    public UUID getOwner() {
        return owner;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public String getWorldName() {
        return worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public int getRadius() {
        return radius;
    }

    public Location getCenter() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z);
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public boolean isInTerritory(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        if (!loc.getWorld().getName().equals(worldName)) {
            return false;
        }
        double dx = loc.getX() - x;
        double dz = loc.getZ() - z;
        return (dx * dx + dz * dz) <= (double) radius * radius;
    }

    public void addMember(UUID uuid) {
        members.add(uuid);
    }
}
