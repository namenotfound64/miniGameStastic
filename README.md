# MiniGameStatistic Plugin

A Minecraft Paper plugin for **CloudNet v4** (RC16) that:

1. **Teleports players** back to the lobby server when a minigame ends (via command or API)
2. **Persists match data** (winner, player count, per-player kills/deaths/assists/score) to a database (PostgreSQL / MySQL / MariaDB / MongoDB)
3. **Displays a hologram** on the lobby server showing the last match's statistics using DecentHolograms

---

## Requirements

| Component | Version |
|---|---|
| Minecraft Server | Paper 1.20.6+ |
| Java | 21+ |
| CloudNet | 4.0.0-RC16 |
| DecentHolograms | 2.8.6+ |
| Database (optional) | PostgreSQL 12+ / MySQL 8.0+ / MariaDB 10.5+ / MongoDB 5.0+ |

## Project Structure

```
src/main/java/net/minegate/plugin/miniGameStatistic/
  MiniGameStatistic.java          # Main plugin class (GAME & LOBBY modes)
  api/
    CloudNetAPI.java              # CloudNet v4 Driver API wrapper
    GameEndAPI.java               # Public API for external plugins to trigger game end
  command/
    GameEndCommand.java           # /gameend command executor
  database/
    GameStatisticRepository.java  # Repository interface
    SqlRepository.java            # PostgreSQL / MySQL / MariaDB implementation
    MongoRepository.java          # MongoDB implementation
    DatabaseManager.java          # Factory & singleton holder
  listener/
    GameEndListener.java          # Bukkit event listener (example)
  model/
    GameStatistic.java            # Match-level data model
    PlayerMatchStatistic.java     # Per-player data model
```

## Configuration (`config.yml`)

```yaml
mode: GAME                    # GAME or LOBBY
lobby-server: "Lobby-1"
proxy-service: "Proxy-1"
teleport-delay: 5
game-server-name: "auto"      # auto-detect from CloudNet

# Database (LOBBY mode only)
database:
  enabled: true
  type: "postgresql"           # postgresql | mysql | mariadb | mongodb
  host: "localhost"
  port: 5432
  database: "minigame_stats"
  username: "postgres"
  password: "password"

# Hologram (LOBBY mode only)
hologram-duration: 30          # seconds, 0 = permanent until next game
hologram-locations:
  - world: "world"
    x: 0.5
    y: 65.0
    z: 0.5

hologram-header:
  - "&6&l★ Game Over ★"
  - "&eServer: &f{game_name}"
  - "&aWinner: &f{winner}"
  - "&bPlayers: &f{player_count}"
  - "&7------- Player Stats -------"
hologram-player-line: "&f{player_name}  &cK:{kills} &4D:{deaths} &eA:{assists} &bS:{score}"
hologram-footer:
  - "&7----------------------------"
```

## Usage

### 1. Game End Command

```
/gameend <winner> [playerCount] [player:uuid:kills:deaths:assists:score ...]
```

**Examples:**

```
/gameend Steve
/gameend Steve 8
/gameend Steve 2 Steve:uuid1:5:2:3:100 Alex:uuid2:3:4:1:60
```

Permission: `minigamestatistic.gameend` (default: op)

### 2. Java API (for other plugins)

```java
import net.minegate.plugin.miniGameStatistic.api.GameEndAPI;
import net.minegate.plugin.miniGameStatistic.model.PlayerMatchStatistic;

// Simple (no per-player stats):
GameEndAPI.endGame("Steve", 8);

// With per-player stats:
List<PlayerMatchStatistic> stats = List.of(
    new PlayerMatchStatistic("Steve", uuid, 5, 2, 3, 100),
    new PlayerMatchStatistic("Alex",  uuid, 3, 4, 1, 60)
);
GameEndAPI.endGame("Steve", 8, stats);
```

### 3. What Happens When Game Ends

On the **GAME** server:
1. Statistics are sent to the lobby server via CloudNet `ChannelMessage`
2. After `teleport-delay` seconds, all online players are sent to `lobby-server`

On the **LOBBY** server:
1. Statistics are received and saved to the configured database (if enabled)
2. DecentHolograms are created at all configured locations showing match results
3. A chat broadcast notifies all lobby players

## Database Schema

### SQL (PostgreSQL / MySQL)

```sql
CREATE TABLE game_statistics (
    match_id VARCHAR(64) PRIMARY KEY,
    game_name VARCHAR(128) NOT NULL,
    winner VARCHAR(64),
    player_count INT NOT NULL,
    timestamp BIGINT NOT NULL
);

CREATE TABLE player_match_statistics (
    id SERIAL PRIMARY KEY,
    match_id VARCHAR(64) NOT NULL,
    player_name VARCHAR(64) NOT NULL,
    player_uuid VARCHAR(64),
    kills INT NOT NULL DEFAULT 0,
    deaths INT NOT NULL DEFAULT 0,
    assists INT NOT NULL DEFAULT 0,
    score INT NOT NULL DEFAULT 0
);
```

### MongoDB

Collection: `game_statistics`

```json
{
    "match_id": "uuid",
    "game_name": "SkyWars-1",
    "winner": "Steve",
    "player_count": 8,
    "timestamp": 1234567890,
    "players": [
        { "player_name": "Steve", "player_uuid": "...", "kills": 5, "deaths": 2, "assists": 3, "score": 100 }
    ]
}
```

## Building

```bash
mvn clean package
```

Output: `target/MiniGamePlugin-1.0-SNAPSHOT.jar`

## Running Unit Tests

```bash
# Run all tests
mvn test

# Run only model tests
mvn test -Dtest="net.minegate.plugin.miniGameStatistic.model.*"

# Run only command tests
mvn test -Dtest="net.minegate.plugin.miniGameStatistic.command.*"

# Run only database tests
mvn test -Dtest="net.minegate.plugin.miniGameStatistic.database.*"

# Run a single test class
mvn test -Dtest="GameStatisticTest"

# Run with verbose output
mvn test -X
```

## Installation

1. Build the JAR: `mvn clean package`
2. Copy `target/MiniGamePlugin-1.0-SNAPSHOT.jar` to the `plugins/` folder
3. Install DecentHolograms on the lobby server
4. Configure `config.yml` (set mode, server names, database, hologram locations)
5. Restart the server

## CloudNet API Reference

Based on [TalexCK/GameVoting](https://github.com/TalexCK/GameVoting) implementation patterns.

## License

MIT License
