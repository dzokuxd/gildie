package pl.gildie.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.gildie.GildiePlugin;
import pl.gildie.managers.GuildManager;
import pl.gildie.managers.MenuManager;
import pl.gildie.managers.RegenManager;
import pl.gildie.managers.TerritoryBarManager;
import pl.gildie.model.Guild;
import pl.gildie.util.ItemCost;
import pl.gildie.util.TeleportUtil;
import pl.gildie.util.WaypointHook;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class GCommand implements CommandExecutor, TabCompleter {
    private static final int DEFAULT_RADIUS = 50;
    private static final int TELEPORT_SECONDS = 15;
    private static final long INVITE_EXPIRE_MS = 60_000L;
    private static final long RAID_DURATION_MS = 60L * 60L * 1000L; // 1h
    private static final double RAID_NEAR_BLOCKS = 30.0;
    private static final long ALLIANCE_EXPIRE_MS = 60_000L;
    private static final int ALLIANCE_MAX_MEMBERS = 15;
    private static final long ALLIANCE_REQUEST_COOLDOWN_MS = 30_000L;
    private static final ItemCost ALLIANCE_COST;
    static {
        Map<Material, Integer> cost = new LinkedHashMap<>();
        cost.put(Material.DIAMOND, 8);
        ALLIANCE_COST = new ItemCost(cost);
    }
    private final Map<UUID, Long> allianceCooldown = new HashMap<>();

    public static final String WAND_NAME = "§6§lRóżdżka zaproszeń gildii";

    private final GildiePlugin plugin;
    private final GuildManager guildManager;
    private final RegenManager regenManager;
    private final TerritoryBarManager territoryBarManager;

    public GCommand(GildiePlugin plugin, GuildManager guildManager, RegenManager regenManager, TerritoryBarManager territoryBarManager) {
        this.plugin = plugin;
        this.guildManager = guildManager;
        this.regenManager = regenManager;
        this.territoryBarManager = territoryBarManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cTylko gracz może używać tej komendy.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "zaloz", "stworz", "create" -> handleCreate(player, args);
            case "opusc", "leave" -> handleLeave(player);
            case "rozwiaz", "disband" -> handleDisband(player);
            case "wyrzuc", "kick" -> handleKick(player, args);
            case "zapros", "invite" -> handleInvite(player, args);
            case "dolacz", "accept", "akceptuj" -> handleAccept(player, args);
            case "odrzuc", "deny" -> handleDeny(player, args);
            case "info", "i" -> handleInfo(player, args);
            case "lista", "list" -> handleList(player);
            case "lider", "leader" -> handleLeader(player, args);
            case "zastepca", "deputy" -> handleDeputy(player, args);
            case "ustawdom", "sethome" -> handleSetHome(player);
            case "dom", "home" -> handleHome(player);
            case "regeneruj", "regen" -> handleRegen(player);
            case "panel" -> MenuManager.openMainMenu(player);
            case "ustawbazawypadowa", "ubw" -> handleSetRaidBase(player);
            case "bazawypadowa", "bw" -> handleRaidBaseTp(player);
            case "peryskop", "p" -> {
                if (plugin.getPeriscopeManager() == null) {
                    player.sendMessage("§cPeryskop niedostępny.");
                } else {
                    plugin.getPeriscopeManager().start(player);
                }
            }
            case "sojusz", "ally" -> handleAlliance(player, args);
            case "wojna", "war" -> {
                if (plugin.getWarManager() == null) {
                    player.sendMessage("§cSystem wojen niedostępny.");
                } else {
                    pl.gildie.war.WarGui.openMain(player, guildManager, plugin.getWarManager());
                }
            }
            case "pp" -> handlePp(player);
            case "pomoc", "help" -> sendHelp(player);
            default -> {
                player.sendMessage("§cNieznana komenda. Użyj §e/g pomoc");
            }
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§8§m--------------------------------");
        player.sendMessage("§6§lGildie §7— komendy:");
        player.sendMessage("§e/g zaloz <tag> §7— załóż gildię (teren r=50)");
        player.sendMessage("§e/g zapros <nick|wand> §7— zaproś gracza / różdżka");
        player.sendMessage("§e/g dolacz <tag> §7— zaakceptuj zaproszenie");
        player.sendMessage("§e/g opusc §7— opuść gildię");
        player.sendMessage("§e/g wyrzuc <nick> §7— wyrzuć członka");
        player.sendMessage("§e/g lider <nick> §7— przekaż przywództwo");
        player.sendMessage("§e/g zastepca <nick> §7— nadaj/odbierz zastępcę");
        player.sendMessage("§e/g rozwiaz §7— rozwiąż gildię (lider)");
        player.sendMessage("§e/g info [tag] §7— informacje");
        player.sendMessage("§e/g lista §7— lista gildii");
        player.sendMessage("§e/g ustawdom §7— ustaw dom gildii");
        player.sendMessage("§e/g dom §7— TP do domu (15s, poza terenem)");
        player.sendMessage("§e/g ubw §7— ustaw bazę wypadową (1h, przy obcym)");
        player.sendMessage("§e/g bw §7— TP do bazy wypadowej (15s)");
        player.sendMessage("§e/g regeneruj §7— regen bloków ≤Y60");
        player.sendMessage("§e/g panel §7— menu fosy");
        player.sendMessage("§e/g peryskop §7— widok z góry na teren gildii");
        player.sendMessage("§e/g sojusz §7— zarządzanie sojuszami");
        player.sendMessage("§e/g wojna §7— panel wojen (GUI)");
        player.sendMessage("§e/g pp §7— ping lokalizacji do gildii (live WP)");
        player.sendMessage("§8§m--------------------------------");
    }

    /** /g pp — ping pomocy do gildii (WaypointAPI.helpPing) */
    private void handlePp(Player player) {
        if (!WaypointHook.isAvailable()) {
            player.sendMessage("§cSystem waypointów niedostępny (ReiMinimap).");
            return;
        }
        player.sendMessage(WaypointHook.helpPing(player));
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUżycie: /g zaloz <tag>");
            return;
        }
        String tag = args[1];
        if (!tag.matches("[A-Za-z0-9]{2,5}")) {
            player.sendMessage("§cTag musi mieć 2–5 znaków (litery i cyfry).");
            return;
        }
        if (guildManager.getGuildByPlayer(player.getUniqueId()) != null) {
            player.sendMessage("§cJesteś już w gildii!");
            return;
        }
        if (guildManager.getGuild(tag) != null) {
            player.sendMessage("§cTaka gildia już istnieje!");
            return;
        }
        if (guildManager.getGuildAt(player.getLocation()) != null) {
            player.sendMessage("§cStoisz na terenie innej gildii!");
            return;
        }

        boolean ok = guildManager.createGuild(tag, player.getUniqueId(), player.getLocation(), DEFAULT_RADIUS);
        if (ok) {
            player.sendMessage("§aZałożyłeś gildię §e" + tag.toUpperCase()
                    + " §az terenem o promieniu §e" + DEFAULT_RADIUS + " §abloków!");
            player.sendMessage("§7Waypoint gildii ustawiony na środku (Y=70).");
            territoryBarManager.update(player);
        } else {
            player.sendMessage("§cNie udało się założyć gildii.");
        }
    }

    private void handleLeave(Player player) {
        Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (guild == null) {
            player.sendMessage("§cNie jesteś w żadnej gildii!");
            return;
        }
        if (guild.isOwner(player.getUniqueId())) {
            player.sendMessage("§cLider nie może opuścić gildii. Użyj §e/g rozwiaz §club §e/g lider <nick>§c.");
            return;
        }
        guild.removeMember(player.getUniqueId());
        guildManager.save();
        player.sendMessage("§aOpuściłeś gildię §e" + guild.getTag() + "§a.");
        notifyOnlineMembers(guild, "§7" + player.getName() + " opuścił gildię.");
    }

    private void handleDisband(Player player) {
        Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (guild == null) {
            player.sendMessage("§cNie jesteś w żadnej gildii!");
            return;
        }
        if (!guild.isOwner(player.getUniqueId())) {
            player.sendMessage("§cTylko lider może rozwiązać gildię!");
            return;
        }
        String tag = guild.getTag();
        notifyOnlineMembers(guild, "§cGildia §e" + tag + " §czostała rozwiązana.");
        guildManager.disband(guild);
        player.sendMessage("§aRozwiązałeś gildię §e" + tag + "§a.");
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUżycie: /g wyrzuc <nick>");
            return;
        }
        Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (guild == null) {
            player.sendMessage("§cNie jesteś w żadnej gildii!");
            return;
        }
        if (!guild.isLeaderOrDeputy(player.getUniqueId())) {
            player.sendMessage("§cTylko lider lub zastępca może wyrzucać!");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        UUID targetId;
        String targetName;
        if (target != null) {
            targetId = target.getUniqueId();
            targetName = target.getName();
        } else {
            // offline — szukaj po nicku w memberach (tylko online dla prostoty)
            player.sendMessage("§cGracz musi być online.");
            return;
        }
        if (!guild.isMember(targetId)) {
            player.sendMessage("§cTen gracz nie jest w twojej gildii!");
            return;
        }
        if (guild.isOwner(targetId)) {
            player.sendMessage("§cNie możesz wyrzucić lidera!");
            return;
        }
        if (guild.isDeputy(targetId) && !guild.isOwner(player.getUniqueId())) {
            player.sendMessage("§cTylko lider może wyrzucić zastępcę!");
            return;
        }
        guild.removeMember(targetId);
        guildManager.save();
        target.sendMessage("§cZostałeś wyrzucony z gildii §e" + guild.getTag() + "§c.");
        notifyOnlineMembers(guild, "§7" + targetName + " został wyrzucony z gildii.");
        player.sendMessage("§aWyrzucono §e" + targetName + "§a.");
    }

    private void handleInvite(Player player, String[] args) {
        Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (guild == null) {
            player.sendMessage("§cNie jesteś w żadnej gildii!");
            return;
        }
        if (!guild.isLeaderOrDeputy(player.getUniqueId())) {
            player.sendMessage("§cTylko lider lub zastępca może zapraszać!");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§cUżycie: /g zapros <nick|wand>");
            return;
        }

        if (args[1].equalsIgnoreCase("wand") || args[1].equalsIgnoreCase("rozdzka")) {
            // Różdżka za darmo — opłata dopiero przy kliknięciu na gracza
            ItemStack wand = new ItemStack(Material.STICK);
            ItemMeta meta = wand.getItemMeta();
            meta.setDisplayName(WAND_NAME);
            ItemCost cost = plugin.getInviteCost();
            List<String> lore = new ArrayList<>();
            lore.add("§7Kliknij PPM na gracza, aby go zaprosić.");
            lore.add("§7Gildia: §e" + guild.getTag());
            if (!cost.isEmpty()) {
                lore.add("§7Koszt za zaproszenie: " + cost.describeInline());
            }
            lore.add("§8Ważna 5 minut.");
            meta.setLore(lore);
            wand.setItemMeta(meta);
            player.getInventory().addItem(wand);
            plugin.getInviteWandUsers().put(player.getUniqueId(), System.currentTimeMillis() + 300_000L);
            player.sendMessage("§aOtrzymałeś różdżkę zaproszeń (5 min, bez opłaty).");
            if (!cost.isEmpty()) {
                player.sendMessage("§7Opłata §f" + cost.describeInline() + " §7pobierana przy każdym zaproszeniu.");
            }
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cGracz offline lub nie znaleziony.");
            return;
        }
        tryInvite(player, guild, target);
    }

    /**
     * Wspólna logika zaproszenia: sprawdzenia + pobranie kosztu + invite.
     * @return true jeśli wysłano zaproszenie
     */
    private boolean tryInvite(Player leader, Guild guild, Player target) {
        if (guild.isMember(target.getUniqueId())) {
            leader.sendMessage("§cTen gracz jest już w twojej gildii!");
            return false;
        }
        if (guildManager.getGuildByPlayer(target.getUniqueId()) != null) {
            leader.sendMessage("§cTen gracz jest już w innej gildii!");
            return false;
        }

        ItemCost cost = plugin.getInviteCost();
        if (!cost.isEmpty()) {
            if (!cost.has(leader)) {
                leader.sendMessage("§cBrak przedmiotów na zaproszenie: " + cost.describeInline());
                return false;
            }
            if (!cost.take(leader)) {
                leader.sendMessage("§cBrak przedmiotów na zaproszenie: " + cost.describeInline());
                return false;
            }
        }

        guild.addInvite(target.getUniqueId(), System.currentTimeMillis() + INVITE_EXPIRE_MS);
        leader.sendMessage("§aZaproszono §e" + target.getName() + " §ado gildii.");
        if (!cost.isEmpty()) {
            leader.sendMessage("§7Pobrano: " + cost.describeInline());
        }
        target.sendMessage("§aOtrzymałeś zaproszenie do gildii §e" + guild.getTag() + "§a.");
        target.sendMessage("§7Wpisz §e/g dolacz " + guild.getTag() + " §7w ciągu 60s.");
        return true;
    }

    /** Wywołanie z listenera różdżki — opłata pobierana tutaj, przy kliknięciu. */
    public void inviteFromWand(Player leader, Player target) {
        Guild guild = guildManager.getGuildByPlayer(leader.getUniqueId());
        if (guild == null || !guild.isLeaderOrDeputy(leader.getUniqueId())) {
            leader.sendMessage("§cNie możesz zapraszać.");
            return;
        }
        tryInvite(leader, guild, target);
    }

    private void handleAccept(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUżycie: /g dolacz <tag>");
            return;
        }
        if (guildManager.getGuildByPlayer(player.getUniqueId()) != null) {
            player.sendMessage("§cJesteś już w gildii!");
            return;
        }
        Guild guild = guildManager.getGuild(args[1]);
        if (guild == null) {
            player.sendMessage("§cNie ma takiej gildii.");
            return;
        }
        if (!guild.hasInvite(player.getUniqueId())) {
            player.sendMessage("§cNie masz aktywnego zaproszenia do tej gildii.");
            return;
        }
        guild.addMember(player.getUniqueId());
        guildManager.save();
        guildManager.checkAllianceLimit(guild);
        player.sendMessage("§aDołączyłeś do gildii §e" + guild.getTag() + "§a!");
        notifyOnlineMembers(guild, "§a" + player.getName() + " dołączył do gildii!");
        territoryBarManager.update(player);
        // wyślij WP
        if (guild.getGuildWaypointId() != null) {
            org.bukkit.Location center = guild.getCenterAtY(70);
            if (center != null) {
                WaypointHook.addGuildWaypoint(player, "Gildia " + guild.getTag(), center, 0x55FF55);
            }
        }
        if (guild.hasActiveRaidBase()) {
            org.bukkit.Location raid = guild.getRaidBase();
            if (raid != null) {
                WaypointHook.addGuildWaypoint(player, "Baza wypadowa", raid, 0xFF5555);
            }
        }
    }

    private void handleDeny(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUżycie: /g odrzuc <tag>");
            return;
        }
        Guild guild = guildManager.getGuild(args[1]);
        if (guild == null) {
            player.sendMessage("§cNie ma takiej gildii.");
            return;
        }
        guild.removeInvite(player.getUniqueId());
        player.sendMessage("§7Odrzucono zaproszenie do §e" + guild.getTag() + "§7.");
    }

    private void handleInfo(Player player, String[] args) {
        Guild guild;
        if (args.length >= 2) {
            guild = guildManager.getGuild(args[1]);
            if (guild == null) {
                player.sendMessage("§cNie ma takiej gildii.");
                return;
            }
        } else {
            guild = guildManager.getGuildByPlayer(player.getUniqueId());
            if (guild == null) {
                player.sendMessage("§cNie jesteś w gildii. Użyj §e/g info <tag>");
                return;
            }
        }
        player.sendMessage("§8§m--------------------------------");
        player.sendMessage("§6Gildia: §e" + guild.getTag());
        String ownerName = nameOf(guild.getOwner());
        player.sendMessage("§7Lider: §f" + ownerName);
        player.sendMessage("§7Członków: §f" + guild.getMembers().size());
        player.sendMessage("§7Teren: §f" + guild.getWorldName()
                + " §7(" + (int) guild.getX() + ", " + (int) guild.getY() + ", " + (int) guild.getZ() + ") r=" + guild.getRadius());
        if (guild.hasHome()) {
            player.sendMessage("§7Dom: §f" + (int) guild.getHomeX() + ", " + (int) guild.getHomeY() + ", " + (int) guild.getHomeZ());
        }
        if (guild.hasActiveRaidBase()) {
            long left = (guild.getRaidExpiresAt() - System.currentTimeMillis()) / 1000;
            player.sendMessage("§7Baza wypadowa: §aaktywna §7(" + formatTime(left) + ")");
        }
        List<String> online = new ArrayList<>();
        for (UUID id : guild.getMembers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                String mark = guild.isOwner(id) ? "§6★" : (guild.isDeputy(id) ? "§e◆" : "§7•");
                online.add(mark + " §f" + p.getName());
            }
        }
        player.sendMessage("§7Online: " + (online.isEmpty() ? "§cbrak" : String.join("§7, ", online)));
        if (plugin.getWarManager() != null) {
            plugin.getWarManager().getActiveWarOf(guild.getTag()).ifPresent(war -> {
                String opponent = war.getOpponent(guild.getTag());
                long rem = war.getRemainingMs();
                long h = rem / 3_600_000L;
                long m = (rem % 3_600_000L) / 60_000L;
                player.sendMessage("§c⚔ Wojna z §e" + opponent + " §c– pozostało §f" + h + "h " + m + "min");
            });
        }
        player.sendMessage("§8§m--------------------------------");
    }

    private void handleList(Player player) {
        if (guildManager.getAll().isEmpty()) {
            player.sendMessage("§7Brak gildii na serwerze.");
            return;
        }
        player.sendMessage("§6Lista gildii:");
        for (Guild g : guildManager.getAll()) {
            long online = g.getMembers().stream()
                    .filter(id -> Bukkit.getPlayer(id) != null && Bukkit.getPlayer(id).isOnline())
                    .count();
            player.sendMessage("§e" + g.getTag() + " §7— " + g.getMembers().size() + " osób (§a" + online + " online§7)");
        }
    }

    private void handleLeader(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUżycie: /g lider <nick>");
            return;
        }
        Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (guild == null) {
            player.sendMessage("§cNie jesteś w gildii!");
            return;
        }
        if (!guild.isOwner(player.getUniqueId())) {
            player.sendMessage("§cTylko lider może przekazać przywództwo!");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !guild.isMember(target.getUniqueId())) {
            player.sendMessage("§cGracz musi być online i w gildii.");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§cJuż jesteś liderem.");
            return;
        }
        guild.setOwner(target.getUniqueId());
        guildManager.save();
        notifyOnlineMembers(guild, "§6" + target.getName() + " §ejest nowym liderem gildii!");
    }

    private void handleDeputy(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUżycie: /g zastepca <nick>");
            return;
        }
        Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (guild == null) {
            player.sendMessage("§cNie jesteś w gildii!");
            return;
        }
        if (!guild.isOwner(player.getUniqueId())) {
            player.sendMessage("§cTylko lider może nadawać zastępców!");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !guild.isMember(target.getUniqueId())) {
            player.sendMessage("§cGracz musi być online i w gildii.");
            return;
        }
        if (guild.isOwner(target.getUniqueId())) {
            player.sendMessage("§cLider nie może być zastępcą.");
            return;
        }
        if (guild.isDeputy(target.getUniqueId())) {
            guild.removeDeputy(target.getUniqueId());
            guildManager.save();
            player.sendMessage("§aOdebrano zastępcę §e" + target.getName() + "§a.");
            target.sendMessage("§cNie jesteś już zastępcą gildii §e" + guild.getTag() + "§c.");
        } else {
            guild.addDeputy(target.getUniqueId());
            guildManager.save();
            player.sendMessage("§aNadano zastępcę §e" + target.getName() + "§a.");
            target.sendMessage("§aZostałeś zastępcą gildii §e" + guild.getTag() + "§a!");
        }
    }

    private void handleSetHome(Player player) {
        Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (guild == null) {
            player.sendMessage("§cNie jesteś w gildii!");
            return;
        }
        if (!guild.isLeaderOrDeputy(player.getUniqueId())) {
            player.sendMessage("§cTylko lider lub zastępca może ustawić dom!");
            return;
        }
        if (!guild.isInTerritory(player.getLocation())) {
            player.sendMessage("§cDom można ustawić tylko na terenie gildii!");
            return;
        }
        guild.setHome(player.getLocation());
        guildManager.save();
        player.sendMessage("§aUstawiono dom gildii na twojej pozycji.");
    }

    private void handleHome(Player player) {
        Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (guild == null) {
            player.sendMessage("§cNie jesteś w gildii!");
            return;
        }
        // Na własnym terenie NIE działa — gracze muszą budować schody
        if (guild.isInTerritory(player.getLocation())) {
            player.sendMessage("§cNie możesz użyć /g dom na terenie własnej gildii! Zbuduj schody.");
            return;
        }
        Location home = guild.getHome();
        if (home == null) {
            player.sendMessage("§cDom gildii niedostępny (świat?).");
            return;
        }
        TeleportUtil.teleportCountdown(plugin, player, home, TELEPORT_SECONDS, "dom gildii " + guild.getTag());
    }

    private void handleRegen(Player player) {
        if (guildManager.getGuildByPlayer(player.getUniqueId()) == null) {
            player.sendMessage("§cNie jesteś w żadnej gildii!");
            return;
        }
        regenManager.startManualRegen(player);
    }

    private void handleSetRaidBase(Player player) {
        Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (guild == null) {
            player.sendMessage("§cNie jesteś w gildii!");
            return;
        }
        if (!guild.isLeaderOrDeputy(player.getUniqueId())) {
            player.sendMessage("§cTylko lider lub zastępca może ustawić bazę wypadową!");
            return;
        }
        Location loc = player.getLocation();
        // nie na własnym terenie
        if (guild.isInTerritory(loc)) {
            player.sendMessage("§cBazy wypadowej nie ustawisz na własnym terenie!");
            return;
        }
        // nie na żadnym obcym terenie
        if (guildManager.getGuildAt(loc) != null) {
            player.sendMessage("§cNie możesz ustawić bazy wypadowej na terenie obcej gildii!");
            return;
        }
        // musi być przy obcym terenie
        if (!guildManager.isNearEnemyTerritory(loc, guild, RAID_NEAR_BLOCKS)) {
            player.sendMessage("§cBazę wypadową możesz ustawić tylko w pobliżu obcego terenu (≤" + (int) RAID_NEAR_BLOCKS + " bloków od granicy)!");
            return;
        }
        // usuń stary WP
        if (guild.getRaidWaypointId() != null) {
            WaypointHook.removeGuildWaypoint(guild.getRaidWaypointId());
        }

        UUID wpId = WaypointHook.addGuildWaypoint(player, "Baza wypadowa", loc, 0xFF5555).orElse(null);
        guild.setRaidBase(loc, RAID_DURATION_MS, wpId);
        guildManager.save();

        player.sendMessage("§aUstawiono bazę wypadową na 1 godzinę!");
        player.sendMessage("§7Na tym bloku (1x1) nikt nie może budować ani niszczyć.");
        player.sendMessage("§7Użyj §e/g bw §7aby się tam teleportować.");
        notifyOnlineMembers(guild, "§cBaza wypadowa ustawiona! §7(/g bw)");
    }

    private void handleRaidBaseTp(Player player) {
        Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (guild == null) {
            player.sendMessage("§cNie jesteś w gildii!");
            return;
        }
        if (!guild.hasActiveRaidBase()) {
            player.sendMessage("§cBrak aktywnej bazy wypadowej! Ustaw przez §e/g ubw§c.");
            return;
        }
        Location dest = guild.getRaidBase();
        if (dest == null) {
            player.sendMessage("§cBaza wypadowa niedostępna (świat?).");
            return;
        }
        long left = (guild.getRaidExpiresAt() - System.currentTimeMillis()) / 1000;
        TeleportUtil.teleportCountdown(plugin, player, dest, TELEPORT_SECONDS,
                "baza wypadowa (" + formatTime(left) + " pozostało)");
    }


    private void handleAlliance(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§8§m--------------------------------");
            player.sendMessage("§6§lSojusze:");
            player.sendMessage("§e/g sojusz <tag> §7— wyślij prośbę (płacisz Ty)");
            player.sendMessage("§e/g sojusz akceptuj <tag> §7— zaakceptuj (płacisz Ty)");
            player.sendMessage("§e/g sojusz odrzuc <tag> §7— odrzuć");
            player.sendMessage("§e/g sojusz rozwiaz <tag> §7— zerwij sojusz");
            player.sendMessage("§e/g sojusz lista §7— lista sojuszników");
            player.sendMessage("§7Limit: §e1 sojusz§7, max §e" + ALLIANCE_MAX_MEMBERS + " §7osób łącznie");
            player.sendMessage("§8§m--------------------------------");
            return;
        }
        String sub = args[1].toLowerCase();
        switch (sub) {
            case "akceptuj", "accept" -> handleAllianceAccept(player, args);
            case "odrzuc", "deny" -> handleAllianceDeny(player, args);
            case "rozwiaz", "break" -> handleAllianceBreak(player, args);
            case "lista", "list" -> handleAllianceList(player);
            default -> handleAllianceRequest(player, args[1]);
        }
    }

    private void handleAllianceRequest(Player player, String targetTag) {
        Guild own = guildManager.getGuildByPlayer(player.getUniqueId());
        if (own == null) { player.sendMessage("§cNie jesteś w gildii!"); return; }
        if (!own.isLeaderOrDeputy(player.getUniqueId())) { player.sendMessage("§cTylko lider lub zastępca!"); return; }
        long now = System.currentTimeMillis();
        Long last = allianceCooldown.get(player.getUniqueId());
        if (last != null && now - last < ALLIANCE_REQUEST_COOLDOWN_MS) {
            long left = (ALLIANCE_REQUEST_COOLDOWN_MS - (now - last)) / 1000;
            player.sendMessage("§cPoczekaj §e" + left + "s §cprzed kolejną prośbą.");
            return;
        }
        Guild other = guildManager.getGuild(targetTag);
        if (other == null) { player.sendMessage("§cNie ma takiej gildii."); return; }
        if (other.getTag().equalsIgnoreCase(own.getTag())) { player.sendMessage("§cNie możesz zawrzeć sojuszu z samą sobą."); return; }
        if (own.isAlliedWith(other.getTag())) { player.sendMessage("§cJuż jesteście w sojuszu!"); return; }
        if (!own.getAllies().isEmpty()) { player.sendMessage("§cMożesz mieć tylko §e1 sojusz§c! Zerwij: §e/g sojusz rozwiaz <tag>"); return; }
        if (!other.getAllies().isEmpty()) { player.sendMessage("§cGildia §e" + other.getTag() + " §cma już sojusz."); return; }
        if (other.hasAllianceRequestFrom(own.getTag())) { player.sendMessage("§cProśba do tej gildii jest już aktywna."); return; }
        int total = own.getMembers().size() + other.getMembers().size();
        if (total > ALLIANCE_MAX_MEMBERS) {
            player.sendMessage("§cSojusz przekroczyłby limit §e" + ALLIANCE_MAX_MEMBERS + " §cosób.");
            return;
        }
        if (!ALLIANCE_COST.has(player) || !ALLIANCE_COST.take(player)) {
            player.sendMessage("§cBrak przedmiotów na sojusz: " + ALLIANCE_COST.describeInline());
            return;
        }
        allianceCooldown.put(player.getUniqueId(), now);
        other.addAllianceRequest(own.getTag(), now + ALLIANCE_EXPIRE_MS);
        player.sendMessage("§aWysłano prośbę o sojusz do §e" + other.getTag() + "§a.");
        player.sendMessage("§7Pobrano: " + ALLIANCE_COST.describeInline());
        notifyOnlineMembers(other, "§6§lSOJUSZ §7» §e" + own.getTag() + " §7prosi o sojusz! §e/g sojusz akceptuj " + own.getTag());
    }

    private void handleAllianceAccept(Player player, String[] args) {
        if (args.length < 3) { player.sendMessage("§cUżycie: /g sojusz akceptuj <tag>"); return; }
        Guild own = guildManager.getGuildByPlayer(player.getUniqueId());
        if (own == null) { player.sendMessage("§cNie jesteś w gildii!"); return; }
        if (!own.isLeaderOrDeputy(player.getUniqueId())) { player.sendMessage("§cTylko lider lub zastępca!"); return; }
        Guild other = guildManager.getGuild(args[2]);
        if (other == null || !own.hasAllianceRequestFrom(other.getTag())) {
            player.sendMessage("§cBrak aktywnej prośby od tej gildii.");
            return;
        }
        if (!own.getAllies().isEmpty()) { player.sendMessage("§cMasz już sojusz!"); own.removeAllianceRequest(other.getTag()); return; }
        if (!other.getAllies().isEmpty()) { player.sendMessage("§cTa gildia ma już inny sojusz."); own.removeAllianceRequest(other.getTag()); return; }
        int total = own.getMembers().size() + other.getMembers().size();
        if (total > ALLIANCE_MAX_MEMBERS) {
            player.sendMessage("§cSojusz przekroczyłby limit §e" + ALLIANCE_MAX_MEMBERS + " §cosób.");
            own.removeAllianceRequest(other.getTag());
            return;
        }
        if (!ALLIANCE_COST.has(player) || !ALLIANCE_COST.take(player)) {
            player.sendMessage("§cBrak przedmiotów na sojusz: " + ALLIANCE_COST.describeInline());
            return;
        }
        own.removeAllianceRequest(other.getTag());
        own.addAlly(other.getTag());
        other.addAlly(own.getTag());
        guildManager.save();
        notifyOnlineMembers(own, "§6§lSOJUSZ §7» §aZawarto sojusz z §e" + other.getTag() + "§a!");
        notifyOnlineMembers(other, "§6§lSOJUSZ §7» §aZawarto sojusz z §e" + own.getTag() + "§a!");
    }

    private void handleAllianceDeny(Player player, String[] args) {
        if (args.length < 3) { player.sendMessage("§cUżycie: /g sojusz odrzuc <tag>"); return; }
        Guild own = guildManager.getGuildByPlayer(player.getUniqueId());
        if (own == null || !own.isLeaderOrDeputy(player.getUniqueId())) { player.sendMessage("§cBrak uprawnień."); return; }
        Guild other = guildManager.getGuild(args[2]);
        if (other == null) { player.sendMessage("§cNie ma takiej gildii."); return; }
        if (!own.hasAllianceRequestFrom(other.getTag())) {
            player.sendMessage("§cNie masz aktywnej prośby od §e" + other.getTag() + "§c.");
            return;
        }
        own.removeAllianceRequest(other.getTag());
        player.sendMessage("§7Odrzucono prośbę o sojusz od §e" + other.getTag() + "§7.");
        for (UUID id : other.getMembers()) {
            if (!other.isLeaderOrDeputy(id)) continue;
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                p.sendMessage("§cGildia §e" + own.getTag() + " §codrzuciła waszą prośbę o sojusz.");
            }
        }
    }

    private void handleAllianceBreak(Player player, String[] args) {
        if (args.length < 3) { player.sendMessage("§cUżycie: /g sojusz rozwiaz <tag>"); return; }
        Guild own = guildManager.getGuildByPlayer(player.getUniqueId());
        if (own == null || !own.isLeaderOrDeputy(player.getUniqueId())) { player.sendMessage("§cBrak uprawnień."); return; }
        Guild other = guildManager.getGuild(args[2]);
        if (other == null || !own.isAlliedWith(other.getTag())) {
            player.sendMessage("§cNie jesteście w sojuszu z tą gildią.");
            return;
        }
        own.removeAlly(other.getTag());
        other.removeAlly(own.getTag());
        guildManager.save();
        notifyOnlineMembers(own, "§c§lSOJUSZ §7» Zerwano sojusz z §e" + other.getTag() + "§c.");
        notifyOnlineMembers(other, "§c§lSOJUSZ §7» §e" + own.getTag() + " §czerwało z wami sojusz.");
    }

    private void handleAllianceList(Player player) {
        Guild own = guildManager.getGuildByPlayer(player.getUniqueId());
        if (own == null) { player.sendMessage("§cNie jesteś w gildii!"); return; }
        if (own.getAllies().isEmpty()) { player.sendMessage("§7Brak sojuszników."); return; }
        player.sendMessage("§6Sojusznicy:");
        for (String tag : own.getAllies()) {
            Guild g = guildManager.getGuild(tag);
            int size = g != null ? g.getMembers().size() : 0;
            player.sendMessage("§e" + tag + " §7(" + size + " osób)");
        }
    }

    private void notifyOnlineMembers(Guild guild, String msg) {
        for (UUID id : guild.getMembers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                p.sendMessage(msg);
            }
        }
    }

    private static String nameOf(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) return p.getName();
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }

    private static String formatTime(long seconds) {
        if (seconds < 0) seconds = 0;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

@Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = Arrays.asList(
                    "zaloz", "zapros", "dolacz", "odrzuc", "opusc", "wyrzuc", "rozwiaz",
                    "info", "lista", "lider", "zastepca", "ustawdom", "dom",
                    "ustawbazawypadowa", "ubw", "bazawypadowa", "bw",
                    "regeneruj", "panel", "peryskop", "p", "sojusz", "wojna", "pp", "pomoc"
            );
            String input = args[0].toLowerCase();
            return subs.stream().filter(s -> s.startsWith(input)).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sojusz")) {
            List<String> subs = new ArrayList<>(Arrays.asList("akceptuj", "odrzuc", "rozwiaz", "lista"));
            for (Guild g : guildManager.getAll()) {
                subs.add(g.getTag());
            }
            String input = args[1].toLowerCase();
            return subs.stream().filter(s -> s.toLowerCase().startsWith(input)).collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("sojusz")) {
            String sub = args[1].toLowerCase();
            if (sub.equals("akceptuj") || sub.equals("odrzuc") || sub.equals("rozwiaz")) {
                List<String> tags = new ArrayList<>();
                for (Guild g : guildManager.getAll()) tags.add(g.getTag());
                String input = args[2].toLowerCase();
                return tags.stream().filter(t -> t.toLowerCase().startsWith(input)).collect(Collectors.toList());
            }
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("zapros") || sub.equals("wyrzuc") || sub.equals("lider") || sub.equals("zastepca")) {
                return null;
            }
            if (sub.equals("dolacz") || sub.equals("odrzuc") || sub.equals("info")) {
                List<String> tags = new ArrayList<>();
                for (Guild g : guildManager.getAll()) tags.add(g.getTag());
                String input = args[1].toLowerCase();
                return tags.stream().filter(t -> t.toLowerCase().startsWith(input)).collect(Collectors.toList());
            }
        }
        return List.of();
    }
}
