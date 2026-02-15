package net.minegate.plugin.miniGameStatistic;

import eu.cloudnetservice.driver.channel.ChannelMessage;
import eu.cloudnetservice.driver.event.EventListener;
import eu.cloudnetservice.driver.event.EventManager;
import eu.cloudnetservice.driver.event.events.channel.ChannelMessageReceiveEvent;
import eu.cloudnetservice.driver.inject.InjectionLayer;
import eu.cloudnetservice.driver.network.buffer.DataBuf;
import eu.cloudnetservice.driver.service.ServiceInfoSnapshot;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.DecentHologramsAPI;
import net.minegate.plugin.miniGameStatistic.api.CloudNetAPI;
import net.minegate.plugin.miniGameStatistic.command.GameEndCommand;
import net.minegate.plugin.miniGameStatistic.command.SaveScoreboardCommand;
import net.minegate.plugin.miniGameStatistic.database.DatabaseManager;
import net.minegate.plugin.miniGameStatistic.listener.GameEndListener;
import net.minegate.plugin.miniGameStatistic.model.GameStatistic;
import net.minegate.plugin.miniGameStatistic.model.PlayerMatchStatistic;
import net.minegate.plugin.miniGameStatistic.scoreboard.ScoreboardTracker;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MiniGameStatistic extends JavaPlugin {

    private String mode;
    private String lobbyServer;
    private String proxyService;
    private int teleportDelay;
    private GameEndListener gameEndListener;
    private ScoreboardTracker scoreboardTracker;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        mode = getConfig().getString("mode", "GAME").toUpperCase();
        lobbyServer = getConfig().getString("lobby-server", "Lobby-1");
        proxyService = getConfig().getString("proxy-service", "Proxy-1");
        teleportDelay = getConfig().getInt("teleport-delay", 5);

        getLogger().info("Starting MiniGameStatistic in " + mode + " mode");

        try {
            CloudNetAPI.initialize();

            if ("GAME".equals(mode)) {
                gameEndListener = new GameEndListener(this);
                getServer().getPluginManager().registerEvents(gameEndListener, this);
                getCommand("gameend").setExecutor(new GameEndCommand(this));

                // Initialize scoreboard tracker
                scoreboardTracker = new ScoreboardTracker();
                scoreboardTracker.loadConfig(getConfig().getConfigurationSection("scoreboard"), getLogger());
                if (scoreboardTracker.isEnabled()) {
                    getCommand("savescoreboard").setExecutor(new SaveScoreboardCommand(this));
                }
            } else if ("LOBBY".equals(mode)) {
                initializeDatabase();
                registerChannelMessageListener();
            }
        } catch (Exception e) {
            getLogger().severe("Failed to initialize: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        DatabaseManager.getInstance().close();
        getLogger().info("MiniGameStatistic plugin disabled");
    }

    // ----------------------------------------------------------------
    //  Database
    // ----------------------------------------------------------------

    private void initializeDatabase() {
        try {
            ConfigurationSection dbSection = getConfig().getConfigurationSection("database");
            DatabaseManager.getInstance().initialize(dbSection, getLogger());
        } catch (Exception e) {
            getLogger().severe("[Database] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ----------------------------------------------------------------
    //  Game End (called on GAME servers)
    // ----------------------------------------------------------------

    public void handleGameEnd(String winner, int playerCount) {
        handleGameEnd(winner, playerCount, null);
    }

    public void handleGameEnd(String winner, int playerCount, List<PlayerMatchStatistic> playerStats) {
        if (!"GAME".equals(mode)) return;

        String gameName = getConfig().getString("game-server-name", "auto");
        if ("auto".equals(gameName)) {
            try {
                gameName = InjectionLayer.ext().instance(ServiceInfoSnapshot.class).name();
            } catch (Exception e) {
                gameName = "Unknown";
            }
        }

        GameStatistic statistic = new GameStatistic(gameName, winner, playerCount);
        if (playerStats != null && !playerStats.isEmpty()) {
            statistic = statistic.withPlayerStatistics(playerStats);
        }

        final GameStatistic finalStat = statistic;

        Bukkit.getScheduler().runTaskLater(this, this::teleportPlayersToLobby, teleportDelay * 20L);
        sendStatisticsToLobby(finalStat);
    }

    private void teleportPlayersToLobby() {
        CloudNetAPI api = CloudNetAPI.getInstance();
        getLogger().info("Teleporting players to " + lobbyServer);

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                String command = "send " + player.getName() + " " + lobbyServer;
                api.executeServiceCommand(proxyService, command);
                player.sendMessage("\u00a7aTeleporting to lobby...");
            } catch (Exception e) {
                getLogger().severe("Failed to teleport " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    // ----------------------------------------------------------------
    //  CloudNet Channel Message (GAME -> LOBBY) — dynamic fields
    // ----------------------------------------------------------------

    private void sendStatisticsToLobby(GameStatistic statistic) {
        try {
            getLogger().info("Sending statistics to lobby: " + statistic);

            DataBuf.Mutable buf = DataBuf.empty()
                    .writeString(statistic.getMatchId())
                    .writeString(statistic.getGameName())
                    .writeString(statistic.getWinner())
                    .writeInt(statistic.getPlayerCount())
                    .writeLong(statistic.getTimestamp());

            // Write player stats with dynamic fields
            List<PlayerMatchStatistic> players = statistic.getPlayerStatistics();
            buf.writeInt(players.size());
            for (PlayerMatchStatistic p : players) {
                buf.writeString(p.getPlayerName());
                buf.writeString(p.getPlayerUUID() != null ? p.getPlayerUUID() : "");
                // Write dynamic stat fields as count + (key, value) pairs
                Map<String, Integer> stats = p.getStats();
                buf.writeInt(stats.size());
                for (Map.Entry<String, Integer> entry : stats.entrySet()) {
                    buf.writeString(entry.getKey());
                    buf.writeInt(entry.getValue());
                }
            }

            ChannelMessage.builder()
                    .channel("minigame_statistics")
                    .message("game_end")
                    .targetService(lobbyServer)
                    .build(buf)
                    .send();

            getLogger().info("Statistics sent successfully to " + lobbyServer);
        } catch (Exception e) {
            getLogger().severe("Failed to send statistics: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void registerChannelMessageListener() {
        EventManager eventManager = InjectionLayer.ext().instance(EventManager.class);
        eventManager.registerListener(new Object() {
            @EventListener
            public void handleChannelMessage(ChannelMessageReceiveEvent event) {
                ChannelMessage message = event.channelMessage();
                if (!"minigame_statistics".equals(message.channel()) || !"game_end".equals(message.message())) {
                    return;
                }
                try {
                    DataBuf content = message.content();
                    String matchId = content.readString();
                    String gameName = content.readString();
                    String winner = content.readString();
                    int playerCount = content.readInt();
                    long timestamp = content.readLong();

                    int playerStatsCount = content.readInt();
                    List<PlayerMatchStatistic> players = new ArrayList<>();
                    for (int i = 0; i < playerStatsCount; i++) {
                        String pName = content.readString();
                        String pUuid = content.readString();
                        int fieldCount = content.readInt();
                        Map<String, Integer> stats = new LinkedHashMap<>();
                        for (int j = 0; j < fieldCount; j++) {
                            stats.put(content.readString(), content.readInt());
                        }
                        players.add(new PlayerMatchStatistic(pName, pUuid, stats));
                    }

                    GameStatistic statistic = new GameStatistic(matchId, gameName, winner, playerCount, timestamp, players);
                    Bukkit.getScheduler().runTask(MiniGameStatistic.this, () -> onStatisticsReceived(statistic));
                } catch (Exception e) {
                    getLogger().severe("Failed to parse statistics message: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    // ----------------------------------------------------------------
    //  Lobby-side: receive, persist, and display
    // ----------------------------------------------------------------

    private void onStatisticsReceived(GameStatistic statistic) {
        getLogger().info("Received statistics: " + statistic);

        if (DatabaseManager.getInstance().isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    DatabaseManager.getInstance().getRepository().saveStatistic(statistic);
                    getLogger().info("[Database] Saved match " + statistic.getMatchId());
                } catch (Exception e) {
                    getLogger().severe("[Database] Failed to save: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }

        displayStatisticsHolograms(statistic);
        Bukkit.broadcastMessage("\u00a76\u00a7l[Game Stats] \u00a7eMatch finished! Check the hologram for details.");
    }

    // ----------------------------------------------------------------
    //  Hologram display (multi-location, dynamic field placeholders)
    // ----------------------------------------------------------------

    private void clearOldHolograms() {
        new ArrayList<>(DecentHologramsAPI.get().getHologramManager().getHolograms()).forEach(holo -> {
            if (holo.getName().startsWith("stats_")) {
                holo.delete();
            }
        });
    }

    private List<Location> getHologramLocations() {
        List<Location> locations = new ArrayList<>();

        if (getConfig().contains("hologram-locations")) {
            List<?> list = getConfig().getList("hologram-locations");
            if (list != null) {
                for (Object item : list) {
                    if (item instanceof java.util.Map<?, ?> map) {
                        Object worldObj = map.get("world");
                        String worldName = worldObj != null ? String.valueOf(worldObj) : "world";
                        double x = toDouble(map.get("x"));
                        double y = toDouble(map.get("y"));
                        double z = toDouble(map.get("z"));
                        var world = Bukkit.getWorld(worldName);
                        if (world != null) {
                            locations.add(new Location(world, x, y, z));
                        }
                    }
                }
            }
        }

        if (locations.isEmpty() && getConfig().contains("hologram-location")) {
            String worldName = getConfig().getString("hologram-location.world", "world");
            double x = getConfig().getDouble("hologram-location.x");
            double y = getConfig().getDouble("hologram-location.y");
            double z = getConfig().getDouble("hologram-location.z");
            var world = Bukkit.getWorld(worldName);
            if (world != null) {
                locations.add(new Location(world, x, y, z));
            }
        }

        return locations;
    }

    private void displayStatisticsHolograms(GameStatistic statistic) {
        clearOldHolograms();

        int duration = getConfig().getInt("hologram-duration", 30);
        List<Location> locations = getHologramLocations();

        if (locations.isEmpty()) {
            getLogger().warning("No hologram locations configured!");
            return;
        }

        List<String> lines = buildHologramLines(statistic);

        int idx = 0;
        List<String> holoNames = new ArrayList<>();
        for (Location loc : locations) {
            String holoName = "stats_" + System.currentTimeMillis() + "_" + idx++;
            DHAPI.createHologram(holoName, loc, lines);
            holoNames.add(holoName);
        }

        if (duration > 0) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                for (String name : holoNames) {
                    if (DHAPI.getHologram(name) != null) {
                        DHAPI.removeHologram(name);
                    }
                }
            }, duration * 20L);
        }
    }

    private List<String> buildHologramLines(GameStatistic statistic) {
        List<String> lines = new ArrayList<>();

        // Header lines
        List<String> headerTemplates = getConfig().getStringList("hologram-header");
        if (headerTemplates.isEmpty()) {
            headerTemplates = List.of(
                    "&6&l\u2605 Game Over \u2605",
                    "&eServer: &f{game_name}",
                    "&aWinner: &f{winner}",
                    "&bPlayers: &f{player_count}"
            );
        }
        for (String tmpl : headerTemplates) {
            lines.add(replacePlaceholders(tmpl, statistic));
        }

        // Per-player lines — dynamic field placeholders
        String playerTemplate = getConfig().getString("hologram-player-line",
                "&f{player_name}  &cK:{kills} &4D:{deaths} &eA:{assists} &bS:{score}");
        for (PlayerMatchStatistic p : statistic.getPlayerStatistics()) {
            lines.add(replacePlayerPlaceholders(playerTemplate, p));
        }

        // Footer lines
        List<String> footerTemplates = getConfig().getStringList("hologram-footer");
        for (String tmpl : footerTemplates) {
            lines.add(replacePlaceholders(tmpl, statistic));
        }

        lines.replaceAll(line -> line.replace('&', '\u00a7'));
        return lines;
    }

    private String replacePlaceholders(String template, GameStatistic stat) {
        return template
                .replace("{game_name}", stat.getGameName())
                .replace("{winner}", stat.getWinner())
                .replace("{player_count}", String.valueOf(stat.getPlayerCount()))
                .replace("{match_id}", stat.getMatchId())
                .replace("{duration}", String.valueOf(getConfig().getInt("hologram-duration", 30)));
    }

    /**
     * Replace player placeholders dynamically: {player_name} plus any stat field
     * like {kills}, {deaths}, {damage}, etc.
     */
    private String replacePlayerPlaceholders(String template, PlayerMatchStatistic p) {
        String result = template.replace("{player_name}", p.getPlayerName());
        // Replace all dynamic stat field placeholders
        for (Map.Entry<String, Integer> entry : p.getStats().entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    private static double toDouble(Object obj) {
        if (obj instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(obj)); }
        catch (Exception e) { return 0.0; }
    }

    public GameEndListener getGameEndListener() {
        return gameEndListener;
    }

    public ScoreboardTracker getScoreboardTracker() {
        return scoreboardTracker;
    }
}
