# MiniGameStatistic Plugin

A Minecraft Paper plugin (version 1.20.1) for CloudNet v4 that automatically teleports players to a lobby server after a minigame ends and tracks game statistics.

## Features

1. **Automatic Teleportation**: When a minigame ends, teleport all players back to the lobby server (default "Lobby-1") after a configurable delay (default 5 seconds). Uses CloudNet v4 API by executing a "send" command on the proxy service.

2. **Statistics Tracking**: Collects game data (winner, player count) and sends it to the lobby server using CloudNet's `ChannelMessage`.

3. **Lobby Display**: On the lobby server, listens for these messages and broadcasts the statistics to all players.

## Project Structure

- **Maven**: Uses Maven for dependency management (`pom.xml`)
- **Dependencies**: 
  - Paper API (1.20.1)
  - CloudNet Driver (4.0.0-RC10)
  - CloudNet Bridge (4.0.0-RC10)
- **Configuration**: `config.yml` to toggle between "Lobby" and "Game" modes
- **CloudNetAPI**: Utility class for CloudNet service interaction (based on TalexCK/GameVoting)
- **Main Plugin**: Event handling and message passing

## Configuration

Edit `config.yml` to configure the plugin:

```yaml
# Plugin mode: "LOBBY" or "GAME"
mode: GAME

# Lobby server name (used in GAME mode to send statistics)
lobby-server: "Lobby-1"

# Proxy service name (used to execute send commands)
proxy-service: "Proxy-1"

# Delay in seconds before teleporting players
teleport-delay: 5

# Game server name (auto-detected if not set)
game-server-name: "auto"
```

## Usage

### Game Server Mode

1. Set `mode: GAME` in `config.yml`
2. When your minigame ends, call the game end handler:

```java
MiniGameStatistic plugin = (MiniGameStatistic) Bukkit.getPluginManager().getPlugin("MiniGameStatistic");
plugin.handleGameEnd("PlayerName", 10); // Winner name and player count
```

This will:
- Send statistics to the lobby server via CloudNet ChannelMessage
- After 5 seconds (configurable), teleport all players to the lobby

### Lobby Server Mode

1. Set `mode: LOBBY` in `config.yml`
2. The plugin will automatically listen for statistics messages
3. When statistics are received, they will be broadcast to all players in the lobby

## Building

```bash
mvn clean package
```

The compiled JAR will be in `target/MiniGamePlugin-1.0-SNAPSHOT.jar`

**Note**: This plugin must be run in a CloudNet v4 environment. The Paper API and CloudNet dependencies are provided at runtime by the server.

## Installation

1. Place the JAR file in the `plugins/` directory of your CloudNet service
2. Configure `config.yml` with appropriate mode and server names
3. Restart the service

## Implementation Details

### CloudNetAPI

The `CloudNetAPI` class provides a wrapper around CloudNet v4 Driver API, based on the implementation from [TalexCK/GameVoting](https://github.com/TalexCK/GameVoting). It handles:
- Service management
- Command execution for player teleportation

### Player Teleportation

Uses the CloudNet proxy service to execute `send <player> <server>` commands, similar to BungeeCord's player sending mechanism.

### Statistics Messaging

Uses CloudNet's `ChannelMessage` system on the `minigame_statistics` channel to send game end data from game servers to lobby servers.

## Requirements

- **Minecraft**: 1.20.1+
- **Java**: 17+
- **CloudNet**: 4.0.0-RC10+
- **Platform**: Paper/Spigot running on CloudNet

## License

MIT License
