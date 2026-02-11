package net.minegate.plugin.miniGameStatistic.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Represents game statistics for a single match.
 */
public class GameStatistic {
    private final String matchId;
    private final String gameName;
    private final String winner;
    private final int playerCount;
    private final long timestamp;
    private final List<PlayerMatchStatistic> playerStatistics;

    /** Backward-compatible simple constructor. */
    public GameStatistic(String gameName, String winner, int playerCount) {
        this(UUID.randomUUID().toString(), gameName, winner, playerCount,
                System.currentTimeMillis(), new ArrayList<>());
    }

    public GameStatistic(String matchId, String gameName, String winner, int playerCount,
                         long timestamp, List<PlayerMatchStatistic> playerStatistics) {
        this.matchId = matchId;
        this.gameName = gameName;
        this.winner = winner;
        this.playerCount = playerCount;
        this.timestamp = timestamp;
        this.playerStatistics = playerStatistics == null ? new ArrayList<>() : new ArrayList<>(playerStatistics);
    }

    public String getMatchId() { return matchId; }
    public String getGameName() { return gameName; }
    public String getWinner() { return winner; }
    public int getPlayerCount() { return playerCount; }
    public long getTimestamp() { return timestamp; }

    public List<PlayerMatchStatistic> getPlayerStatistics() {
        return Collections.unmodifiableList(playerStatistics);
    }

    /** Return a copy with the given player statistics attached. */
    public GameStatistic withPlayerStatistics(List<PlayerMatchStatistic> stats) {
        return new GameStatistic(matchId, gameName, winner, playerCount, timestamp, stats);
    }

    @Override
    public String toString() {
        return "GameStatistic{matchId='" + matchId + "', gameName='" + gameName
                + "', winner='" + winner + "', playerCount=" + playerCount
                + ", timestamp=" + timestamp + ", players=" + playerStatistics.size() + '}';
    }
}
