# MiniGameStatistic Plugin

A Minecraft Paper plugin for **CloudNet v4** (RC16) that:

1. **Auto-collects player stats** from vanilla Minecraft scoreboards with **fully dynamic fields** — configure any number of stat fields (kills, deaths, damage, heals, blocks, etc.)
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
    SqlRepository.java             # PostgreSQL / MySQL / MariaDB implementation (EAV schema)
    MongoRepository.java           # MongoDB implementation (nested document)
    DatabaseManager.java           # Factory & singleton holder
  listener/
    GameEndListener.java           # Bukkit event listener (example)
  model/
    GameStatistic.java             # Match-level data model
    PlayerMatchStatistic.java      # Per-player data model (dynamic Map<String,Integer>)
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
  sort-by: "score"            # which field to sort players by in hologram (optional)
  # Add ANY number of fields — each maps to a vanilla scoreboard objective
  objectives:
    kills: "game_kills"
    deaths: "game_deaths"
    assists: "game_assists"
    score: "game_score"
    # Examples of additional fields:
    # damage: "game_damage"
    # heals: "game_heals"
    # blocks_placed: "game_blocks"

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

# Placeholders: {game_name}, {winner}, {player_count}, {match_id}, {duration}
# Per-player line: {player_name} and any field name from objectives: {kills}, {damage}, etc.
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

## Dynamic Fields

The stat field system is fully dynamic. You are **not limited** to kills/deaths/assists/score — add any fields you need:

```yaml
objectives:
  kills: "game_kills"
  deaths: "game_deaths"
  damage_dealt: "game_damage"
  damage_taken: "game_damage_taken"
  heals: "game_heals"
  blocks_placed: "game_blocks"
  distance_walked: "game_distance"
```

All fields are automatically:
- **Tracked** from vanilla scoreboard objectives
- **Merged** across rounds (SUM or MAX)
- **Serialized** via CloudNet ChannelMessage
- **Stored** in the database (SQL uses EAV schema, MongoDB uses nested documents)
- **Displayed** in holograms via `{field_name}` placeholders

No code changes or schema migrations needed — just edit `config.yml` and restart.

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
/scoreboard objectives add game_damage dummy
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

### Manual Stats via Command

You can pass stats manually using key=value format:

```
/gameend Steve 2 Steve:uuid1:kills=5:deaths=2:damage=300 Alex:uuid2:kills=3:deaths=4:damage=150
```

Legacy positional format is also supported for backward compatibility:

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

// With dynamic per-player stats:
List<PlayerMatchStatistic> stats = List.of(
    new PlayerMatchStatistic("Steve", uuid, Map.of("kills", 5, "deaths", 2, "damage", 300)),
    new PlayerMatchStatistic("Alex",  uuid, Map.of("kills", 3, "deaths", 4, "damage", 150))
);
GameEndAPI.endGame("Steve", 8, stats);

// Legacy 4-field constructor still works:
new PlayerMatchStatistic("Steve", uuid, 5, 2, 3, 100);
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

Each stat field is merged independently according to the configured mode.

## Database Schema

### SQL (PostgreSQL / MySQL) — EAV Schema

Stat fields are stored in a flexible key-value table, so adding new fields requires no schema changes.

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
    player_uuid VARCHAR(64)
);

CREATE TABLE player_stat_fields (
    id SERIAL PRIMARY KEY,
    player_stat_id INT NOT NULL,
    field_name VARCHAR(64) NOT NULL,
    field_value INT NOT NULL DEFAULT 0
);
```

Example data in `player_stat_fields`:

| player_stat_id | field_name | field_value |
|---|---|---|
| 1 | kills | 5 |
| 1 | deaths | 2 |
| 1 | damage | 300 |
| 2 | kills | 3 |
| 2 | deaths | 4 |
| 2 | damage | 150 |

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
        {
            "player_name": "Steve",
            "player_uuid": "...",
            "stats": { "kills": 5, "deaths": 2, "damage": 300 }
        }
    ]
}
```

## Deployment on CloudNet

### Build

```bash
mvn clean package -DskipTests
```

Output: `target/MiniGamePlugin-1.2.0.jar`

### Deploy

Place the same JAR into both **Game** and **Lobby** templates:

```
CloudNet/local/templates/
├── Lobby/default/plugins/
│   ├── MiniGamePlugin-1.2.0.jar
│   └── DecentHolograms-x.x.x.jar       ← lobby only
└── SkyWars/default/plugins/
    └── MiniGamePlugin-1.2.0.jar
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
  sort-by: "score"
  objectives:
    kills: "game_kills"
    deaths: "game_deaths"
    assists: "game_assists"
    score: "game_score"
    damage: "game_damage"
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
hologram-player-line: "&f{player_name}  &cK:{kills} &4D:{deaths} &6DMG:{damage} &bS:{score}"
```

### Database Preparation

- **PostgreSQL / MySQL**: Create the database (`CREATE DATABASE minigame_stats;`). Tables are auto-created on first run.
- **MongoDB**: No preparation needed. Collections are auto-created.

## Running Unit Tests

```bash
# Run all 39 tests
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
  (any number of custom fields)      |                          |
     |                               |                          |
  /savescoreboard (per round)        |                          |
     |                               |                          |
  /gameend Steve                     |                          |
     |── ChannelMessage ──────────>  |  ──────────────────>     |
     |   (dynamic field key-value)   |                     Receive stats
     |                               |                          |── Save to DB (async, EAV)
     |<── send command (delayed) ── Proxy                       |── Create holograms
  Players teleported to Lobby        |                          |── Broadcast chat
```

## Version History

### v1.2.0

- **Dynamic stat fields** — no longer limited to kills/deaths/assists/score; add any number of fields in `config.yml`
- `PlayerMatchStatistic` refactored to use `Map<String, Integer>` internally (backward-compatible constructors preserved)
- `ScoreboardTracker` reads arbitrary objectives from config
- New `sort-by` config option to control hologram player sorting
- `/gameend` command supports new `field=value` format: `Steve:uuid:kills=5:damage=300`
- Hologram `{placeholder}` system now dynamically replaces any field name
- SQL database switched to EAV schema (`player_stat_fields` table) — no schema changes needed for new fields
- MongoDB stores stats as nested document per player
- CloudNet ChannelMessage serializes dynamic key-value pairs
- 39 unit tests covering dynamic fields, merge logic, and backward compatibility

### v1.1.0

- Added vanilla scoreboard integration — auto-read stats from configurable objectives
- Added `/savescoreboard` command for multi-round data snapshots
- Added `merge-mode` config: `SUM` (accumulate) or `MAX` (best round)
- `/gameend` now auto-attaches scoreboard data when no manual stats given
- Added multi-database support: PostgreSQL, MySQL/MariaDB, MongoDB
- Added configurable multi-location hologram display with per-player stats
- Added public Java API (`GameEndAPI`) for external plugin integration

### v1.0.0

- Initial release
- Basic game end command and CloudNet channel messaging
- Player teleportation via proxy
- Simple hologram display

## CloudNet API Reference

Based on [CloudNet 4.0.0-RC16](https://github.com/CloudNetService/CloudNet/releases) API patterns and [TalexCK/GameVoting](https://github.com/TalexCK/GameVoting) implementation.

## License

MIT License
