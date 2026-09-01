package pl.gildie.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Soft-depend hook do ReiMinimap WaypointAPI (bez twardej zależności Maven).
 */
public final class WaypointHook {

    private static Object api;
    private static Method addGuildWaypointPlayer;
    private static Method addGuildWaypointTag;
    private static Method removeGuildWaypoint;
    private static Method addGlobalWaypoint;
    private static Method removeWaypoint;
    private static boolean tried;
    private static final Logger LOG = Bukkit.getLogger();

    private WaypointHook() {
    }

    public static boolean isAvailable() {
        ensure();
        return api != null;
    }

    private static void ensure() {
        if (tried) {
            return;
        }
        tried = true;
        try {
            Class<?> apiClass = Class.forName("pl.ourproject.server.api.WaypointAPI");
            Method get = apiClass.getMethod("get");
            api = get.invoke(null);
            if (api == null) {
                LOG.info("[Gildie] WaypointAPI niedostępne (ReiMinimap nie załadowany).");
                return;
            }
            addGuildWaypointPlayer = apiClass.getMethod("addGuildWaypoint", Player.class, String.class, Location.class, int.class);
            addGuildWaypointTag = apiClass.getMethod("addGuildWaypoint", String.class, String.class, Location.class, int.class);
            removeGuildWaypoint = apiClass.getMethod("removeGuildWaypoint", UUID.class);
            addGlobalWaypoint = apiClass.getMethod("addGlobalWaypoint", String.class, Location.class, int.class);
            removeWaypoint = apiClass.getMethod("removeWaypoint", UUID.class);
            LOG.info("[Gildie] Podpięto WaypointAPI (ReiMinimap).");
        } catch (Throwable t) {
            api = null;
            LOG.info("[Gildie] WaypointAPI niedostępne: " + t.getMessage());
        }
    }

    /** WP tylko dla członków gildii (sesyjny). */
    @SuppressWarnings("unchecked")
    public static Optional<UUID> addGuildWaypoint(Player source, String name, Location loc, int color) {
        ensure();
        if (api == null || source == null || loc == null) {
            return Optional.empty();
        }
        try {
            Object result = addGuildWaypointPlayer.invoke(api, source, name, loc, color);
            if (result instanceof Optional) {
                return (Optional<UUID>) result;
            }
        } catch (Throwable t) {
            LOG.warning("[Gildie] addGuildWaypoint failed: " + t.getMessage());
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public static Optional<UUID> addGuildWaypoint(String guildTag, String name, Location loc, int color) {
        ensure();
        if (api == null || guildTag == null || loc == null) {
            return Optional.empty();
        }
        try {
            Object result = addGuildWaypointTag.invoke(api, guildTag, name, loc, color);
            if (result instanceof Optional) {
                return (Optional<UUID>) result;
            }
        } catch (Throwable t) {
            LOG.warning("[Gildie] addGuildWaypoint(tag) failed: " + t.getMessage());
        }
        return Optional.empty();
    }

    public static void removeGuildWaypoint(UUID id) {
        ensure();
        if (api == null || id == null) {
            return;
        }
        try {
            removeGuildWaypoint.invoke(api, id);
        } catch (Throwable t) {
            LOG.warning("[Gildie] removeGuildWaypoint failed: " + t.getMessage());
        }
    }

    public static UUID addGlobalWaypoint(String name, Location loc, int color) {
        ensure();
        if (api == null || loc == null) {
            return null;
        }
        try {
            Object result = addGlobalWaypoint.invoke(api, name, loc, color);
            if (result instanceof UUID) {
                return (UUID) result;
            }
        } catch (Throwable t) {
            LOG.warning("[Gildie] addGlobalWaypoint failed: " + t.getMessage());
        }
        return null;
    }

    public static void removeWaypoint(UUID id) {
        ensure();
        if (api == null || id == null) {
            return;
        }
        try {
            removeWaypoint.invoke(api, id);
        } catch (Throwable t) {
            LOG.warning("[Gildie] removeWaypoint failed: " + t.getMessage());
        }
    }

    public static void reset() {
        tried = false;
        api = null;
    }
}
