# Halo Mod — Public API

## `HaloCommandInterceptor`

**Package:** `network.azusake.halo.client`

`HaloCommandInterceptor` is the abstraction layer for intercepting `/halo` commands before they reach the server. It isolates the core phase-tracking / local-execution logic from the loader-specific hook mechanism.

### Interface

```java
public interface HaloCommandInterceptor {
    void register();
    boolean isRegistered();
}
```

### Contract

Every implementation **must** follow this flow in each command executor:

1. Call `HaloPhaseTracker.getInstance().shouldIntercept()`.
2. If `true` (LOCAL phase — server without the mod):
   - Call `HaloLocalCommandHandler.handle(commandString)`.
   - Display the returned feedback on the local chat HUD.
   - **Do not** forward the command to the server.
3. If `false` (singleplayer or MULTIPLAYER phase — server with the mod):
   - Forward the raw command string to the server (e.g. `client.getNetworkHandler().sendCommand(cmd)`).

### Why This Layer Exists

- The core classes (`HaloPhaseTracker`, `HaloLocalCommandHandler`, `HaloLocalManager`) contain **zero** loader-specific code.
- Mixin-based interception proved fragile across Yarn mapping versions.
- This interface lets each loader (Fabric, NeoForge, …) plug in its own command-registration API.

### Fabric Implementation

`FabricHaloCommandInterceptor` (same package) registers `/halo` via `ClientCommandRegistrationCallback.EVENT`.

### Porting to Another Loader

1. Implement `HaloCommandInterceptor` using that loader's client-command API.
2. Call `register()` during client initialisation.
3. No core logic files need to be changed.

---

## Lifecycle Hooks

### `HaloPhaseTracker`

| Method | Purpose |
|--------|---------|
| `getPhase()` | `LOCAL` or `MULTIPLAYER` |
| `transitionToMultiplayer()` | Called when `halo:hello` arrives |
| `resetToLocal()` | Called on disconnect |
| `shouldIntercept()` | Singleplayer safety net — never `true` for integrated server |

### `HaloLocalManager`

Data persisted to `config/halo-azusake/halo_local_halos.json` on every mutation. Server keys are stable `hostString:port` strings.

| Method | Purpose |
|--------|---------|
| `showHalo(serverKey, uuid, defId)` | Record a halo |
| `hideHalo(serverKey, uuid)` | Remove a halo |
| `getHalo(serverKey, uuid)` | Look up |
| `getHalosForServer(serverKey)` | All UUIDs for rendering |
| `clearServer(serverKey)` | Disconnect cleanup |
| `serverKeyFromAddress(address)` | Stable key from `InetSocketAddress` |

### `HaloLocalCommandHandler`

```java
static String handle(String command)
```

Supported: `list`, `dump`, `show @s <def>`, `hide @s`, `config <p> <v>`, `reload`, `active`.
`show`/`hide` strictly require `@s`.

---

## Network Channels

| Channel | Direction | Purpose |
|---------|-----------|---------|
| `halo:sync` | S2C | Full state snapshot on join |
| `halo:update` | S2C | Incremental attach/remove |
| `halo:defs_report` | C2S | Client reports local definition IDs |
| `halo:hello` | S2C | Handshake — server has the mod installed |
