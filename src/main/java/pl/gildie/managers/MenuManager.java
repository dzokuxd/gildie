package pl.gildie.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pl.gildie.model.ItemBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MenuManager {

    private static final Map<UUID, Integer> digY = new HashMap<>();   // Y przy kopaniu
    private static final Map<UUID, Integer> digR = new HashMap<>();   // Radius przy kopaniu

    private static final Map<UUID, Integer> wallR = new HashMap<>();  // Radius przy ścianach
    private static final Map<UUID, Integer> wallH = new HashMap<>();  // Wysokość przy ścianach

    // ====================== GŁÓWNE MENU ======================
    public static void openMainMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§6§lGIGA FOS MENU");

        inv.setItem(13, ItemBuilder.create(Material.DIAMOND_SHOVEL, "§c§lKOPANIE"));

        inv.setItem(11, ItemBuilder.create(Material.SAND,
                "§e§lŚCIANY Z PIASKU",
                "§7Kliknij aby ustawić i zbudować"));

        inv.setItem(15, ItemBuilder.create(Material.OBSIDIAN,
                "§f§lŚCIANY Z OBSYDIANU",
                "§7Kliknij aby ustawić i zbudować"));

        fill(inv);
        p.openInventory(inv);
    }

    // ====================== MENU KOPANIA ======================
    public static void openSettingsMenu(Player p) {
        UUID id = p.getUniqueId();

        digY.putIfAbsent(id, 70);
        digR.putIfAbsent(id, 15);

        int Y = digY.get(id);
        int R = digR.get(id);

        Inventory inv = Bukkit.createInventory(null, 45, "§8§lUSTAWIENIA FOSY");

        inv.setItem(13, ItemBuilder.create(Material.DIAMOND_BLOCK, "§eY: " + Y));
        inv.setItem(12, ItemBuilder.create(Material.RED_STAINED_GLASS_PANE, "§c-5"));
        inv.setItem(14, ItemBuilder.create(Material.GREEN_STAINED_GLASS_PANE, "§a+5"));

        inv.setItem(22, ItemBuilder.create(Material.GOLD_BLOCK, "§eR: " + R));
        inv.setItem(21, ItemBuilder.create(Material.RED_STAINED_GLASS_PANE, "§c-1"));
        inv.setItem(23, ItemBuilder.create(Material.GREEN_STAINED_GLASS_PANE, "§a+1"));

        inv.setItem(31, ItemBuilder.create(Material.LIME_WOOL, "§a§lSTART"));

        fill(inv);
        p.openInventory(inv);
    }

    public static void handleSettingsClick(Player p, int slot, DigManager dig) {
        UUID id = p.getUniqueId();

        int Y = digY.getOrDefault(id, 70);
        int R = digR.getOrDefault(id, 15);

        switch (slot) {
            case 12: Y = Math.max(5, Y - 5); break;
            case 14: Y = Math.min(200, Y + 5); break;
            case 21: R = Math.max(15, R - 1); break;
            case 23: R = Math.min(41, R + 1); break;

            case 31: // START - kopanie
                p.closeInventory();
                dig.start(p, R, Y);
                return;
        }

        digY.put(id, Y);
        digR.put(id, R);
        openSettingsMenu(p);   // odśwież menu
    }

    // ====================== MENU ŚCIAN ======================
    public static void openWallsSettingsMenu(Player p, Material material, int digRadius) {
        UUID id = p.getUniqueId();

        int minWall = digRadius + 1;
        int maxWall = digRadius + 9;

        // Zawsze ustawiamy aktualną wartość w zakresie min-max
        wallR.putIfAbsent(id, minWall);
        int currentR = wallR.get(id);
        if (currentR < minWall) currentR = minWall;
        if (currentR > maxWall) currentR = maxWall;
        wallR.put(id, currentR);

        wallH.putIfAbsent(id, 80);

        int H = wallH.get(id);

        Inventory inv = Bukkit.createInventory(null, 45, "§8§lUSTAWIENIA ŚCIAN");

        inv.setItem(13, ItemBuilder.create(material, "§eMateriał: " + material.name()));

        inv.setItem(22, ItemBuilder.create(Material.GOLD_BLOCK, "§ePromień ściany: " + currentR,
                "§7Min: " + minWall + " §8| §7Max: " + maxWall));

        inv.setItem(21, ItemBuilder.create(Material.RED_STAINED_GLASS_PANE, "§c-1"));
        inv.setItem(23, ItemBuilder.create(Material.GREEN_STAINED_GLASS_PANE, "§a+1"));

        inv.setItem(31, ItemBuilder.create(Material.DIAMOND_BLOCK, "§eWysokość: " + H,
                "§7Max: 80"));
        inv.setItem(30, ItemBuilder.create(Material.RED_STAINED_GLASS_PANE, "§c-5"));
        inv.setItem(32, ItemBuilder.create(Material.GREEN_STAINED_GLASS_PANE, "§a+5"));

        inv.setItem(40, ItemBuilder.create(Material.LIME_WOOL, "§a§lZACZNIJ BUDOWAĆ"));

        fill(inv);
        p.openInventory(inv);
    }

    public static void handleWallsSettingsClick(Player p, int slot, DigManager dig, Material material, int digRadius) {
        UUID id = p.getUniqueId();

        int minWall = digRadius + 1;
        int maxWall = digRadius + 9;

        int R = wallR.getOrDefault(id, minWall);
        int H = wallH.getOrDefault(id, 80);

        switch (slot) {
            case 21: // -1
                R = Math.max(minWall, R - 1);
                break;
            case 23: // +1
                R = Math.min(maxWall, R + 1);
                break;
            case 30: // -5
                H = Math.max(5, H - 5);
                break;
            case 32: // +5
                H = Math.min(80, H + 5);
                break;

            case 40: // ZACZNIJ BUDOWAĆ
                p.closeInventory();
                dig.buildWalls(p, material, R, H);
                return;
        }
        p.sendMessage("§7[Debug] R = " + R + " | Min=" + minWall + " | Max=" + maxWall);

        wallR.put(id, R);
        wallH.put(id, H);

        // Odśwież menu z aktualnymi limitami
        openWallsSettingsMenu(p, material, digRadius);
    }

    private static void fill(Inventory inv) {
        ItemStack glass = ItemBuilder.create(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, glass);
            }
        }
    }
}