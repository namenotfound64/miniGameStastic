package net.minegate.plugin.miniGameStatistic.model;

/**
 * Represents game statistics to be sent to the lobby server
 */
public class GameStatistic {
    private final String gameName;
    private final String winner;
    private final int playerCount;
    private final long timestamp;

    public GameStatistic(String gameName, String winner, int playerCount) {
        this.gameName = gameName;
        this.winner = winner;
        this.playerCount = playerCount;
        this.timestamp = System.currentTimeMillis();
    }

    public String getGameName() {
        return gameName;
    }

    public String getWinner() {
        return winner;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "GameStatistic{" +
                "gameName='" + gameName + '\'' +
                ", winner='" + winner + '\'' +
                ", playerCount=" + playerCount +
                ", timestamp=" + timestamp +
                '}';
    }
}
