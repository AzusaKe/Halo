# Task 03: Networking

## Agent Type: `general-purpose`

## Goal
Implement client-server synchronization so that halo assignments made on the server are visible to all relevant clients. After this task, when an operator runs `/halo apply <player> <def>`, that player and all nearby players see the halo state synced.

## Dependencies
- `task-02-server-core` complete (HaloManager, commands working server-side)

## Input
- `HaloManager` with `setHalo()`/`removeHalo()` hooks
- `HaloCommand` with command registration
- `HaloInstance` data class
- Fabric Networking API v1

## Task

### 1. Define Packets (`network/` package — create if needed, or put in top-level)

**`HaloSyncS2CPacket.java`** — sent when a halo is created or updated:
- Fields: `UUID entityUuid`, `Identifier definitionId`, `boolean needsSnap`
- Use `PacketCodec` with `Identifier` codec + UUID codec + boolean
- Register with `PayloadTypeRegistry.playS2C()`

**`HaloRemoveS2CPacket.java`** — sent when a halo is removed:
- Fields: `UUID entityUuid`
- Register with `PayloadTypeRegistry.playS2C()`

### 2. Implement `HaloNetwork.java`

Central networking utility:

```java
public class HaloNetwork {
    public static final Identifier HALO_SYNC_ID = new Identifier("halo", "halo_sync");
    public static final Identifier HALO_REMOVE_ID = new Identifier("halo", "halo_remove");

    public static void registerS2CPackets() {
        // Register payload types
        // Register global receivers on client side
    }

    // Send to all players tracking an entity
    public static void sendHaloSyncToTrackers(Entity entity, HaloInstance halo) { ... }

    // Send to a specific player (for initial join sync)
    public static void sendHaloSyncToPlayer(ServerPlayerEntity player, HaloInstance halo) { ... }

    public static void sendHaloRemoveToTrackers(Entity entity) { ... }
}
```

### 3. Client-Side Receivers (`HaloModClient` or new `HaloClientNetwork.java`)

On receiving `HaloSyncS2CPacket`:
- Create or update a client-side `HaloInstance` in a client-side `HaloManager` (or directly in `HaloRenderer`'s state map)
- Set `needsSnap = true` if indicated

On receiving `HaloRemoveS2CPacket`:
- Remove the halo from client state

### 4. Server-Side Sync Points

Modify `HaloManager.setHalo()`:
- After creating/updating a halo, call `HaloNetwork.sendHaloSyncToTrackers(entity, halo)`

Modify `HaloManager.removeHalo()`:
- After removing, call `HaloNetwork.sendHaloRemoveToTrackers(entity)`

Handle player join: When a player logs in, iterate all active halos and sync to that player. Register `ServerPlayConnectionEvents.JOIN`.

Handle entity start tracking: Use Fabric API's `ServerEntityWorldChangeEvents` or `EntityTrackingEvents` to sync halos when an entity enters a player's tracking range. If Fabric 1.20.1 API doesn't have this, simplify: sync to all players each tick for visible entities (trading bandwidth for simplicity).

### 5. Verify

- Launch integrated server with two clients (or one client + check packet logs)
- Client A runs `/halo apply @p halo:ring_default`
- Client B (another player) should receive the packet (verify via debug log in client receiver)
- `/halo remove @p` — Client B receives remove packet
- Reconnect — halo state persists for Client A (they resync on join)

## Output Artifacts
- `network/HaloSyncS2CPacket.java` (or top-level)
- `network/HaloRemoveS2CPacket.java`
- `network/HaloNetwork.java`
- Updated `HaloManager.java` with sync calls
- Updated `HaloMod.java` with networking registration + join event
- Updated `HaloModClient.java` with client receiver registration
