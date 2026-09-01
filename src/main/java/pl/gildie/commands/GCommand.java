package pl.gildie.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.gildie.managers.GuildManager;
import pl.gildie.managers.MenuManager;
import pl.gildie.managers.RegenManager;
import pl.gildie.managers.TerritoryBarManager;
import pl.gildie.model.Guild;

public class GCommand implements CommandExecutor {
    private static final int DEFAULT_RADIUS = 50;

    private final GuildManager guildManager;
    private final RegenManager regenManager;
    private final TerritoryBarManager territoryBarManager;

    public GCommand(GuildManager guildManager, RegenManager regenManager, TerritoryBarManager territoryBarManager) {
        this.guildManager = guildManager;
        this.regenManager = regenManager;
        this.territoryBarManager = territoryBarManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cTylko gracz moze uzywac tej komendy.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§8§m-----------------------------");
            player.sendMessage("§e/g zaloz <tag> §7- zaklada gildie z terenem");
            player.sendMessage("§e/g regeneruj §7- regeneruje bloki ponizej Y=60");
            player.sendMessage("§8§m-----------------------------");
            return true;
        }

        if (args[0].equalsIgnoreCase("zaloz")) {
            if (args.length < 2) {
                player.sendMessage("§cUzycie: /g zaloz <tag>");
                return true;
            }
            String tag = args[1];
            if (!tag.matches("[A-Za-z0-9]{2,5}")) {
                player.sendMessage("§cTag musi miec 2-5 znakow (litery i cyfry).");
                return true;
            }
            if (guildManager.getGuildByPlayer(player.getUniqueId()) != null) {
                player.sendMessage("§cJestes juz w gildii!");
                return true;
            }
            if (guildManager.getGuild(tag) != null) {
                player.sendMessage("§cTaka gildia juz istnieje!");
                return true;
            }
            if (guildManager.getGuildAt(player.getLocation()) != null) {
                player.sendMessage("§cStoisz na terenie innej gildii!");
                return true;
            }

            boolean ok = guildManager.createGuild(tag, player.getUniqueId(), player.getLocation(), DEFAULT_RADIUS);
            if (ok) {
                player.sendMessage("§aZalozyles gildie §e" + tag.toUpperCase()
                        + " §az terenem o promieniu §e" + DEFAULT_RADIUS + " §ablokow!");
                territoryBarManager.update(player);
            } else {
                player.sendMessage("§cNie udalo sie zalozyc gildii.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("regeneruj")) {
            if (guildManager.getGuildByPlayer(player.getUniqueId()) == null) {
                player.sendMessage("§cNie jestes w zadnej gildii!");
                return true;
            }
            regenManager.startManualRegen(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("panel")) {
            MenuManager.openMainMenu(player);
            return true;
        }
        player.sendMessage("§cNieznana komenda. Uzyj §e/g");
        return true;
    }
}
