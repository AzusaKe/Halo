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

### `HaloModConfig` / `HaloModConfigStore`

Mod-level config file `config/halo-azusake/halo_mod_config.json` holds the
low-level command-system configuration (currently the permission level required
by `/halo`). It is completely separate from the runtime `/halo config` tuning
(`HaloConfig`). Missing or blank files are written with defaults at startup;
out-of-range values are clamped to 0–4; corrupt JSON falls back to defaults with
a warning; unknown keys are ignored (backwards compatible).

| Method | Purpose |
|--------|---------|
| `HaloModConfigStore.load()` | Load at startup (called once during mod init) |
| `HaloModConfigStore.getPermissionLevel()` | Permission level required by the `/halo` command tree (default 2, range 0–4) |
| `HaloModConfig.getCommandPermissionLevel()` | Current permission level |
| `HaloModConfig.setCommandPermissionLevel(int)` | Set the permission level (clamped to 0–4) |

---

## Custom Head Anchor

Halo's anchor computation is abstracted behind a head-anchor provider
interface. Other mods can register their own providers during client
initialisation to take over (or override) the head anchor of any entity, e.g.
to match a custom renderer / skeletal animation system.

### Data flow & call contract

- `EntityAnchorProvider.resolve(LivingEntity, float tickDelta)` is called by
  Halo **once per render frame on the render thread** (not per tick). It
  receives the partial-tick progress `tickDelta` (0–1; may exceed 1 at low
  frame rates).
- The provider is responsible for interpolating between the previous and
  current tick state and returns a full **6-DOF** `HeadAnchor`:
  - `Vec3d headCenter` — head center in world coordinates (3 DOF)
  - `float yaw` / `float pitch` / `float roll` — head orientation (3 DOF,
    degrees, MC convention)
- Mods only provide the **head**'s 6 DOF; Halo still computes the halo's own
  position damping, offset and orientation modes internally.

### Frame-ordering and null contract

- **Frame ordering**: Halo may invoke `resolve` *before* the current frame's
  camera/head orientation has been computed by the provider's animation or
  camera system. The provider must **cache the previous frame's
  `HeadAnchor`** and return it whenever the current-frame input is not ready,
  instead of returning partial or default data.
- **Never return `null`**: providers must not return `null`, and every
  component must be a finite number (no NaN). Halo (the resolver) treats
  `null` as a provider bug: it logs an error and falls back to
  `FallbackAnchorProvider` so the render pipeline never crashes.

### Registration

Listen to `AnchorProviderSetupEvent` from your own `ClientModInitializer`.
Halo fires the event **after every client entrypoint has run (at the end of
the first client tick)**, so your listener is always observed regardless of
mod load order:

```java
import network.azusake.halo.api.AnchorProviderSetupEvent;
import network.azusake.halo.api.HeadAnchor;
import network.azusake.halo.api.EntityAnchorProvider;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

public class MyModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AnchorProviderSetupEvent.EVENT.register(registry ->
            registry.register(LivingEntity.class, new MyHeadProvider()));
    }
}

final class MyHeadProvider implements EntityAnchorProvider {
    @Override
    public HeadAnchor resolve(LivingEntity entity, float tickDelta) {
        Vec3d headCenter = ...; // custom head center (world coordinates)
        float yaw = ...;        // head orientation (degrees)
        float pitch = ...;
        float roll = ...;       // pure-animation mods may supply roll freely
        return new HeadAnchor(headCenter, yaw, pitch, roll);
    }
}
```

### Registry semantics (`EntityAnchorProviderRegistry`)

| Scenario | How | Notes |
|----------|-----|-------|
| Specific entity type | `register(ZombieEntity.class, provider)` | Exact class wins; registering a superclass affects all subclasses |
| Specific individual | `register(uuid, provider)` | UUID hit outranks any class registration |
| Arbitrary predicate (NBT/team/state) | Delegation pattern | Capture the old provider before registering, delegate on non-match |

Delegation example (zombies carrying a specific NBT flag only):

```java
EntityAnchorProvider prev = registry.getProvider(ZombieEntity.class); // captured before registering, never null
registry.register(ZombieEntity.class, (entity, tickDelta) ->
    entity.getNbt().getBoolean("my:special_head")
        ? myAnchor(entity, tickDelta)
        : prev.resolve(entity, tickDelta));
```

Lookup order: `UUID → exact class → superclass chain → FallbackAnchorProvider`.
For both class and UUID keys, the last registration wins.

### Default behaviour

- Players use `PlayerAnchorProvider` (driven by
  `data/halo/entity_anchors/player.json`); other entities use
  `FallbackAnchorProvider` (height × 0.85 heuristic).
- Vanilla entity heads have no roll; the local player's head follows the
  camera, so its roll is inherited from the real camera (in 1.20.1 there is
  no `Camera.getRoll()`; the roll is recovered from `Camera.getRotation()`)
  and the halo head frame tilts rigidly with it.

### 6-DOF rotation convention

- Angles are in degrees; the roll sign follows MC's camera-roll convention
  (equivalent to `rotationYXZ(-yaw, pitch, roll)`, i.e. the Z component of
  Minecraft's camera quaternion).
- `roll=0` behaves identically to the old 5-DOF pipeline.

---

## Network Channels

| Channel | Direction | Purpose |
|---------|-----------|---------|
| `halo:sync` | S2C | Full state snapshot on join |
| `halo:update` | S2C | Incremental attach/remove |
| `halo:defs_report` | C2S | Client reports local definition IDs |
| `halo:hello` | S2C | Handshake — server has the mod installed |
