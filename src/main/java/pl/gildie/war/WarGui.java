package pl.gildie.war;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.gildie.managers.GuildManager;
import pl.gildie.model.Guild;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Całe GUI systemu wojen – otwierane przez /g wojna.
 * Żadnych komend tekstowych do wyzwania / stats / historii.
 */
public final class WarGui {

    public static final String TITLE_MAIN = "§c§lWojny gildii";
    public static final String TITLE_CHALLENGE = "§c§lWyzwij gildię";
    public static final String TITLE_DURATION = "§e§lCzas trwania wojny";
    public static final String TITLE_STATS = "§cWojna vs ";
    public static final String TITLE_HISTORY = "§6Historia wojen";
    public static final String TITLE_PICK_STATS = "§e§lWybierz gildię (statystyki)";

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    private WarGui() {}

    // -------------------------------------------------------------------------
    // Główne menu
    // -------------------------------------------------------------------------

    public static void openMain(Player player, GuildManager guildManager, WarManager warManager) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_MAIN);

        inv.setItem(11, named(Material.IRON_SWORD, "§c§lWyzwij gildię",
                "§7Kliknij, aby wybrać gildię",
                "§7i czas trwania wojny (1–3 h).",
                "",
                "§eTNT musi być włączone (16:00–21:00)"));

        inv.setItem(13, named(Material.BOOK, "§e§lStatystyki wojny",
                "§7Wybierz gildię, z którą",
                "§7masz aktywną wojnę.",
                "§7Zabójstwa, zgony, koxy,",
                "§7perły, TNT, uderzenia w jajo."));

        inv.setItem(15, named(Material.CHEST, "§6§lHistoria wojen",
                "§7Wszystkie zakończone",
                "§7i aktywne wojny."));

        boolean tnt = TntManager.isTntEnabled();
        inv.setItem(22, named(tnt ? Material.TNT : Material.BARRIER,
                tnt ? "§a§lTNT WŁĄCZONE" : "§c§lTNT WYŁĄCZONE",
                "§7Godziny: §f16:00–21:00",
                "§7Poza tym TNT nie niszczy bloków."));

        // Aktywna wojna własnej gildii
        Guild own = guildManager.getGuildByPlayer(player.getUniqueId());
        if (own != null) {
            warManager.getActiveWarOf(own.getTag()).ifPresent(war -> {
                String opp = war.getOpponent(own.getTag());
                long rem = war.getRemainingMs();
                inv.setItem(4, named(Material.REDSTONE_BLOCK, "§c§lTwoja aktywna wojna",
                        "§7Przeciwnik: §e" + opp,
                        "§7Pozostało: §f" + formatDuration(rem),
                        "",
                        "§eKliknij → statystyki"));
            });
        }

        player.openInventory(inv);
    }

    // -------------------------------------------------------------------------
    // Lista gildii do wyzwania
    // -------------------------------------------------------------------------

    public static void openChallengeList(Player player, GuildManager guildManager, WarManager warManager) {
        Guild own = guildManager.getGuildByPlayer(player.getUniqueId());
        if (own == null) {
            player.sendMessage("§cNie należysz do żadnej gildii.");
            return;
        }
        if (!own.isLeaderOrDeputy(player.getUniqueId())) {
            player.sendMessage("§cTylko lider lub zastępca może wypowiadać wojnę.");
            return;
        }
        if (!TntManager.isTntEnabled()) {
            player.sendMessage("§cWojnę można wypowiedzieć tylko gdy TNT jest włączone (16:00–21:00).");
            return;
        }
        if (warManager.getActiveWarOf(own.getTag()).isPresent()) {
            player.sendMessage("§cTwoja gildia już prowadzi wojnę.");
            return;
        }

        List<Guild> candidates = guildManager.getAll().stream()
                .filter(g -> !g.getTag().equalsIgnoreCase(own.getTag()))
                .filter(g -> !own.isAlliedWith(g.getTag()))
                .filter(g -> warManager.getActiveWarOf(g.getTag()).isEmpty())
                .collect(Collectors.toList());

        int size = Math.min(54, Math.max(9, ((candidates.size() + 8) / 9) * 9));
        Inventory inv = Bukkit.createInventory(null, size, TITLE_CHALLENGE);

        int i = 0;
        for (Guild g : candidates) {
            if (i >= size) break;
            inv.setItem(i++, named(Material.WHITE_BANNER, "§e" + g.getTag(),
                    "§7Członków: §f" + g.getMembers().size(),
                    "§7Kliknij, aby wybrać czas wojny."));
        }
        if (candidates.isEmpty()) {
            inv.setItem(4, named(Material.BARRIER, "§cBrak dostępnych gildii",
                    "§7Wszystkie gildie są w sojuszu,",
                    "§7w wojnie albo nie istnieją."));
        }
        player.openInventory(inv);
    }

    // -------------------------------------------------------------------------
    // Wybór czasu (1h / 2h / 3h) – tag w nazwie itemu
    // -------------------------------------------------------------------------

    public static void openDurationPicker(Player player, String targetTag) {
        Inventory inv = Bukkit.createInventory(null, 9, TITLE_DURATION);
        inv.setItem(2, named(Material.CLOCK, "§a1 godzina",
                "§7Wyzwij §e" + targetTag + " §7na §f1h",
                "§8TAG:" + targetTag.toUpperCase() + ":1"));
        inv.setItem(4, named(Material.CLOCK, "§e2 godziny",
                "§7Wyzwij §e" + targetTag + " §7na §f2h",
                "§8TAG:" + targetTag.toUpperCase() + ":2"));
        inv.setItem(6, named(Material.CLOCK, "§c3 godziny",
                "§7Wyzwij §e" + targetTag + " §7na §f3h",
                "§8TAG:" + targetTag.toUpperCase() + ":3"));
        player.openInventory(inv);
    }

    // -------------------------------------------------------------------------
    // Lista gildii do podglądu statystyk (tylko aktywne wojny gracza)
    // -------------------------------------------------------------------------

    public static void openStatsPicker(Player player, GuildManager guildManager, WarManager warManager) {
        Guild own = guildManager.getGuildByPlayer(player.getUniqueId());
        if (own == null) {
            player.sendMessage("§cNie masz gildii.");
            return;
        }
        var opt = warManager.getActiveWarOf(own.getTag());
        if (opt.isEmpty()) {
            player.sendMessage("§cTwoja gildia nie prowadzi żadnej wojny.");
            return;
        }
        // Jedna aktywna wojna – od razu stats
        openStats(player, own, opt.get());
    }

    public static void openStats(Player player, Guild own, War war) {
        String tag = war.getOpponent(own.getTag());
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_STATS + tag);

        WarStats ownS = war.getStats(own.getTag());
        WarStats enemyS = war.getStats(tag);

        inv.setItem(11, named(Material.DIAMOND_SWORD, "§aTwoja gildia §f" + own.getTag(),
                "§7Zabójstwa: §f" + ownS.getKills(),
                "§7Zgony: §f" + ownS.getDeaths(),
                "§7Ranking: §f" + ownS.getRankingGained() + " §8(placeholder)",
                "§7Koxy: §f" + ownS.getKoxyEaten(),
                "§7Refy: §f" + ownS.getRefillsUsed(),
                "§7Perły: §f" + ownS.getPearlsUsed(),
                "§7TNT: §f" + ownS.getTntFired(),
                "§7Uderzenia w jajo: §f" + ownS.getEggHits()));

        inv.setItem(15, named(Material.IRON_SWORD, "§cPrzeciwnik §f" + tag,
                "§7Zabójstwa: §f" + enemyS.getKills(),
                "§7Zgony: §f" + enemyS.getDeaths(),
                "§7Ranking: §f" + enemyS.getRankingGained(),
                "§7Koxy: §f" + enemyS.getKoxyEaten(),
                "§7Refy: §f" + enemyS.getRefillsUsed(),
                "§7Perły: §f" + enemyS.getPearlsUsed(),
                "§7TNT: §f" + enemyS.getTntFired(),
                "§7Uderzenia w jajo: §f" + enemyS.getEggHits()));

        long rem = war.getRemainingMs();
        inv.setItem(13, named(Material.CLOCK, "§ePozostały czas",
                "§f" + formatDuration(rem),
                "§7Stan: §aAktywna"));

        inv.setItem(22, named(Material.ARROW, "§7« Powrót", "§8Kliknij, aby wrócić"));

        player.openInventory(inv);
    }

    // -------------------------------------------------------------------------
    // Historia – po polsku + pełne staty
    // -------------------------------------------------------------------------

    public static void openHistory(Player player, WarManager warManager) {
        List<War> list = warManager.getHistory();
        // najnowsze na górze
        List<War> sorted = new ArrayList<>(list);
        sorted.sort((a, b) -> Long.compare(b.getStartTime(), a.getStartTime()));

        int size = Math.min(54, Math.max(9, ((sorted.size() + 8) / 9) * 9));
        Inventory inv = Bukkit.createInventory(null, size, TITLE_HISTORY);

        int i = 0;
        for (War w : sorted) {
            if (i >= size) break;
            Material mat = switch (w.getState()) {
                case ACTIVE -> Material.REDSTONE_BLOCK;
                case ENDED_CONQUEST -> Material.GOLD_BLOCK;
                case ENDED_KILLS -> Material.IRON_BLOCK;
                default -> Material.COAL_BLOCK;
            };
            String stan = switch (w.getState()) {
                case ACTIVE -> "§aAktywna";
                case ENDED_CONQUEST -> "§6Zakończona (podbicie)";
                case ENDED_KILLS -> "§eZakończona (zabójstwa)";
                case ENDED_TIMEOUT -> "§7Zakończona (czas)";
            };
            WarStats a = w.getStats(w.getAttackerTag());
            WarStats d = w.getStats(w.getDefenderTag());

            List<String> lore = new ArrayList<>();
            lore.add("§7Stan: " + stan);
            lore.add("§7Start: §f" + DATE_FMT.format(new Date(w.getStartTime())));
            if (w.isActive()) {
                lore.add("§7Pozostało: §f" + formatDuration(w.getRemainingMs()));
            } else if (w.getEndTime() > 0) {
                lore.add("§7Koniec: §f" + DATE_FMT.format(new Date(w.getEndTime())));
            }
            lore.add("");
            lore.add("§7Zabójstwa: §a" + a.getKills() + " §7/ §c" + d.getKills());
            lore.add("§7Zgony: §a" + a.getDeaths() + " §7/ §c" + d.getDeaths());
            lore.add("§7Koxy: §a" + a.getKoxyEaten() + " §7/ §c" + d.getKoxyEaten());
            lore.add("§7Refy: §a" + a.getRefillsUsed() + " §7/ §c" + d.getRefillsUsed());
            lore.add("§7Perły: §a" + a.getPearlsUsed() + " §7/ §c" + d.getPearlsUsed());
            lore.add("§7TNT: §a" + a.getTntFired() + " §7/ §c" + d.getTntFired());
            lore.add("§7Uderzenia w jajo: §a" + a.getEggHits() + " §7/ §c" + d.getEggHits());

            inv.setItem(i++, named(mat,
                    "§e" + w.getAttackerTag() + " §7vs §e" + w.getDefenderTag(),
                    lore.toArray(new String[0])));
        }
        if (sorted.isEmpty()) {
            inv.setItem(4, named(Material.BARRIER, "§7Brak historii wojen"));
        }
        player.openInventory(inv);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    public static String formatDuration(long ms) {
        if (ms <= 0) return "0 min";
        long h = ms / 3_600_000;
        long m = (ms % 3_600_000) / 60_000;
        if (h > 0) return h + "h " + m + "min";
        return m + " min";
    }

    private static ItemStack named(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
