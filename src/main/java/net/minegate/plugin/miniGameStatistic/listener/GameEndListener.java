package net.minegate.plugin.miniGameStatistic.listener;

import net.minegate.plugin.miniGameStatistic.MiniGameStatistic;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listens for game end events and triggers statistic collection
 * This is a simplified example - in a real implementation, you would listen for 
 * specific game-end events from your minigame plugin
 */
public class GameEndListener implements Listener {
    private final MiniGameStatistic plugin;

    public GameEndListener(MiniGameStatistic plugin) {
        this.plugin = plugin;
    }

    /**
     * Example: When all players leave, consider the game ended
     * In a real implementation, you would listen for your minigame's end event
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // This is just an example trigger - replace with your actual game end event
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int onlinePlayers = Bukkit.getOnlinePlayers().size();
            if (onlinePlayers == 0) {
                // Game ended - collect and send statistics
                plugin.getLogger().info("Game ended, preparing to send statistics");
            }
        }, 20L); // Check after 1 second
    }

    /**
     * Call this method when your game actually ends to send statistics
     * @param winner The winner of the game
     * @param playerCount The number of players who participated
     */
    public void handleGameEnd(String winner, int playerCount) {
        plugin.handleGameEnd(winner, playerCount);
    }
}
