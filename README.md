# MiniGameStatistic Plugin

A Minecraft Paper plugin for **CloudNet v4** (RC16) that:

1. **Auto-collects player stats** from vanilla Minecraft scoreboards (kills, deaths, assists, score)
2. **Teleports players** back to the lobby server when a minigame ends (via command or API)
3. **Persists match data** to a database (PostgreSQL / MySQL / MariaDB / MongoDB)
4. **Displays a hologram** on the lobby server showing the last match's player statistics using DecentHolograms
5. **Supports multi-round games** — accumulate stats across rounds with configurable SUM or MAX merge

---

## Requirements

| Component | Version |
|---|---|
| Minecraft Server | Paper 1.20.6+ |
| Java | 21+ |
| CloudNet | 4.0.0-RC16 |
| DecentHolograms | 2.8.6+ (lobby server only) |
| Database (optional) | PostgreSQL 12+ / MySQL 8.0+ / MariaDB 10.5+ / MongoDB 5.0+ |

## Project Structure

```
src/main/java/net/minegate/plugin/miniGameStatistic/
  MiniGameStatistic.java            # Main plugin class (GAME & LOBBY modes)
  api/
    CloudNetAPI.java                # CloudNet v4 Driver API wrapper
    GameEndAPI.java                 # Public API for external plugins to trigger game end
  command/
    GameEndCommand.java             # /gameend command — auto-attaches scoreboard data
    SaveScoreboardCommand.java      # /savescoreboard command — snapshot per round
  database/
    GameStatisticRepository.java    # Repository interface
    SqlRepository.java             # PostgreSQL / MySQL / MariaDB implementation
    MongoRepository.java           # MongoDB implementation
    DatabaseManager.java           # Factory & singleton holder
  listener/
    GameEndListener.java           # Bukkit event listener (example)
  model/
    GameStatistic.java             # Match-level data model
    PlayerMatchStatistic.java      # Per-player data model
  scoreboard/
    ScoreboardTracker.java         # Reads vanilla scoreboards, SUM/MAX merge across rounds
```

## Configuration (`config.yml`)

```yaml
mode: GAME                    # GAME or LOBBY
lobby-server: "Lobby-1"
proxy-service: "Proxy-1"
teleport-delay: 5
game-server-name: "auto"      # auto-detect from CloudNet

# ===== Scoreboard Tracking (GAME mode) =====
scoreboard:
  enabled: true
  merge-mode: "SUM"           # SUM = add across rounds, MAX = keep highest per field
  objectives:
    kills: "game_kills"       # vanilla scoreboard objective names
    deaths: "game_deaths"
    assists: "game_assists"
    score: "game_score"

# ===== Database (LOBBY mode) =====
database:
  enabled: true
  type: "postgresql"           # postgresql | mysql | mariadb | mongodb
  host: "localhost"
  port: 5432
  database: "minigame_stats"
  username: "postgres"
  password: "password"

# ===== Hologram (LOBBY mode) =====
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

## Commands

| Command | Permission | Description |
|---|---|---|
| `/gameend <winner> [count] [stats...]` | `minigamestatistic.gameend` | End the game. Auto-attaches scoreboard data if no manual stats given. |
| `/savescoreboard` | `minigamestatistic.savescoreboard` | Snapshot current scoreboard for all online players. Call after each round. |

## Usage

### Scoreboard-Based Workflow (Recommended)

Your minigame creates vanilla scoreboard objectives (e.g. via command blocks or a plugin):

```
/scoreboard objectives add game_kills dummy
/scoreboard objectives add game_deaths dummy
/scoreboard objectives add game_assists dummy
/scoreboard objectives add game_score dummy
```

The plugin reads these objectives automatically. You just need to configure the objective names in `config.yml`.

**Single-round game:**

```
Game ends → /gameend Steve
  → Plugin auto-snapshots scoreboard, sends data to lobby, teleports players
```

**Multi-round game (same server):**

```
Round 1 ends → /savescoreboard          (saves round 1 data)
Round 2 ends → /savescoreboard          (merges round 2 via SUM or MAX)
Round 3 ends → /gameend Steve           (final snapshot + send + teleport + clear)
```

### Manual Stats (Alternative)

You can still pass stats manually on the command line:

```
/gameend Steve 2 Steve:uuid1:5:2:3:100 Alex:uuid2:3:4:1:60
```

Manual stats take priority over scoreboard data.

### Java API (for other plugins)

```java
import net.minegate.plugin.miniGameStatistic.api.GameEndAPI;
import net.minegate.plugin.miniGameStatistic.model.PlayerMatchStatistic;

// Simple — scoreboard data is auto-attached:
GameEndAPI.endGame("Steve", 8);

// With explicit per-player stats:
List<PlayerMatchStatistic> stats = List.of(
    new PlayerMatchStatistic("Steve", uuid, 5, 2, 3, 100),
    new PlayerMatchStatistic("Alex",  uuid, 3, 4, 1, 60)
);
GameEndAPI.endGame("Steve", 8, stats);
```

### What Happens When Game Ends

On the **GAME** server:
1. Scoreboard data is snapshot and merged (if enabled)
2. Statistics are sent to the lobby server via CloudNet `ChannelMessage`
3. After `teleport-delay` seconds, all online players are sent to `lobby-server`
4. Scoreboard tracker is cleared for the next session

On the **LOBBY** server:
1. Statistics are received and saved to the configured database (if enabled)
2. DecentHolograms are created at all configured locations showing match results
3. A chat broadcast notifies all lobby players

## Merge Modes

| Mode | Behavior | Use Case |
|---|---|---|
| `SUM` | Adds values from each round together | "Total kills across all rounds" |
| `MAX` | Keeps the highest value from any single round | "Best score in any single round" |

Each stat field (kills, deaths, assists, score) is merged independently.

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

## Deployment on CloudNet

### Build

```bash
mvn clean package -DskipTests
```

Output: `target/MiniGamePlugin-1.1.0.jar`

### Deploy

Place the same JAR into both **Game** and **Lobby** templates:

```
CloudNet/local/templates/
├── Lobby/default/plugins/
│   ├── MiniGamePlugin-1.1.0.jar
│   └── DecentHolograms-x.x.x.jar       ← lobby only
└── SkyWars/default/plugins/
    └── MiniGamePlugin-1.1.0.jar
```

- **Database drivers** (PostgreSQL, MySQL, MongoDB) are shaded into the JAR — no extra files needed.
- **CloudNet Driver/Bridge** are provided at runtime by CloudNet — no extra files needed.
- **DecentHolograms** only needs to be on the Lobby server.

### Configure

**Game server** (`config.yml`):

```yaml
mode: GAME
lobby-server: "Lobby-1"
proxy-service: "Proxy-1"
scoreboard:
  enabled: true
  merge-mode: "SUM"
  objectives:
    kills: "game_kills"
    deaths: "game_deaths"
    assists: "game_assists"
    score: "game_score"
```

**Lobby server** (`config.yml`):

```yaml
mode: LOBBY
database:
  enabled: true
  type: "postgresql"
  host: "db.example.com"
  port: 5432
  database: "minigame_stats"
  username: "postgres"
  password: "secret"
hologram-locations:
  - world: "world"
    x: 0.5
    y: 65.0
    z: 0.5
```

### Database Preparation

- **PostgreSQL / MySQL**: Create the database (`CREATE DATABASE minigame_stats;`). Tables are auto-created on first run.
- **MongoDB**: No preparation needed. Collections are auto-created.

## Running Unit Tests

```bash
# Run all 30 tests
mvn test

# Run only model tests
mvn test -Dtest="net.minegate.plugin.miniGameStatistic.model.*"

# Run only command tests
mvn test -Dtest="net.minegate.plugin.miniGameStatistic.command.*"

# Run only database tests
mvn test -Dtest="net.minegate.plugin.miniGameStatistic.database.*"

# Run only scoreboard tracker tests
mvn test -Dtest="net.minegate.plugin.miniGameStatistic.scoreboard.*"

# Run a single test class
mvn test -Dtest="ScoreboardTrackerTest"
```

## Data Flow

```
[Game Server]                    [CloudNet]                [Lobby Server]
     |                               |                          |
  Scoreboard auto-tracks data        |                          |
     |                               |                          |
  /savescoreboard (per round)        |                          |
     |                               |                          |
  /gameend Steve                     |                          |
     |── ChannelMessage ──────────>  |  ──────────────────>     |
     |   (stats + player data)       |                     Receive stats
     |                               |                          |── Save to DB (async)
     |<── send command (delayed) ── Proxy                       |── Create holograms
  Players teleported to Lobby        |                          |── Broadcast chat
```

## Version History

### v1.1.0

- Added vanilla scoreboard integration — auto-read `kills`, `deaths`, `assists`, `score` from configurable objectives
- Added `/savescoreboard` command for multi-round data snapshots
- Added `merge-mode` config: `SUM` (accumulate) or `MAX` (best round)
- `/gameend` now auto-attaches scoreboard data when no manual stats given
- Added multi-database support: PostgreSQL, MySQL/MariaDB, MongoDB
- Added configurable multi-location hologram display with per-player stats
- Added public Java API (`GameEndAPI`) for external plugin integration
- 30 unit tests covering models, commands, database manager, and scoreboard tracker

### v1.0.0

- Initial release
- Basic game end command and CloudNet channel messaging
- Player teleportation via proxy
- Simple hologram display

## CloudNet API Reference

Based on [CloudNet 4.0.0-RC16](https://github.com/CloudNetService/CloudNet/releases) API patterns and [TalexCK/GameVoting](https://github.com/TalexCK/GameVoting) implementation.

## License

MIT License
