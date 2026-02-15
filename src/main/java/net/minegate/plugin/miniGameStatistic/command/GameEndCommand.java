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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Command to trigger game end.
 *
 * <p>Usage:
 *   /gameend &lt;winner&gt; [playerCount] [player:uuid:field1=val1:field2=val2 ...]
 *
 * <p>Example:
 *   /gameend Steve 8 Steve:uuid1:kills=5:deaths=2:score=100 Alex:uuid2:kills=3:deaths=4:score=60
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
            sender.sendMessage("\u00a7cUsage: /gameend <winner> [playerCount] [player:uuid:field=val:field=val ...]");
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
                int snapped = tracker.snapshotCurrentScoreboard();
                if (tracker.hasData()) {
                    playerStats = tracker.buildStatistics();
                    sender.sendMessage("\u00a7aAuto-attached scoreboard data for \u00a7f"
                            + playerStats.size() + "\u00a7a players (final snapshot: "
                            + snapped + ", mode: " + tracker.getMergeMode() + ").");
                }
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
     * Parse "PlayerName:UUID:field1=val1:field2=val2:..."
     * Also supports legacy format "PlayerName:UUID:kills:deaths:assists:score"
     */
    static PlayerMatchStatistic parsePlayerStat(String raw) {
        String[] parts = raw.split(":");
        if (parts.length < 3) return null;
        try {
            String name = parts[0];
            String uuid = parts[1];

            // Check if new key=value format or legacy positional format
            if (parts.length >= 3 && parts[2].contains("=")) {
                // New format: field=value pairs
                Map<String, Integer> stats = new LinkedHashMap<>();
                for (int i = 2; i < parts.length; i++) {
                    String[] kv = parts[i].split("=", 2);
                    if (kv.length == 2) {
                        stats.put(kv[0], Integer.parseInt(kv[1]));
                    }
                }
                return new PlayerMatchStatistic(name, uuid, stats);
            } else if (parts.length >= 6) {
                // Legacy format: name:uuid:kills:deaths:assists:score
                return new PlayerMatchStatistic(name, uuid,
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3]),
                        Integer.parseInt(parts[4]),
                        Integer.parseInt(parts[5]));
            }
            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static int parseIntSafe(String s) {
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) { return -1; }
    }
}
