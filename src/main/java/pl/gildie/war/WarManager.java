package pl.gildie.war;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import pl.gildie.GildiePlugin;
import pl.gildie.managers.GuildManager;
import pl.gildie.model.Guild;
import pl.gildie.util.WaypointHook;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Zarządza wojnami, sztandarami, historią i zapisem flat.
 */
public class WarManager {

    private final JavaPlugin plugin;
    private final GuildManager guildManager;
    private final Logger log;
    private final File file;

    private final Map<String, War> activeWars = new ConcurrentHashMap<>();
    private final Map<UUID, War> warsById = new ConcurrentHashMap<>();
    private final List<War> history = new ArrayList<>();
    private final Map<UUID, Location> droppedBanners = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> bannerWaypoints = new ConcurrentHashMap<>();
    private final Set<UUID> bannerCarriers = ConcurrentHashMap.newKeySet();
    /** tag gildii -> ostatni chat alert o ataku na jajo */
    private final Map<String, Long> lastEggChatAlert = new ConcurrentHashMap<>();
    /** debounce: ten sam swing odpala BreakEvent + InteractEvent */
    private final Map<UUID, Long> lastEggHitAt = new ConcurrentHashMap<>();

    private volatile boolean warsDirty;
    private static Attribute SCALE_ATTR;

    public static final int DEFAULT_EGG_HITS = 500;
    public static final long MIN_DURATION_MS = 60L * 60 * 1000;
    public static final long MAX_DURATION_MS = 3L * 60 * 60 * 1000;
    public static final double RANKING_PERCENT = 0.10;
    private static final int HISTORY_CAP = 80;

    static {
        try {
            SCALE_ATTR = (Attribute) Attribute.class.getField("GENERIC_SCALE").get(null);
        } catch (Throwable ignored) {
            SCALE_ATTR = null;
        }
    }

    public WarManager(JavaPlugin plugin, GuildManager guildManager) {
        this.plugin = plugin;
        this.guildManager = guildManager;
        this.log = plugin.getLogger();
        this.file = new File(plugin.getDataFolder(), "wars.yml");
        BannerItem.init(plugin);
        load();
    }

    private String pairKey(String a, String b) {
        a = a.toUpperCase();
        b = b.toUpperCase();
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    public boolean isBannerCarrier(UUID playerId) {
        return playerId != null && bannerCarriers.contains(playerId);
    }

    public Optional<War> getActiveWarBetween(String tag1, String tag2) {
        return Optional.ofNullable(activeWars.get(pairKey(tag1, tag2)));
    }

    public Optional<War> getActiveWarOf(String tag) {
        if (tag == null) return Optional.empty();
        tag = tag.toUpperCase();
        for (War w : activeWars.values()) {
            if (w.isActive() && w.isParticipant(tag)) return Optional.of(w);
        }
        return Optional.empty();
    }

    public Collection<War> getActiveWars() {
        return activeWars.values().stream().filter(War::isActive).collect(Collectors.toList());
    }

    public List<War> getHistory() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    public Optional<War> getWarById(UUID id) {
        if (id == null) return Optional.empty();
        War w = warsById.get(id);
        return Optional.ofNullable(w);
    }

    public boolean declareWar(Player leader, String targetTag, long durationMs) {
        Guild attacker = guildManager.getGuildByPlayer(leader.getUniqueId());
        if (attacker == null) {
            leader.sendMessage("§cNie należysz do żadnej gildii.");
            return false;
        }
        if (!attacker.isLeaderOrDeputy(leader.getUniqueId())) {
            leader.sendMessage("§cTylko lider lub zastępca może wypowiadać wojnę.");
            return false;
        }
        Guild defender = guildManager.getGuild(targetTag);
        if (defender == null) {
            leader.sendMessage("§cNie znaleziono gildii §f" + targetTag);
            return false;
        }
        if (attacker.getTag().equalsIgnoreCase(defender.getTag())) {
            leader.sendMessage("§cNie możesz wypowiedzieć wojny własnej gildii.");
            return false;
        }
        if (attacker.isAlliedWith(defender.getTag())) {
            leader.sendMessage("§cNie możesz wypowiedzieć wojny sojusznikowi.");
            return false;
        }
        if (getActiveWarOf(attacker.getTag()).isPresent()) {
            leader.sendMessage("§cTwoja gildia już prowadzi wojnę.");
            return false;
        }
        if (getActiveWarOf(defender.getTag()).isPresent()) {
            leader.sendMessage("§cTa gildia już prowadzi wojnę.");
            return false;
        }
        if (!TntManager.isTntEnabled()) {
            leader.sendMessage("§cWojnę można wypowiedzieć tylko gdy TNT jest włączone (16:00–21:00).");
            return false;
        }
        durationMs = Math.max(MIN_DURATION_MS, Math.min(MAX_DURATION_MS, durationMs));

        War war = new War(UUID.randomUUID(), attacker.getTag(), defender.getTag(), durationMs);
        activeWars.put(pairKey(attacker.getTag(), defender.getTag()), war);
        warsById.put(war.getId(), war);
        synchronized (history) {
            history.add(war);
        }
        lastEggChatAlert.remove(defender.getTag());
        markDirty();
        save();

        String hours = String.format("%.0f", durationMs / 3600000.0);
        Bukkit.broadcastMessage("§c§l[WOJNA] §e" + attacker.getTag() + " §cwypowiedziała wojnę gildii §e" + defender.getTag()
                + " §cna §f" + hours + "h§c!");
        notifyGuild(defender, "§c§lWOJNA! §e" + attacker.getTag() + " §czaatakowało waszą gildię!",
                "§7Chrońcie jajo (HP " + defender.getEggHp() + "/" + defender.getMaxEggHp() + ")", true, Sound.ENTITY_WITHER_SPAWN);
        notifyGuild(attacker, "§aWypowiedzieliście wojnę §e" + defender.getTag(),
                "§7Zniszczcie jajo wroga (500 HP)", false, Sound.ENTITY_PLAYER_LEVELUP);
        return true;
    }

    /**
     * Uderzenie w jajo – tylko gdy wojna aktywna i TNT włączone.
     * Jajo nigdy nie znika z mapy.
     */
    public boolean handleEggHit(Player player, Guild eggGuild) {
        Guild attackerGuild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (attackerGuild == null) return false;
        if (attackerGuild.getTag().equals(eggGuild.getTag())) return false;

        Optional<War> opt = getActiveWarBetween(attackerGuild.getTag(), eggGuild.getTag());
        if (opt.isEmpty() || !opt.get().isActive()) return false;

        if (!TntManager.isTntEnabled()) {
            player.sendMessage("§cJajo można niszczyć tylko gdy TNT jest włączone (16–21).");
            return true;
        }

        // BreakEvent + LEFT_CLICK_BLOCK w tym samym ticku = 2 HP. Jedno uderzenie = 1 HP.
        long now = System.currentTimeMillis();
        Long prev = lastEggHitAt.put(player.getUniqueId(), now);
        if (prev != null && now - prev < 80L) {
            return true;
        }

        War war = opt.get();
        if (eggGuild.getEggHp() <= 0) {
            if (war.getActiveBannerId() == null) {
                startConquest(player, war, eggGuild, attackerGuild);
            } else {
                player.sendMessage("§cSztandar tej gildii jest już w grze.");
            }
            return true;
        }

        war.getStats(attackerGuild.getTag()).addEggHit();
        boolean broken = eggGuild.damageEgg(1);
        guildManager.markDirty();

        EggHologram holo = hologram();
        if (holo != null) holo.updateHp(eggGuild);

        int hp = eggGuild.getEggHp();
        int max = eggGuild.getMaxEggHp();
        notifyEggAttack(eggGuild, player, attackerGuild, hp, max, broken);

        if (broken) {
            ensureEggBlock(eggGuild);
            startConquest(player, war, eggGuild, attackerGuild);
        }
        markDirty();
        return true;
    }

    /**
     * Powiadomienia gildii przy ataku na jajo:
     * – action bar przy każdym hicie
     * – chat + title co 50 HP (450, 400, …, 50) oraz przy rozbiciu
     */
    private void notifyEggAttack(Guild defenders, Player attacker, Guild attackerGuild,
                                 int hp, int max, boolean broken) {
        String pctBar = hp + "/" + max;
        String action = broken
                ? "§4§lJAJO ROZBITE! §cSztandar zabrany przez §f" + attacker.getName()
                : "§c⚠ Jajo atakowane! §f" + pctBar + " §7(" + attacker.getName() + ")";
        for (UUID id : defenders.getMembers()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            sendActionBar(p, action);
        }

        if (broken) {
            lastEggChatAlert.put(defenders.getTag(), System.currentTimeMillis());
            notifyGuild(defenders,
                    "§4§lJAJO ROZBITE!",
                    "§c" + attacker.getName() + " §7(" + attackerGuild.getTag() + ") §czabrał sztandar!",
                    true, Sound.ENTITY_ENDER_DRAGON_GROWL);
            return;
        }

        int step = Math.max(1, plugin.getConfig().getInt("war.egg-alert-every-hp", 50));
        if (hp > 0 && hp % step == 0) {
            lastEggChatAlert.put(defenders.getTag(), System.currentTimeMillis());
            notifyGuild(defenders,
                    "§c⚠ JAJO ATAKOWANE",
                    "§cHP: §f" + pctBar + " §7– §e" + attacker.getName() + " §8(" + attackerGuild.getTag() + ")",
                    true, Sound.BLOCK_NOTE_BLOCK_BASS);
        }
    }

    private void notifyGuild(Guild guild, String title, String subtitle, boolean useTitle, Sound sound) {
        if (guild == null) return;
        for (UUID id : guild.getMembers()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            p.sendMessage("§c§l[GILDIA] §r" + title + (subtitle == null || subtitle.isEmpty() ? "" : " §8– " + subtitle));
            if (useTitle) {
                p.sendTitle(title, subtitle == null ? "" : subtitle, 5, 40, 10);
            }
            if (sound != null) {
                p.playSound(p.getLocation(), sound, 0.8f, 1.0f);
            }
        }
    }

    private static void sendActionBar(Player p, String msg) {
        try {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
        } catch (Throwable t) {
            p.sendMessage(msg);
        }
    }

    private EggHologram hologram() {
        if (plugin instanceof GildiePlugin gp) return gp.getEggHologram();
        return null;
    }

    private void ensureEggBlock(Guild g) {
        Location loc = g.getEggLocation();
        if (loc == null || loc.getWorld() == null) return;
        String matName = plugin.getConfig().getString("egg.material", "DRAGON_EGG");
        org.bukkit.Material mat;
        try {
            mat = org.bukkit.Material.valueOf(matName);
        } catch (Exception e) {
            mat = org.bukkit.Material.DRAGON_EGG;
        }
        if (loc.getBlock().getType() != mat) {
            loc.getBlock().setType(mat, false);
        }
    }

    public void tickEggRegen() {
        if (TntManager.isTntEnabled()) return;
        int perTick = plugin.getConfig().getInt("war.egg-regen-per-tick", 5);
        EggHologram holo = hologram();
        boolean any = false;
        for (Guild g : guildManager.getAll()) {
            if (!g.hasEgg()) continue;
            if (g.regenEgg(perTick)) {
                any = true;
                if (holo != null) holo.updateHp(g);
            }
        }
        if (any) guildManager.markDirty();
    }

    private void startConquest(Player player, War war, Guild conquered, Guild conqueror) {
        if (war.getActiveBannerId() != null) {
            player.sendMessage("§cSztandar tej gildii jest już w grze.");
            return;
        }
        UUID bannerId = UUID.randomUUID();
        ItemStack banner = BannerItem.create(war.getId(), bannerId, conquered.getTag(), conqueror.getTag());

        ItemStack oldHelmet = player.getInventory().getHelmet();
        if (oldHelmet != null && oldHelmet.getType() != org.bukkit.Material.AIR) {
            player.getWorld().dropItemNaturally(player.getLocation(), oldHelmet);
        }
        player.getInventory().setHelmet(banner);

        war.setActiveBannerId(bannerId);
        war.setBannerCarrierGuild(conqueror.getTag());
        war.setBannerCarrierPlayer(player.getUniqueId());
        bannerCarriers.add(player.getUniqueId());

        setScale(player, 1.6);

        Location top = findHighestBlock(conquered);
        if (top != null) {
            player.teleport(top.add(0.5, 1, 0.5));
        }

        UUID wpId = WaypointHook.addGlobalWaypoint("Sztandar " + conquered.getTag(), player.getLocation(), 0xFF5555);
        if (wpId != null) {
            bannerWaypoints.put(bannerId, wpId);
        }

        Bukkit.broadcastMessage("§c§l[WOJNA] §e" + player.getName() + " §cz gildii §e" + conqueror.getTag()
                + " §cprzejął sztandar gildii §e" + conquered.getTag() + "§c! Musi zanieść go do swojej bazy!");
        notifyGuild(conquered, "§4SZTANDAR SKRADZIONY!",
                "§cZabijcie §f" + player.getName() + " §ci odzyskajcie sztandar!",
                true, Sound.ENTITY_WITHER_SPAWN);
        markDirty();
        save();
    }

    private Location findHighestBlock(Guild g) {
        org.bukkit.World w = Bukkit.getWorld(g.getWorldName());
        if (w == null) return null;
        int x = (int) Math.floor(g.getX());
        int z = (int) Math.floor(g.getZ());
        int y = w.getHighestBlockYAt(x, z);
        return new Location(w, x, y, z);
    }

    public void tryCompleteConquest(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        if (!BannerItem.isBanner(helmet)) {
            bannerCarriers.remove(player.getUniqueId());
            return;
        }

        UUID warId = BannerItem.getWarId(helmet);
        UUID bannerId = BannerItem.getBannerId(helmet);
        String from = BannerItem.getFromGuild(helmet);

        Guild own = guildManager.getGuildByPlayer(player.getUniqueId());
        if (own == null) return;
        if (!own.isInTerritory(player.getLocation())) return;

        Optional<War> opt = getWarById(warId);
        if (opt.isEmpty() || !opt.get().isActive()) return;
        War war = opt.get();
        if (!war.isParticipant(own.getTag())) return;
        if (war.getActiveBannerId() != null && bannerId != null && !war.getActiveBannerId().equals(bannerId)) {
            return; // stary / zduplikowany sztandar
        }

        // Odzyskanie przez właścicieli jaja – wojna trwa, HP wraca do 25%
        if (from != null && from.equalsIgnoreCase(own.getTag())) {
            recoverBanner(player, war, bannerId, own);
            return;
        }

        war.setState(War.State.ENDED_CONQUEST);
        war.setEndTime(System.currentTimeMillis());
        stripBanner(player, bannerId);
        war.clearBanner();
        activeWars.remove(pairKey(war.getAttackerTag(), war.getDefenderTag()));

        applyWinRewards(war, own.getTag(), from);

        Bukkit.broadcastMessage("§a§l[WOJNA] §e" + own.getTag() + " §apodbiła gildię §e" + from
                + " §a! Wojna zakończona podbiciem przez §f" + player.getName());
        markDirty();
        save();
    }

    private void recoverBanner(Player player, War war, UUID bannerId, Guild owners) {
        stripBanner(player, bannerId);
        war.clearBanner();
        int restored = Math.max(1, owners.getMaxEggHp() / 4);
        owners.setEggHp(restored);
        guildManager.markDirty();
        EggHologram holo = hologram();
        if (holo != null) holo.updateHp(owners);
        ensureEggBlock(owners);

        Bukkit.broadcastMessage("§a§l[WOJNA] §e" + owners.getTag() + " §aodzyskała sztandar! Wojna trwa.");
        notifyGuild(owners, "§aSztandar odzyskany!", "§7Jajo: " + restored + "/" + owners.getMaxEggHp() + " HP", true, Sound.ENTITY_PLAYER_LEVELUP);
        markDirty();
        save();
    }

    private void stripBanner(Player player, UUID bannerId) {
        player.getInventory().setHelmet(null);
        setScale(player, 1.0);
        bannerCarriers.remove(player.getUniqueId());
        UUID wp = bannerWaypoints.remove(bannerId);
        if (wp != null) WaypointHook.removeWaypoint(wp);
        droppedBanners.remove(bannerId);
    }

    private void applyWinRewards(War war, String winnerTag, String loserTag) {
        Guild winner = guildManager.getGuild(winnerTag);
        Guild loser = guildManager.getGuild(loserTag);
        if (winner == null || loser == null) return;

        war.getStats(winnerTag).setRankingGained(0);

        for (UUID uuid : winner.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage("§aOtrzymujesz nagrodę za wygraną wojnę (placeholder – case).");
            }
        }
    }

    public void handleBannerDeath(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        if (!BannerItem.isBanner(helmet)) return;

        UUID bannerId = BannerItem.getBannerId(helmet);
        UUID warId = BannerItem.getWarId(helmet);

        player.getInventory().setHelmet(null);
        setScale(player, 1.0);
        bannerCarriers.remove(player.getUniqueId());

        Location dropLoc = player.getLocation();
        Item dropped = player.getWorld().dropItemNaturally(dropLoc, helmet);
        dropped.setPickupDelay(20);

        droppedBanners.put(bannerId, dropLoc.clone());

        UUID oldWp = bannerWaypoints.remove(bannerId);
        if (oldWp != null) WaypointHook.removeWaypoint(oldWp);
        UUID newWp = WaypointHook.addGlobalWaypoint("Sztandar (upadł)", dropLoc, 0xFF5555);
        if (newWp != null) bannerWaypoints.put(bannerId, newWp);

        getWarById(warId).ifPresent(w -> w.setBannerCarrierPlayer(null));

        Bukkit.broadcastMessage("§c§l[WOJNA] §eSztandar upadł! §cKtokolwiek go podniesie, może go zanieść.");
        markDirty();
    }

    public void handleBannerPickup(Player player, ItemStack banner) {
        if (!BannerItem.isBanner(banner)) return;
        UUID bannerId = BannerItem.getBannerId(banner);
        UUID warId = BannerItem.getWarId(banner);

        Guild g = guildManager.getGuildByPlayer(player.getUniqueId());
        if (g == null) {
            player.sendMessage("§cTylko członek gildii może podnieść sztandar.");
            player.getWorld().dropItemNaturally(player.getLocation(), banner);
            return;
        }
        Optional<War> wopt = getWarById(warId);
        if (wopt.isEmpty() || !wopt.get().isActive() || !wopt.get().isParticipant(g.getTag())) {
            player.sendMessage("§cTen sztandar nie należy do Twojej wojny.");
            return;
        }

        ItemStack oldHelmet = player.getInventory().getHelmet();
        if (oldHelmet != null && oldHelmet.getType() != org.bukkit.Material.AIR && !BannerItem.isBanner(oldHelmet)) {
            player.getWorld().dropItemNaturally(player.getLocation(), oldHelmet);
        }
        player.getInventory().setHelmet(banner.clone());

        getWarById(warId).ifPresent(war -> {
            war.setBannerCarrierPlayer(player.getUniqueId());
            war.setBannerCarrierGuild(g.getTag());
        });
        bannerCarriers.add(player.getUniqueId());

        droppedBanners.remove(bannerId);
        UUID oldWp = bannerWaypoints.remove(bannerId);
        if (oldWp != null) WaypointHook.removeWaypoint(oldWp);
        UUID newWp = WaypointHook.addGlobalWaypoint("Sztandar " + BannerItem.getFromGuild(banner), player.getLocation(), 0xFF5555);
        if (newWp != null) bannerWaypoints.put(bannerId, newWp);

        setScale(player, 1.6);
        player.sendMessage("§aPodniosłeś sztandar! Zanieś go do terenu swojej gildii.");
        markDirty();
    }

    public void tick() {
        List<War> toEnd = new ArrayList<>();
        for (War w : activeWars.values()) {
            if (w.isExpired()) toEnd.add(w);
        }
        for (War w : toEnd) {
            endByKillsOrTimeout(w);
        }
    }

    /** Zrzut YAML – wywoływany co kilka sekund, nie przy każdym hicie. */
    public void flush() {
        if (warsDirty) save();
        guildManager.saveIfDirty();
    }

    private void markDirty() {
        warsDirty = true;
    }

    private void endByKillsOrTimeout(War war) {
        WarStats a = war.getStats(war.getAttackerTag());
        WarStats d = war.getStats(war.getDefenderTag());
        String winner;
        if (a.getKills() > d.getKills()) {
            winner = war.getAttackerTag();
            war.setState(War.State.ENDED_KILLS);
        } else if (d.getKills() > a.getKills()) {
            winner = war.getDefenderTag();
            war.setState(War.State.ENDED_KILLS);
        } else {
            winner = null;
            war.setState(War.State.ENDED_TIMEOUT);
        }
        war.setEndTime(System.currentTimeMillis());
        activeWars.remove(pairKey(war.getAttackerTag(), war.getDefenderTag()));

        clearActiveBannerWorld(war);

        if (winner != null) {
            String loser = war.getOpponent(winner);
            applyWinRewards(war, winner, loser);
            Bukkit.broadcastMessage("§e§l[WOJNA] §fWojna " + war.getAttackerTag() + " vs " + war.getDefenderTag()
                    + " §ezakończona. Zwycięzca (więcej zabójstw): §a" + winner);
        } else {
            Bukkit.broadcastMessage("§e§l[WOJNA] §fWojna " + war.getAttackerTag() + " vs " + war.getDefenderTag()
                    + " §ezakończona remisem (timeout).");
        }
        markDirty();
        save();
    }

    private void clearActiveBannerWorld(War war) {
        UUID bannerId = war.getActiveBannerId();
        UUID carrierId = war.getBannerCarrierPlayer();
        if (carrierId != null) {
            Player p = Bukkit.getPlayer(carrierId);
            if (p != null) {
                ItemStack helm = p.getInventory().getHelmet();
                if (BannerItem.isBanner(helm)) p.getInventory().setHelmet(null);
                setScale(p, 1.0);
            }
            bannerCarriers.remove(carrierId);
        }
        // wszyscy online – na wypadek rozjechanego carrierId
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack helm = p.getInventory().getHelmet();
            if (BannerItem.isBanner(helm)) {
                UUID wid = BannerItem.getWarId(helm);
                if (war.getId().equals(wid)) {
                    p.getInventory().setHelmet(null);
                    setScale(p, 1.0);
                    bannerCarriers.remove(p.getUniqueId());
                }
            }
        }
        if (bannerId != null) {
            UUID wp = bannerWaypoints.remove(bannerId);
            if (wp != null) WaypointHook.removeWaypoint(wp);
            droppedBanners.remove(bannerId);
            removeDroppedBannerItems(war.getId());
        }
        war.clearBanner();
    }

    private void removeDroppedBannerItems(UUID warId) {
        if (warId == null) return;
        for (World w : Bukkit.getWorlds()) {
            for (Item item : w.getEntitiesByClass(Item.class)) {
                if (BannerItem.isBanner(item.getItemStack())) {
                    UUID id = BannerItem.getWarId(item.getItemStack());
                    if (warId.equals(id)) item.remove();
                }
            }
        }
    }

    private static void setScale(Player player, double value) {
        if (SCALE_ATTR == null || player == null) return;
        try {
            AttributeInstance inst = player.getAttribute(SCALE_ATTR);
            if (inst != null) inst.setBaseValue(value);
        } catch (Throwable ignored) {
        }
    }

    public void save() {
        FileConfiguration config = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        List<War> snapshot;
        synchronized (history) {
            int extra = history.size() - HISTORY_CAP;
            if (extra > 0) {
                history.subList(0, extra).removeIf(w -> w.getState() != War.State.ACTIVE);
            }
            snapshot = new ArrayList<>(history);
        }
        for (War w : snapshot) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", w.getId().toString());
            m.put("attacker", w.getAttackerTag());
            m.put("defender", w.getDefenderTag());
            m.put("start", w.getStartTime());
            m.put("duration", w.getDurationMs());
            m.put("end", w.getEndTime());
            m.put("state", w.getState().name());
            if (w.getActiveBannerId() != null) m.put("bannerId", w.getActiveBannerId().toString());
            if (w.getBannerCarrierPlayer() != null) m.put("bannerPlayer", w.getBannerCarrierPlayer().toString());
            if (w.getBannerCarrierGuild() != null) m.put("bannerGuild", w.getBannerCarrierGuild());
            Map<String, Object> st = new LinkedHashMap<>();
            for (Map.Entry<String, WarStats> e : w.getAllStats().entrySet()) {
                WarStats s = e.getValue();
                Map<String, Object> sm = new LinkedHashMap<>();
                sm.put("kills", s.getKills());
                sm.put("deaths", s.getDeaths());
                sm.put("ranking", s.getRankingGained());
                sm.put("koxy", s.getKoxyEaten());
                sm.put("refills", s.getRefillsUsed());
                sm.put("pearls", s.getPearlsUsed());
                sm.put("tnt", s.getTntFired());
                sm.put("eggHits", s.getEggHits());
                st.put(e.getKey(), sm);
            }
            m.put("stats", st);
            list.add(m);
        }
        config.set("wars", list);
        try {
            config.save(file);
            warsDirty = false;
        } catch (IOException e) {
            log.severe("Nie można zapisać wars.yml: " + e.getMessage());
        }
    }

    public void load() {
        if (!file.exists()) return;
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<?> raw = config.getList("wars");
        if (raw == null) return;
        synchronized (history) {
            history.clear();
        }
        activeWars.clear();
        warsById.clear();
        for (Object o : raw) {
            if (!(o instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) o;
            try {
                UUID id = UUID.fromString(String.valueOf(m.get("id")));
                String att = String.valueOf(m.get("attacker"));
                String def = String.valueOf(m.get("defender"));
                long start = ((Number) m.get("start")).longValue();
                long dur = ((Number) m.get("duration")).longValue();
                long end = ((Number) m.get("end")).longValue();
                War.State state = War.State.valueOf(String.valueOf(m.get("state")));
                Map<String, WarStats> statsMap = new HashMap<>();
                Object stObj = m.get("stats");
                if (stObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> st = (Map<String, Object>) stObj;
                    for (Map.Entry<String, Object> e : st.entrySet()) {
                        if (e.getValue() instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> sm = (Map<String, Object>) e.getValue();
                            WarStats ws = new WarStats(
                                    ((Number) sm.getOrDefault("kills", 0)).intValue(),
                                    ((Number) sm.getOrDefault("deaths", 0)).intValue(),
                                    ((Number) sm.getOrDefault("ranking", 0)).intValue(),
                                    ((Number) sm.getOrDefault("koxy", 0)).intValue(),
                                    ((Number) sm.getOrDefault("refills", 0)).intValue(),
                                    ((Number) sm.getOrDefault("pearls", 0)).intValue(),
                                    ((Number) sm.getOrDefault("tnt", 0)).intValue(),
                                    ((Number) sm.getOrDefault("eggHits", 0)).intValue()
                            );
                            statsMap.put(e.getKey().toUpperCase(), ws);
                        }
                    }
                }
                War war = new War(id, att, def, start, dur, end, state, statsMap);
                Object bid = m.get("bannerId");
                if (bid != null && !String.valueOf(bid).isBlank() && !"null".equals(String.valueOf(bid))) {
                    try { war.setActiveBannerId(UUID.fromString(String.valueOf(bid))); } catch (Exception ignored) {}
                }
                Object bp = m.get("bannerPlayer");
                if (bp != null && !String.valueOf(bp).isBlank() && !"null".equals(String.valueOf(bp))) {
                    try { war.setBannerCarrierPlayer(UUID.fromString(String.valueOf(bp))); } catch (Exception ignored) {}
                }
                Object bg = m.get("bannerGuild");
                if (bg != null) war.setBannerCarrierGuild(String.valueOf(bg));
                synchronized (history) {
                    history.add(war);
                }
                warsById.put(id, war);
                if (state == War.State.ACTIVE) {
                    activeWars.put(pairKey(att, def), war);
                }
            } catch (Exception ex) {
                log.warning("Błąd ładowania wojny: " + ex.getMessage());
            }
        }
        log.info("Załadowano wojny (aktywnych: " + activeWars.size() + ")");
        // Wojny które skończyły się gdy serwer był offline
        List<War> expired = new ArrayList<>();
        for (War w : activeWars.values()) {
            if (w.isExpired()) expired.add(w);
        }
        for (War w : expired) {
            endByKillsOrTimeout(w);
        }
    }

    /**
     * Po restarcie / join: gracz ze sztandarem na hełmie znowu jest nosicielem.
     */
    public void rebindBanner(Player player) {
        if (player == null) return;
        ItemStack helmet = player.getInventory().getHelmet();
        if (!BannerItem.isBanner(helmet)) {
            bannerCarriers.remove(player.getUniqueId());
            return;
        }
        UUID warId = BannerItem.getWarId(helmet);
        UUID bannerId = BannerItem.getBannerId(helmet);
        Optional<War> opt = getWarById(warId);
        if (opt.isEmpty() || !opt.get().isActive()) {
            player.getInventory().setHelmet(null);
            setScale(player, 1.0);
            bannerCarriers.remove(player.getUniqueId());
            player.sendMessage("§cSztandar był z nieaktywnej wojny i został usunięty.");
            return;
        }
        War war = opt.get();
        Guild g = guildManager.getGuildByPlayer(player.getUniqueId());
        if (g == null || !war.isParticipant(g.getTag())) {
            player.getInventory().setHelmet(null);
            setScale(player, 1.0);
            return;
        }
        war.setActiveBannerId(bannerId);
        war.setBannerCarrierPlayer(player.getUniqueId());
        war.setBannerCarrierGuild(g.getTag());
        bannerCarriers.add(player.getUniqueId());
        setScale(player, 1.6);
        UUID oldWp = bannerWaypoints.remove(bannerId);
        if (oldWp != null) WaypointHook.removeWaypoint(oldWp);
        UUID wp = WaypointHook.addGlobalWaypoint("Sztandar " + BannerItem.getFromGuild(helmet), player.getLocation(), 0xFF5555);
        if (wp != null) bannerWaypoints.put(bannerId, wp);
        markDirty();
    }
}
