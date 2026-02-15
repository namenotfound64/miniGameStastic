package net.minegate.plugin.miniGameStatistic.command;

import net.minegate.plugin.miniGameStatistic.MiniGameStatistic;
import net.minegate.plugin.miniGameStatistic.model.PlayerMatchStatistic;
import net.minegate.plugin.miniGameStatistic.scoreboard.ScoreboardTracker;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to trigger game end.
 *
 * <p>Usage:
 *   /gameend &lt;winner&gt; [playerCount] [player:uuid:kills:deaths:assists:score ...]
 *
 * <p>Behavior:
 * <ul>
 *   <li>If manual player stats are provided on the command line, those are used.</li>
 *   <li>Otherwise, if scoreboard tracking is enabled, a final snapshot is taken
 *       and the accumulated scoreboard data is attached automatically.</li>
 *   <li>After sending, the scoreboard tracker is cleared for the next session.</li>
 * </ul>
 */
public class GameEndCommand implements CommandExecutor {
    private final MiniGameStatistic plugin;

    public GameEndCommand(MiniGameStatistic plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("minigamestatistic.gameend")) {
            sender.sendMessage("\u00a7cYou don't have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("\u00a7cUsage: /gameend <winner> [playerCount] [player:uuid:kills:deaths:assists:score ...]");
            return true;
        }

        String winner = args[0];
        int playerCount = Bukkit.getOnlinePlayers().size();

        // Optional second arg = player count
        int statsStartIndex = 1;
        if (args.length > 1) {
            int parsed = parseIntSafe(args[1]);
            if (parsed >= 0) {
                playerCount = parsed;
                statsStartIndex = 2;
            }
        }

        // Parse optional per-player stats from command line
        List<PlayerMatchStatistic> playerStats = new ArrayList<>();
        for (int i = statsStartIndex; i < args.length; i++) {
            PlayerMatchStatistic stat = parsePlayerStat(args[i]);
            if (stat != null) {
                playerStats.add(stat);
            } else {
                sender.sendMessage("\u00a7eWarning: Skipped malformed stat entry: " + args[i]);
            }
        }

        // If no manual stats provided, try scoreboard tracker
        if (playerStats.isEmpty()) {
            ScoreboardTracker tracker = plugin.getScoreboardTracker();
            if (tracker != null && tracker.isEnabled()) {
                // Take a final snapshot to capture the latest scoreboard state
                int snapped = tracker.snapshotCurrentScoreboard();
                if (tracker.hasData()) {
                    playerStats = tracker.buildStatistics();
                    sender.sendMessage("\u00a7aAuto-attached scoreboard data for \u00a7f"
                            + playerStats.size() + "\u00a7a players (final snapshot: "
                            + snapped + ", mode: " + tracker.getMergeMode() + ").");
                }
                // Clear accumulated data for next game session
                tracker.clear();
            }
        }

        sender.sendMessage("\u00a7aEnding game | winner: \u00a7f" + winner
                + " \u00a7a| players: \u00a7f" + playerCount
                + " \u00a7a| stats entries: \u00a7f" + playerStats.size());

        plugin.handleGameEnd(winner, playerCount, playerStats.isEmpty() ? null : playerStats);
        return true;
    }

    /**
     * Parse "PlayerName:UUID:kills:deaths:assists:score"
     */
    static PlayerMatchStatistic parsePlayerStat(String raw) {
        String[] parts = raw.split(":");
        if (parts.length < 6) return null;
        try {
            return new PlayerMatchStatistic(
                    parts[0], parts[1],
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4]),
                    Integer.parseInt(parts[5])
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static int parseIntSafe(String s) {
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) { return -1; }
    }
}
