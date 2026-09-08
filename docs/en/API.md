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

Mod-level config file `config/halo-azusake/halo_mod_config.json` holds command
permissions and experimental compatibility switches. It is completely separate from the runtime `/halo config` tuning
(`HaloConfig`). Missing or blank files are written with defaults at startup;
missing fields in an existing file are merged back without deleting unknown
keys; out-of-range values are clamped to 0–4; corrupt JSON falls back to defaults
with a warning.

| Method | Purpose |
|--------|---------|
| `HaloModConfigStore.load()` | Load at startup (called once during mod init) |
| `HaloModConfigStore.getPermissionLevel()` | Permission level required by the `/halo` command tree (default 2, range 0–4) |
| `HaloModConfig.getCommandPermissionLevel()` | Current permission level |
| `HaloModConfig.setCommandPermissionLevel(int)` | Set the permission level (clamped to 0–4) |
| `HaloModConfig.isExperimentalYsmAnchorEnabled()` | Experimental YSM 2.6.5 Head anchor switch (default `false`) |
| `HaloModConfig.getExperimentalYsmHeadLocalOffset()` | Head-local `[right, up, back]` offset in blocks; zero by default |

### Experimental YSM 2.6.5 compatibility

This integration supports only the Fabric 1.21.1 release
`2.6.5-fabric+mc1.21.1`. Existing configs receive the two experimental fields
automatically on the next startup; the switch still defaults to `false`.
Restart the game after editing the config:

```json
{
  "commandPermissionLevel": 2,
  "experimentalYsmAnchorEnabled": true,
  "experimentalYsmHeadLocalOffset": [0.0, 0.0, 0.0]
}
```

The offset is applied in the final local coordinate frame of YSM's `Head`
bone. A zero vector uses the model author's Head pivot directly. Capture applies
to players and other living entities whose render is replaced by YSM; non-living
entities are ignored. The local first-person player always uses the camera
anchor so Iris shadow/auxiliary passes cannot contaminate its position, while
third-person and other living entities continue to use YSM Head capture.
Missing or unsupported YSM versions, unusable Head hierarchies, and degenerate
matrices safely fall back to Halo's normal entity anchors. YSM is not a required
Halo dependency.

---

## Custom Head Anchor

Halo exposes a registry that resolves head-anchor providers by entity type or UUID. The details below describe the actual public API in this branch.

> Important: the eight branches share the same semantics, but they are not one cross-version binary API. An external mod must depend on the Halo build for the target branch and use that branch's Minecraft mapping types and loader registration mechanism.

### Data flow & call contract

`EntityAnchorProvider.resolve(entity, tickDelta)` is called once per render frame on the client render thread. The provider should return a non-null `HeadAnchor` whose components are finite; when current-frame data is not ready, it should retain the previous valid frame or use an appropriate Vanilla/fallback anchor.

The entity parameter type in this branch is `net.minecraft.entity.LivingEntity`.

The `HeadAnchor.headCenter` type in this branch is `net.minecraft.util.math.Vec3d`; yaw, pitch and roll are angles in degrees, following Minecraft conventions.

The lookup order is: exact UUID → exact entity class → superclass chain → fallback. For the same key, the last registration wins.

`getProvider(Class<?>)` returns a usable fallback provider when no dedicated provider is registered; callers should not treat it as a nullable API.

### Registration

Fabric branches register during client initialisation through Halo's Fabric-compatible event:

```java
import net.fabricmc.api.ClientModInitializer;
import network.azusake.halo.api.AnchorProviderSetupEvent;
import net.minecraft.entity.LivingEntity;

public final class MyModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AnchorProviderSetupEvent.EVENT.register(registry ->
            registry.register(LivingEntity.class, new MyHeadProvider()));
    }
}
```

The provider implementation has the following shape:

```java
import network.azusake.halo.api.EntityAnchorProvider;
import network.azusake.halo.api.HeadAnchor;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

final class MyHeadProvider implements EntityAnchorProvider {
    @Override
    public HeadAnchor resolve(LivingEntity entity, float tickDelta) {
        Vec3d headCenter = ...; // custom head center in world coordinates
        float yaw = ...;        // degrees
        float pitch = ...;      // degrees
        float roll = ...;       // degrees
        return new HeadAnchor(headCenter, yaw, pitch, roll);
    }
}
```

### Default behaviour and compatibility

- External providers only supply an anchor. The UUID, exact-class, superclass-chain and fallback rules do not change with the loader.
- Players use Halo's player anchor provider; an available EMF player capture may override the anchor for that frame, and unavailable capture falls back to the player provider.
- The current EMF compatibility code captures players only. Non-player entities do not use EMF capture and continue through the branch's existing YSM, Vanilla or fallback path. This is an intentional conservative policy.
- YSM compatibility and configuration are branch-specific and independent of EMF; consult the YSM section and configuration for the target branch.

### EMF/ETF compatibility

EMF compatibility is enabled by default and has no Halo configuration switch. The supported lower bound is EMF 3.1.1; no fixed upper bound is imposed, and the actual ABI is checked at runtime. ETF adds no separate capture code and is validated only for coexistence with EMF/ETF.

When the loaded EMF ABI is incompatible, Halo reports the following message in the log and chat:

> 当前Halo模组的EMF兼容代码无法再适用于加载版本的emf模组，请前往源码库汇报

### API v1 and a possible API v2

The current public API is v1. It exposes the Minecraft-mapped `LivingEntity` and vector type of each branch, so it is semantically consistent across branches but not source- or binary-identical across loaders and Minecraft versions.

A future API v2 can coexist with v1 and provide a more platform-neutral abstraction. Each branch would implement v2 through its own loader/version adapter and internal abstraction layer while preserving the same anchor semantics. This is a design direction, not an implemented API.

To preserve existing external mods and Halo's current compatibility packages, v2 should be additive or provide a v1 bridge. Removing or changing the v1 method descriptors or anchor units would require updates to the EMF/YSM adapter boundaries and could break already-compiled external mods. The EMF capture and ABI-detection layer, and ETF coexistence validation, can remain isolated behind those adapters.

### Registration advice

External mods should normally register a concrete entity class instead of unconditionally replacing `LivingEntity.class`. When wrapping an existing provider, capture it before registering the replacement and delegate for states that your provider does not handle. Do not assume that every entity has the same model-part hierarchy.

## Network Channels

| Channel | Direction | Purpose |
|---------|-----------|---------|
| `halo:sync` | S2C | Full state snapshot on join |
| `halo:update` | S2C | Incremental attach/remove |
| `halo:defs_report` | C2S | Client reports local definition IDs |
| `halo:hello` | S2C | Handshake — server has the mod installed |
