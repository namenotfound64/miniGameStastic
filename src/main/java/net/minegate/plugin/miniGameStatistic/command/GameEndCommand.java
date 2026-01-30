package net.minegate.plugin.miniGameStatistic.command;

import net.minegate.plugin.miniGameStatistic.MiniGameStatistic;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Example command to manually trigger game end for testing purposes
 * Usage: /gameend <winner> [playerCount]
 */
public class GameEndCommand implements CommandExecutor {
    private final MiniGameStatistic plugin;

    public GameEndCommand(MiniGameStatistic plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("minigamestatistic.gameend")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cUsage: /gameend <winner> [playerCount]");
            return true;
        }

        String winner = args[0];
        int playerCount = args.length > 1 ? parsePlayerCount(args[1]) : Bukkit.getOnlinePlayers().size();

        if (playerCount < 0) {
            sender.sendMessage("§cInvalid player count!");
            return true;
        }

        sender.sendMessage("§aEnding game with winner: §f" + winner + " §aand §f" + playerCount + " §aplayers");
        plugin.handleGameEnd(winner, playerCount);

        return true;
    }

    private int parsePlayerCount(String arg) {
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
