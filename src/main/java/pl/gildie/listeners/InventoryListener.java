package pl.gildie.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import pl.gildie.managers.DigManager;
import pl.gildie.managers.MenuManager;

public class InventoryListener implements Listener {

    private final DigManager dig;

    public InventoryListener(DigManager dig) {
        this.dig = dig;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {

        if (!(e.getWhoClicked() instanceof Player)) return;

        Player p = (Player) e.getWhoClicked();
        String rawTitle = e.getView().getTitle();
        String title = rawTitle.replace("§", "");   // usuwa kolory dla bezpiecznego porównania
        int slot = e.getRawSlot();

        // ====================== GŁÓWNE MENU ======================
        if (title.contains("GIGA FOS MENU")) {
            e.setCancelled(true);

            if (slot == 13) {                    // KOPANIE - łopata
                MenuManager.openSettingsMenu(p);
            }
            else if (slot == 11) {               // Ściany z Piasku
                int digRadius = dig.getPlayerDigRadius(p);
                if (digRadius <= 0) {
                    p.sendMessage("§cNajpierw wykop fosę!");
                    return;
                }
                MenuManager.openWallsSettingsMenu(p, Material.SAND, digRadius);
            }
            else if (slot == 15) {               // Ściany z Obsydianu
                int digRadius = dig.getPlayerDigRadius(p);
                if (digRadius <= 0) {
                    p.sendMessage("§cNajpierw wykop fosę!");
                    return;
                }
                MenuManager.openWallsSettingsMenu(p, Material.OBSIDIAN, digRadius);
            }
            return;
        }

        // ====================== MENU USTAWIEŃ FOSY ======================
        if (title.contains("USTAWIENIA FOSY")) {
            e.setCancelled(true);
            MenuManager.handleSettingsClick(p, slot, dig);
            return;
        }

        // ====================== MENU USTAWIEŃ ŚCIAN ======================
        if (title.contains("USTAWIENIA ŚCIAN")) {
            e.setCancelled(true);

            Material mat = null;
            if (e.getView().getItem(13) != null) {
                mat = e.getView().getItem(13).getType();
            }

            int digRadius = dig.getPlayerDigRadius(p);

            if (mat != null && (mat == Material.SAND || mat == Material.OBSIDIAN)) {
                MenuManager.handleWallsSettingsClick(p, slot, dig, mat, digRadius);
            }
        }
    }
}