package pl.gildie.war;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduler + flaga globalna tntEnabled.
 * TNT niszczy bloki tylko w godzinach 16:00–21:00 (konfigurowalne).
 */
public final class TntManager {

    private static final AtomicBoolean tntEnabled = new AtomicBoolean(false);
    /** null = według godzin, true/false = wymuszenie admina (/wojna tnt) */
    private static Boolean forced = null;
    private static BukkitTask task;
    private static int startHour = 16;
    private static int endHour = 21;
    private static ZoneId zone = ZoneId.of("Europe/Warsaw");

    private TntManager() {}

    public static void start(JavaPlugin plugin) {
        startHour = plugin.getConfig().getInt("tnt.start-hour", 16);
        endHour = plugin.getConfig().getInt("tnt.end-hour", 21);
        String zoneStr = plugin.getConfig().getString("tnt.timezone", "Europe/Warsaw");
        try {
            zone = ZoneId.of(zoneStr);
        } catch (Exception ignored) {
            zone = ZoneId.of("Europe/Warsaw");
        }

        // Sprawdzaj co 30 sekund
        task = Bukkit.getScheduler().runTaskTimer(plugin, TntManager::checkTime, 20L, 20L * 30);
        checkTime(); // od razu
        plugin.getLogger().info("TNT scheduler uruchomiony (" + startHour + ":00–" + endHour + ":00, " + zone + ")");
    }

    public static void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private static void checkTime() {
        LocalTime now = LocalTime.now(zone);
        int hour = now.getHour();
        boolean natural = hour >= startHour && hour < endHour;
        boolean should = forced != null ? forced : natural;
        boolean was = tntEnabled.getAndSet(should);
        if (was != should) {
            if (should) {
                Bukkit.broadcastMessage("§a§l[TNT] §aTNT zostało §lwłączone§a (godziny " + startHour + "–" + endHour + ").");
            } else {
                Bukkit.broadcastMessage("§c§l[TNT] §cTNT zostało §lwyłączone§c. Wybuchy nie niszczą bloków.");
            }
        }
    }

    public static boolean isTntEnabled() {
        return tntEnabled.get();
    }

    /** Na potrzeby testów / admin. Scheduler nie nadpisuje, dopóki nie wywołasz clearForce(). */
    public static void setEnabled(boolean enabled) {
        forced = enabled;
        tntEnabled.set(enabled);
    }

    public static void clearForce() {
        forced = null;
        checkTime();
    }
}
