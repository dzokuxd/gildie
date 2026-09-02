package pl.gildie.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.gildie.managers.GuildManager;
import pl.gildie.model.Guild;
import pl.gildie.war.TntManager;
import pl.gildie.war.War;
import pl.gildie.war.WarManager;
import pl.gildie.war.WarStats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /wojna – otwiera GUI: wyzwij, statystyki, historia.
 */
public class WojnaCommand implements CommandExecutor, TabCompleter {

    private final GuildManager guildManager;
    private final WarManager warManager;

    public WojnaCommand(GuildManager guildManager, WarManager warManager) {
        this.guildManager = guildManager;
        this.warManager = warManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Tylko gracz.");
            return true;
        }

        if (args.length == 0) {
            openMainGui(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "wyzwij", "challenge" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUżycie: /wojna wyzwij <tag> [1-3]");
                    return true;
                }
                String tag = args[1];
                long hours = 1;
                if (args.length >= 3) {
                    try {
                        hours = Long.parseLong(args[2]);
                    } catch (NumberFormatException ignored) {}
                }
                hours = Math.max(1, Math.min(3, hours));
                warManager.declareWar(player, tag, hours * 3600_000L);
            }
            case "stats", "statystyki" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUżycie: /wojna stats <tag>");
                    return true;
                }
                openStatsGui(player, args[1]);
            }
            case "historia", "history" -> openHistoryGui(player);
            case "tnt" -> {
                if (player.hasPermission("gildie.admin")) {
                    boolean now = !TntManager.isTntEnabled();
                    TntManager.setEnabled(now);
                    player.sendMessage("§eTNT force: " + (now ? "§awłączone" : "§cwyłączone"));
                }
            }
            default -> openMainGui(player);
        }
        return true;
    }

    private void openMainGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§c§lWojny gildii");

        inv.setItem(11, named(Material.IRON_SWORD, "§cWyzwij gildię",
                "§7Kliknij aby otworzyć listę",
                "§7lub użyj: §f/wojna wyzwij <tag> [1-3h]",
                "",
                "§eTNT musi być włączone (16-21)"));

        inv.setItem(13, named(Material.BOOK, "§eStatystyki wojny",
                "§7/wojna stats <tag>",
                "§7Pokazuje zabójstwa, zgony,",
                "§7koxy, perły, TNT, uderzenia w jajo"));

        inv.setItem(15, named(Material.CHEST, "§6Historia wojen",
                "§7Wszystkie zakończone i aktywne wojny"));

        // Status TNT
        boolean tnt = TntManager.isTntEnabled();
        inv.setItem(22, named(tnt ? Material.TNT : Material.BARRIER,
                tnt ? "§aTNT WŁĄCZONE" : "§cTNT WYŁĄCZONE",
                "§7Godziny: 16:00–21:00"));

        player.openInventory(inv);
    }

    private void openStatsGui(Player player, String tag) {
        Guild own = guildManager.getGuildByPlayer(player.getUniqueId());
        if (own == null) {
            player.sendMessage("§cNie masz gildii.");
            return;
        }
        var opt = warManager.getActiveWarBetween(own.getTag(), tag);
        if (opt.isEmpty()) {
            player.sendMessage("§cBrak aktywnej wojny z §f" + tag);
            return;
        }
        War war = opt.get();
        Inventory inv = Bukkit.createInventory(null, 27, "§cWojna vs " + tag.toUpperCase());

        WarStats ownS = war.getStats(own.getTag());
        WarStats enemyS = war.getStats(tag);

        inv.setItem(11, named(Material.DIAMOND_SWORD, "§aTwoja gildia §f" + own.getTag(),
                "§7Zabójstwa: §f" + ownS.getKills(),
                "§7Zgony: §f" + ownS.getDeaths(),
                "§7Ranking: §f" + ownS.getRankingGained() + " (placeholder)",
                "§7Koxy: §f" + ownS.getKoxyEaten(),
                "§7Refy: §f" + ownS.getRefillsUsed(),
                "§7Perły: §f" + ownS.getPearlsUsed(),
                "§7TNT: §f" + ownS.getTntFired(),
                "§7Uderzenia w jajo: §f" + ownS.getEggHits()));

        inv.setItem(15, named(Material.IRON_SWORD, "§cPrzeciwnik §f" + tag.toUpperCase(),
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
                "§f" + (rem / 3600000) + "h " + ((rem % 3600000) / 60000) + "min"));

        player.openInventory(inv);
    }

    private void openHistoryGui(Player player) {
        List<War> list = warManager.getHistory();
        int size = Math.min(54, ((list.size() / 9) + 1) * 9);
        if (size < 9) size = 9;
        Inventory inv = Bukkit.createInventory(null, size, "§6Historia wojen");

        int i = 0;
        for (War w : list) {
            if (i >= size) break;
            Material mat = switch (w.getState()) {
                case ACTIVE -> Material.REDSTONE_BLOCK;
                case ENDED_CONQUEST -> Material.GOLD_BLOCK;
                case ENDED_KILLS -> Material.IRON_BLOCK;
                default -> Material.COAL_BLOCK;
            };
            inv.setItem(i++, named(mat,
                    "§e" + w.getAttackerTag() + " §7vs §e" + w.getDefenderTag(),
                    "§7Stan: §f" + w.getState().name(),
                    "§7Start: §f" + new java.util.Date(w.getStartTime()),
                    "§7Zabójstwa A/D: §f" + w.getStats(w.getAttackerTag()).getKills()
                            + " / " + w.getStats(w.getDefenderTag()).getKills()));
        }
        player.openInventory(inv);
    }

    private ItemStack named(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("wyzwij", "stats", "historia").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("wyzwij") || args[0].equalsIgnoreCase("stats"))) {
            return guildManager.getAll().stream()
                    .map(Guild::getTag)
                    .filter(t -> t.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("wyzwij")) {
            return Arrays.asList("1", "2", "3");
        }
        return new ArrayList<>();
    }
}
