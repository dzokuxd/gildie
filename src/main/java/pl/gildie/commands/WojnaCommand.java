package pl.gildie.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.gildie.managers.GuildManager;
import pl.gildie.war.TntManager;
import pl.gildie.war.WarGui;
import pl.gildie.war.WarManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Alias /wojna → otwiera to samo GUI co /g wojna.
 * Wszystkie akcje są w GUI, bez subkomend tekstowych.
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
            sender.sendMessage("§cTylko gracz.");
            return true;
        }
        // Admin force TNT
        if (args.length >= 1 && args[0].equalsIgnoreCase("tnt") && player.hasPermission("gildie.admin")) {
            boolean now = !TntManager.isTntEnabled();
            TntManager.setEnabled(now);
            player.sendMessage("§eTNT force: " + (now ? "§awłączone" : "§cwyłączone"));
            return true;
        }
        WarGui.openMain(player, guildManager, warManager);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
