package net.minegate.plugin.miniGameStatistic;

import eu.cloudnetservice.driver.channel.ChannelMessage;
import eu.cloudnetservice.driver.channel.ChannelMessageTarget;
import eu.cloudnetservice.driver.event.EventListener;
import eu.cloudnetservice.driver.event.EventManager;
import eu.cloudnetservice.driver.event.events.channel.ChannelMessageReceiveEvent;
import eu.cloudnetservice.driver.inject.InjectionLayer;
import net.minegate.plugin.miniGameStatistic.api.CloudNetAPI;
import net.minegate.plugin.miniGameStatistic.command.GameEndCommand;
import net.minegate.plugin.miniGameStatistic.listener.GameEndListener;
import net.minegate.plugin.miniGameStatistic.model.GameStatistic;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;
import eu.decentsoftware.holograms.api.DecentHologramsAPI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public final class MiniGameStatistic extends JavaPlugin {

    private String mode;
    private String lobbyServer;
    private String proxyService;
    private int teleportDelay;
    private GameEndListener gameEndListener;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();
        
        // Load configuration
        mode = getConfig().getString("mode", "GAME").toUpperCase();
        lobbyServer = getConfig().getString("lobby-server", "Lobby-1");
        proxyService = getConfig().getString("proxy-service", "Proxy-1");
        teleportDelay = getConfig().getInt("teleport-delay", 5);
        
        getLogger().info("Starting MiniGameStatistic plugin in " + mode + " mode");
        
        try {
            // Initialize CloudNet API
            CloudNetAPI.initialize();
            getLogger().info("CloudNet API initialized successfully");
            
            if ("GAME".equals(mode)) {
                // Game mode: Register event listener for game end
                gameEndListener = new GameEndListener(this);
                getServer().getPluginManager().registerEvents(gameEndListener, this);
                getLogger().info("Game mode: Registered game end listener");
                
                // Register game end command for manual triggering
                getCommand("gameend").setExecutor(new GameEndCommand(this));
                getLogger().info("Game mode: Registered /gameend command");
            } else if ("LOBBY".equals(mode)) {
                // Lobby mode: Register CloudNet channel message listener
                registerChannelMessageListener();
                getLogger().info("Lobby mode: Registered channel message listener");
            } else {
                getLogger().warning("Unknown mode: " + mode + ". Please set mode to LOBBY or GAME in config.yml");
            }
            
        } catch (Exception e) {
            getLogger().severe("Failed to initialize CloudNet API: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("MiniGameStatistic plugin disabled");
    }

    /**
     * Handle game end in GAME mode
     * Teleports players to lobby and sends statistics
     */
    public void handleGameEnd(String winner, int playerCount) {
        if (!"GAME".equals(mode)) {
            return;
        }

        String gameName = getConfig().getString("game-server-name", "auto");
        if ("auto".equals(gameName)) {
            // Try to get current service name from CloudNet
            try {
                gameName = InjectionLayer.ext().instance(eu.cloudnetservice.driver.service.ServiceInfoSnapshot.class).name();
            } catch (Exception e) {
                gameName = "Unknown";
            }
        }

        getLogger().info("Game ended! Winner: " + winner + ", Players: " + playerCount);

        // Create statistics object
        final GameStatistic statistic = new GameStatistic(gameName, winner, playerCount);

        // Teleport players after delay
        Bukkit.getScheduler().runTaskLater(this, () -> {
            teleportPlayersToLobby();
        }, teleportDelay * 20L); // Convert seconds to ticks

        // Send statistics to lobby server
        sendStatisticsToLobby(statistic);
    }

    /**
     * Teleport all players to the lobby server
     */
    private void teleportPlayersToLobby() {
        CloudNetAPI api = CloudNetAPI.getInstance();
        int successCount = 0;
        int failCount = 0;

        getLogger().info("Teleporting " + Bukkit.getOnlinePlayers().size() + " players to " + lobbyServer);

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                // Execute "send <player> <server>" command on proxy service
                String command = "send " + player.getName() + " " + lobbyServer;
                api.executeServiceCommand(proxyService, command);
                
                successCount++;
                getLogger().info("Sent teleport command for " + player.getName() + " to " + lobbyServer);
                
                // Send message to player
                player.sendMessage("§aTeleporting to lobby in " + teleportDelay + " seconds...");
                
            } catch (Exception e) {
                failCount++;
                getLogger().severe("Failed to send teleport command for " + player.getName() + ": " + e.getMessage());
            }
        }

        getLogger().info("Teleport commands sent: " + successCount + " succeeded, " + failCount + " failed");
    }

    /**
     * Send game statistics to lobby server via CloudNet ChannelMessage
     */
    private void sendStatisticsToLobby(GameStatistic statistic) {
        try {
            getLogger().info("Sending statistics to lobby server: " + statistic);

            ChannelMessage.builder()
                    .channel("minigame_statistics")
                    .message("game_end")
                    .buffer()
                    .writeString(statistic.getGameName())
                    .writeString(statistic.getWinner())
                    .writeInt(statistic.getPlayerCount())
                    .writeLong(statistic.getTimestamp())
                    .targetService(lobbyServer)
                    .build()
                    .send();

            getLogger().info("Statistics sent successfully to " + lobbyServer);
        } catch (Exception e) {
            getLogger().severe("Failed to send statistics: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Register listener for incoming statistics messages in LOBBY mode
     */
    private void registerChannelMessageListener() {
        EventManager eventManager = InjectionLayer.ext().instance(EventManager.class);
        
        eventManager.registerListener(new Object() {
            @EventListener
            public void handleChannelMessage(ChannelMessageReceiveEvent event) {
                ChannelMessage message = event.channelMessage();
                
                // Check if this is a minigame statistics message
                if ("minigame_statistics".equals(message.channel()) && 
                    "game_end".equals(message.message())) {
                    
                    try {
                        // Read statistics data
                        String gameName = message.content().readString();
                        String winner = message.content().readString();
                        int playerCount = message.content().readInt();
                        long timestamp = message.content().readLong();
                        
                        GameStatistic statistic = new GameStatistic(gameName, winner, playerCount);
                        
                        getLogger().info("Received game statistics: " + statistic);
                        
                        // Broadcast to all players on lobby
                        Bukkit.getScheduler().runTask(MiniGameStatistic.this, () -> {
                            broadcastStatistics(statistic);
                        });
                        
                    } catch (Exception e) {
                        getLogger().severe("Failed to read statistics message: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    /**
     * Broadcast game statistics to all players in lobby
     */
    private Location getHologramLocation() {
        String worldName = getConfig().getString("hologram-location.world", "world");
        double x = getConfig().getDouble("hologram-location.x");
        double y = getConfig().getDouble("hologram-location.y");
        double z = getConfig().getDouble("hologram-location.z");
        return new Location(Bukkit.getWorld(worldName), x, y, z);
    }

    private void clearOldHolograms() {
        // 使用 DecentHolograms API 获取全息图列表
        // 注意：DHAPI.getHologramManager().getHolograms()
        // 建议将其放入一个新列表中遍历，防止并发修改异常
        new ArrayList<>(DecentHologramsAPI.get().getHologramManager().getHolograms()).forEach(holo -> {
            if (holo.getName().startsWith("stats_")) {
                holo.delete();
            }
        });
    }

    private void broadcastStatistics(GameStatistic statistic) {
        // 1. 先清理
        clearOldHolograms();

        int duration = getConfig().getInt("hologram-duration", 15);
        Location spawnLocation = getHologramLocation();

        // 2. 使用 ArrayList (可变列表)
        List<String> lines = new ArrayList<>();
        lines.add("§6§l★ 游戏结束统计 ★");
        lines.add("§e游戏服务器: §f" + statistic.getGameName());
        lines.add("§a获胜者: §f" + statistic.getWinner());
        lines.add("§b在线人数: §f" + statistic.getPlayerCount());

        // 3. 根据 duration 动态添加最后一行提示
        if (duration > 0) {
            lines.add("§7(本提示将在 " + duration + " 秒后自动刷新)");
        } else {
            lines.add("§7(本提示将保留至下一场比赛)");
        }

        // 4. 创建全息图 (此时 lines 已经包含了所有信息)
        String holoName = "stats_" + System.currentTimeMillis();
        Hologram hologram = DHAPI.createHologram(holoName, spawnLocation, lines);

        Bukkit.broadcastMessage("§6§l[Game Stats] §e小游戏已结束！统计信息已在大厅显示。");

        // 5. 处理自动删除逻辑
        if (duration > 0) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (DHAPI.getHologram(holoName) != null) {
                    DHAPI.removeHologram(holoName);
                    getLogger().info("已删除过期的统计全息图: " + holoName);
                }
            }, duration * 20L);
        }

    }

    /**
     * Get the game end listener (useful for manual triggering)
     */
    public GameEndListener getGameEndListener() {
        return gameEndListener;
    }
}
