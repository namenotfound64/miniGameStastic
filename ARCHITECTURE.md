# Architecture and Flow Documentation

This document explains the architecture and data flow of the MiniGameStatistic plugin.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         CloudNet v4                              │
│  ┌─────────────┐      ┌─────────────┐      ┌─────────────┐     │
│  │   Proxy-1   │      │  BedWars-1  │      │   Lobby-1   │     │
│  │             │◄─────┤   (GAME)    │──────►│  (LOBBY)    │     │
│  │             │      │             │      │             │     │
│  └─────────────┘      └─────────────┘      └─────────────┘     │
│         │                    │                     │            │
│         │                    │                     │            │
│    send command      ChannelMessage       Broadcast stats      │
└─────────────────────────────────────────────────────────────────┘
```

## Component Architecture

### 1. CloudNetAPI (`api/CloudNetAPI.java`)

**Purpose**: Wrapper around CloudNet v4 Driver API

**Key Features**:
- Service management and lookup
- Command execution on CloudNet services
- Based on TalexCK/GameVoting implementation

**Methods**:
```java
- initialize(): Initialize the API
- getInstance(): Get singleton instance
- getServiceByName(String): Find a service by name
- executeServiceCommand(String, String): Run command on a service
```

### 2. GameStatistic Model (`model/GameStatistic.java`)

**Purpose**: Data transfer object for game statistics

**Fields**:
```java
- gameName: String (name of the game server)
- winner: String (name of the winning player)
- playerCount: int (number of players who participated)
- timestamp: long (when the game ended)
```

### 3. Main Plugin Class (`MiniGameStatistic.java`)

**Purpose**: Core plugin logic with dual-mode operation

**Modes**:

#### GAME Mode (for minigame servers)
- Registers GameEndListener for game events
- Handles game end by:
  1. Creating statistics object
  2. Scheduling player teleportation (after delay)
  3. Sending statistics to lobby via ChannelMessage

#### LOBBY Mode (for lobby servers)
- Registers CloudNet ChannelMessage listener
- Receives statistics messages
- Broadcasts statistics to all lobby players

### 4. GameEndListener (`listener/GameEndListener.java`)

**Purpose**: Listen for game end events and trigger statistics collection

**Features**:
- Example implementation using PlayerQuitEvent
- Provides `handleGameEnd()` method for integration

### 5. GameEndCommand (`command/GameEndCommand.java`)

**Purpose**: Manual game end triggering for testing

**Usage**: `/gameend <winner> [playerCount]`

## Data Flow

### Game End Flow (GAME mode)

```
1. Game Ends
   └─> Plugin.handleGameEnd(winner, playerCount)
       │
       ├─> Create GameStatistic object
       │   └─> gameName = current service name (from CloudNet)
       │       winner = provided
       │       playerCount = provided
       │       timestamp = System.currentTimeMillis()
       │
       ├─> Schedule Teleportation (after delay)
       │   └─> For each online player:
       │       └─> CloudNetAPI.executeServiceCommand(
       │           proxyService,
       │           "send <player> <lobbyServer>"
       │           )
       │
       └─> Send Statistics via ChannelMessage
           └─> Channel: "minigame_statistics"
               Message: "game_end"
               Target: lobbyServer
               Data: [gameName, winner, playerCount, timestamp]
```

### Statistics Reception Flow (LOBBY mode)

```
1. ChannelMessageReceiveEvent triggered
   └─> Check if channel == "minigame_statistics"
       └─> Check if message == "game_end"
           └─> Read data from buffer:
               - gameName: String
               - winner: String
               - playerCount: int
               - timestamp: long
               │
               └─> Create GameStatistic object
                   └─> Schedule on main thread:
                       └─> broadcastStatistics()
                           └─> Bukkit.broadcastMessage(
                               formatted statistics message
                               )
```

## CloudNet Integration

### Player Teleportation

The plugin uses CloudNet's proxy command system to teleport players:

```
Game Server → CloudNetAPI → Proxy Service
                            └─> Execute: "send <player> <lobby>"
                                └─> Player teleported to lobby
```

**Why via Proxy?**
- Direct player teleportation requires BungeeCord/Velocity API
- CloudNet proxy services support standard proxy commands
- More reliable across different proxy implementations

### ChannelMessage System

CloudNet's ChannelMessage allows cross-service communication:

```java
// Sending (from GAME server)
ChannelMessage.builder()
    .channel("minigame_statistics")  // Custom channel name
    .message("game_end")             // Message type
    .json(true)                      // Use JSON format
    .buffer()                        // Data buffer
        .writeString(gameName)       // Write data
        .writeString(winner)
        .writeInt(playerCount)
        .writeLong(timestamp)
    .build()
    .targetService(lobbyServer)      // Target specific service
    .build()
    .send();

// Receiving (on LOBBY server)
@EventListener
public void handleChannelMessage(ChannelMessageReceiveEvent event) {
    if ("minigame_statistics".equals(event.channelMessage().channel())) {
        String gameName = event.channelMessage().content().readString();
        // ... read other data
    }
}
```

## Timing Diagram

```
Time    Game Server (BedWars-1)         Proxy (Proxy-1)         Lobby (Lobby-1)
─────────────────────────────────────────────────────────────────────────────────
T+0s    Game ends
        │
        ├─ Create statistics
        ├─ Send ChannelMessage ────────────────────────────────► Receive message
        │                                                         │
        └─ Schedule teleport (5s)                                ├─ Parse data
                                                                  └─ Broadcast to players
                                                                     "BedWars-1 ended!
                                                                      Winner: Steve..."
T+5s    For each player:
        send teleport command ──────────► Execute "send" command
                                          │
                                          └─ Move player ─────────► Player arrives
                                                                     in lobby
```

## Configuration Impact

### mode: GAME
- **Enables**: GameEndListener, GameEndCommand, teleportation, statistics sending
- **Disables**: ChannelMessage listening
- **Required**: lobby-server, proxy-service, teleport-delay

### mode: LOBBY
- **Enables**: ChannelMessage listening, statistics broadcasting
- **Disables**: Game event handling, teleportation
- **Required**: None (just receives messages)

## Error Handling

### Service Not Found
```java
try {
    api.executeServiceCommand(serviceName, command);
} catch (IllegalArgumentException e) {
    getLogger().severe("Service not found: " + serviceName);
}
```

### ChannelMessage Failure
```java
try {
    ChannelMessage.builder()...send();
} catch (Exception e) {
    getLogger().severe("Failed to send statistics: " + e.getMessage());
    e.printStackTrace();
}
```

### CloudNet Not Available
```java
try {
    CloudNetAPI.initialize();
} catch (Exception e) {
    getLogger().severe("Failed to initialize CloudNet API");
    getServer().getPluginManager().disablePlugin(this);
}
```

## Performance Considerations

### Asynchronous Operations
- ChannelMessage sending is non-blocking
- CloudNet command execution is asynchronous
- Statistics broadcasting uses Bukkit scheduler for thread safety

### Memory Usage
- GameStatistic objects are lightweight (< 100 bytes)
- No persistent storage - messages are fire-and-forget
- EventListener objects are registered once at startup

### Network Traffic
- ChannelMessage: ~100-200 bytes per game end
- Proxy commands: ~50 bytes per player
- Minimal overhead for typical minigame scenarios

## Security Considerations

### Permission System
- `/gameend` command requires `minigamestatistic.gameend` permission
- Default: op only
- Prevents abuse of manual game ending

### Input Validation
- Player names are passed directly (trusted from Bukkit API)
- Player count is validated (must be non-negative)
- Service names from config (trusted source)

### CloudNet Integration
- Uses CloudNet's built-in authentication
- No direct network connections
- Relies on CloudNet's security model
