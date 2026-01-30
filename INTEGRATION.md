# Integration Guide

This guide explains how to integrate the MiniGameStatistic plugin with your existing minigame.

## For Minigame Developers

### Option 1: Using the Plugin API (Recommended)

When your game ends, simply call the plugin's `handleGameEnd` method:

```java
// Get the MiniGameStatistic plugin instance
MiniGameStatistic statPlugin = (MiniGameStatistic) Bukkit.getPluginManager().getPlugin("MiniGameStatistic");

if (statPlugin != null) {
    // Call when your game ends
    // Parameters: winner name, total player count
    statPlugin.handleGameEnd("PlayerName", 10);
}
```

### Option 2: Using the Command

For testing or manual triggering, you can use the `/gameend` command:

```
/gameend <winner> [playerCount]
```

Example:
```
/gameend Steve 8
```

This will:
1. Send statistics to the lobby server
2. After 5 seconds (configurable), teleport all players to the lobby

### Option 3: Extending the GameEndListener

You can extend the `GameEndListener` class to listen for your specific game events:

```java
public class MyGameListener extends GameEndListener {
    
    public MyGameListener(MiniGameStatistic plugin) {
        super(plugin);
    }
    
    @EventHandler
    public void onMyGameEnd(MyGameEndEvent event) {
        // Get winner and player count from your event
        String winner = event.getWinner().getName();
        int playerCount = event.getPlayers().size();
        
        // Trigger the statistics and teleportation
        handleGameEnd(winner, playerCount);
    }
}
```

## Configuration Examples

### Game Server Configuration

```yaml
# config.yml for game servers
mode: GAME
lobby-server: "Lobby-1"
proxy-service: "Proxy-1"
teleport-delay: 5
game-server-name: "auto"  # or specify like "BedWars-1"
```

### Lobby Server Configuration

```yaml
# config.yml for lobby servers
mode: LOBBY
lobby-server: "Lobby-1"
proxy-service: "Proxy-1"
teleport-delay: 5
game-server-name: "auto"
```

## Player Experience

### On Game Server (GAME mode)
1. When game ends, players see: "§aTeleporting to lobby in 5 seconds..."
2. Statistics are sent to the lobby server
3. After 5 seconds, all players are teleported to the lobby

### On Lobby Server (LOBBY mode)
1. When statistics are received, all lobby players see:
   ```
   §6§l[Game Stats] §eBedWars-1 §7ended! §aWinner: §fSteve §7| §aPlayers: §f8
   ```

## Advanced Usage

### Custom Statistics Message Format

To customize the broadcast message, modify the `broadcastStatistics` method in `MiniGameStatistic.java`:

```java
private void broadcastStatistics(GameStatistic statistic) {
    String message = String.format(
        "§6§l[Game Stats] §e%s §7ended! §aWinner: §f%s §7| §aPlayers: §f%d",
        statistic.getGameName(),
        statistic.getWinner(),
        statistic.getPlayerCount()
    );
    
    Bukkit.broadcastMessage(message);
}
```

### Using CloudNet ChannelMessage Directly

If you want to send custom data, you can use CloudNet's ChannelMessage API:

```java
ChannelMessage.builder()
    .channel("minigame_statistics")
    .message("game_end")
    .json(true)
    .buffer()
        .writeString("GameName")
        .writeString("Winner")
        .writeInt(10)
        .writeLong(System.currentTimeMillis())
    .build()
    .targetService("Lobby-1")
    .build()
    .send();
```

## Troubleshooting

### Players not teleporting
- Check that the proxy service name is correct in `config.yml`
- Verify that the lobby server is running
- Check console logs for error messages

### Statistics not appearing on lobby
- Ensure lobby server has `mode: LOBBY` in config.yml
- Check that CloudNet is properly configured
- Verify both servers are on the same CloudNet network

### CloudNet API errors
- Make sure the plugin is running on a CloudNet service
- Verify CloudNet version is 4.0.0-RC10 or higher
- Check that CloudNet Driver and Bridge modules are available

## Example Minigame Integration

```java
public class MyMiniGame extends JavaPlugin {
    private MiniGameStatistic statPlugin;
    
    @Override
    public void onEnable() {
        // Get the statistics plugin
        statPlugin = (MiniGameStatistic) Bukkit.getPluginManager().getPlugin("MiniGameStatistic");
        
        if (statPlugin == null) {
            getLogger().warning("MiniGameStatistic plugin not found!");
        }
    }
    
    public void endGame(Player winner, List<Player> players) {
        // Your game ending logic here...
        
        // Send statistics and teleport players
        if (statPlugin != null) {
            statPlugin.handleGameEnd(winner.getName(), players.size());
        } else {
            // Fallback: manually teleport players if needed
            getLogger().warning("Cannot send statistics - plugin not available");
        }
    }
}
```

## Dependencies in your plugin.yml

If your minigame plugin depends on MiniGameStatistic, add this to your `plugin.yml`:

```yaml
depend: [MiniGameStatistic]
```

Or use `softdepend` if it's optional:

```yaml
softdepend: [MiniGameStatistic]
```
