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
| `HaloModConfig.isExperimentalYsmAnchorEnabled()` | YSM 2.6.5 Head anchor switch (default `true`; set `false` to disable) |
| `HaloModConfig.getExperimentalYsmHeadLocalOffset()` | Head-local `[right, up, back]` offset in blocks; zero by default |

### Experimental YSM 2.6.5 compatibility

This integration supports only the NeoForge 26.1 release
`2.6.5-neoforge+mc26.1`. Existing configs receive the two fields automatically
on the next startup. The switch defaults to `true`; set it to `false` to disable
YSM capture. Restart the game after editing the config:

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

## Head Anchor API v2

### Developer quick start

Use the following sequence for an external renderer integration.

#### Add Halo to the build and loader metadata

Place the Halo 1.3.0 jar matching the Minecraft version and loader in the external mod's
`libs` directory. Compile against it, but do not bundle it into the external mod:

```groovy
dependencies {
    compileOnly files("libs/halo-<minecraft>-<loader>-1.3.0.jar")
}
```

Halo must be installed separately at runtime. For a required integration, declare a loader
dependency as well:

```json
{
  "depends": {
    "halo": ">=1.3.0"
  }
}
```

```toml
# Forge 1.20.1: META-INF/mods.toml
[[dependencies.your_mod_id]]
modId="halo"
mandatory=true
versionRange="[1.3.0,)"
ordering="AFTER"
side="CLIENT"

# NeoForge: META-INF/neoforge.mods.toml
[[dependencies.your_mod_id]]
modId="halo"
type="required"
versionRange="[1.3.0,)"
ordering="AFTER"
side="CLIENT"
```

For an optional integration, first use the loader's mod-presence check and isolate all direct
API references in a compatibility-only class. Do not load that class when Halo is absent.

#### Register one source and keep its handle

Create one source for the integration, not one per entity or frame:

```java
import java.util.UUID;
import network.azusake.halo.api.v2.AnchorPose;
import network.azusake.halo.api.v2.AnchorRotation;
import network.azusake.halo.api.v2.AnchorSource;
import network.azusake.halo.api.v2.AnchorVec3;
import network.azusake.halo.api.v2.HaloAnchorApi;

public final class HaloAnchorBridge implements AutoCloseable {
    private AnchorSource source;

    public void start() {
        if (source == null) {
            source = HaloAnchorApi.register("your_mod_id:final_head");
        }
    }

    public boolean submit(
        UUID entityUuid,
        double worldX, double worldY, double worldZ,
        double qx, double qy, double qz, double qw
    ) {
        AnchorSource active = source;
        return active != null && active.submit(entityUuid, new AnchorPose(
            new AnchorVec3(worldX, worldY, worldZ),
            new AnchorRotation(qx, qy, qz, qw)
        ));
    }

    @Override
    public void close() {
        AnchorSource active = source;
        source = null;
        if (active != null) {
            active.close();
        }
    }
}
```

The ID must match `[a-z0-9_.-]+:[a-z0-9_./-]+`. An invalid ID throws
`IllegalArgumentException`; an active duplicate throws `IllegalStateException`.
`close()` is idempotent, clears that source's cached captures immediately, and allows the
same ID to be registered again.

#### Submit at the final base-head transform

Call the bridge synchronously where the effective renderer has completed the current entity's
final visual head transform. This is usually immediately after the final head model or locator
transform. A renderer wrapper must submit after its own final adjustment.

Do not submit from a client tick, end-of-frame callback, worker thread, armour layer, or
unrelated feature model. Pass the UUID of the entity currently being rendered. The call must
remain inside Halo's entity-render scope; moving it to a later callback will make it return
`false`.

#### Convert renderer coordinates to the API basis

The position must be absolute world space in blocks, not model or camera/view space. If a
renderer exposes a view-relative final matrix, undo the view rotation and add the camera world
position:

```text
worldPosition = cameraWorldPosition + inverse(viewRotation) * viewPosition
worldRotation = inverse(viewRotation) * viewRotationOfHead
```

Extract an orthonormal rotation before creating the quaternion; scale and shear do not belong
in `AnchorRotation`. JOML quaternion components can be passed without reordering:

```java
bridge.submit(entityUuid, worldX, worldY, worldZ,
    worldRotation.x(), worldRotation.y(),
    worldRotation.z(), worldRotation.w());
```

#### Handle the result

`submit(...) == true` means this pose was accepted for the current main-camera entity pass.
`false` is expected for shadow/auxiliary or unknown passes, a wrong/null UUID, a null pose,
calls outside the render scope, or a closed source. A rejected submission never replaces the
last valid pose. Do not retry it later from outside the renderer; only rate-limit diagnostics
if expected third-person main-pass submissions continually return `false`.

Deferred renderers must submit during the actual deferred head draw, not when extraction is
queued. Halo retains an accepted capture for at most one main frame and rebases its
entity-relative position onto current interpolation. World changes, entity unload, and source
close invalidate captures. The local first-person player uses Halo's camera anchor instead of
an external entity submission.

Before release, test third-person movement, shaders on/off, entity unload, world changes, live
source close, and source re-registration.

Halo 1.3.0 embeds the same push-based API v2 in all eight target jars. Add the matching Halo jar as a `compileOnly` or `provided` dependency; there is no separate API artifact. API v1 has been removed.

The public package is `network.azusake.halo.api.v2` and uses only Java 17/JDK types:

```java
public final class HaloAnchorApi {
    public static AnchorSource register(String sourceId);
}

public interface AnchorSource extends AutoCloseable {
    boolean submit(UUID entityUuid, AnchorPose pose);
    @Override void close();
}

public record AnchorPose(AnchorVec3 position, AnchorRotation rotation) {}
public record AnchorVec3(double x, double y, double z) {}
public record AnchorRotation(double x, double y, double z, double w) {}
```

### Registration and submission

Register once during client initialization, then submit where the effective renderer has completed the entity's final head transform:

```java
private static final AnchorSource SOURCE = HaloAnchorApi.register("example:custom_head");

boolean accepted = SOURCE.submit(entityUuid, new AnchorPose(
    new AnchorVec3(worldX, worldY, worldZ),
    new AnchorRotation(qx, qy, qz, qw)
));
```

`sourceId` must be a unique lowercase `namespace:path`. Registering an active duplicate throws; after the idempotent `close()`, the id may be registered again. A source normally lives for the client process. World changes clear captures but keep registrations. Closing a source immediately removes its cached capture, and later submissions return `false`.

The last valid submission in one main-camera entity pass is the final rendered result. Renderer wrappers must submit after completing their own final transform. `submit` returns `true` only inside a main-camera entity render scope whose UUID matches. Shadow/auxiliary or unknown shader passes, wrong UUIDs, calls outside the scope, and closed sources return `false` and do not replace a valid anchor.

### Coordinates and rotation

- `AnchorVec3` is an absolute world position in blocks: `+X` east, `+Y` up, `+Z` south.
- `AnchorRotation` is an `(x,y,z,w)` quaternion. Its constructor normalizes finite non-zero input and rejects zero or non-finite values.
- Rotated local `+Y` is head-up, local `+Z` is head-forward, and entity-right is rotated local `-X`.

### Multiple passes, delayed frames, and fallback

Halo's safety gate combines the main camera root matrix, main frustum, current entity, and an Iris shadow-pass probe when available. If a detected render backend cannot be classified, submission is rejected with rate-limited diagnostics. Later Iris shadow or auxiliary passes do not advance the main frame and cannot replace the main anchor.

Captures survive for at most one main frame for deferred drawing. Positions are cached relative to the entity and rebased onto its current interpolated position, avoiding stale camera/entity offsets. World changes, entity unload, and source close invalidate captures immediately. Without an accepted capture, players use the configured pose fallback and other living entities use Vanilla height/yaw/pitch. The local first-person player always uses the camera anchor.

Halo registers `halo:vanilla`, `halo:emf`, and `halo:ysm` on supported YSM branches. Vanilla and EMF capture player heads only; YSM retains its existing all-living-entity scope. ETF remains a coexistence test target rather than a separate capture source.

## Network Channels

| Channel | Direction | Purpose |
|---------|-----------|---------|
| `halo:sync` | S2C | Full state snapshot on join |
| `halo:update` | S2C | Incremental attach/remove |
| `halo:defs_report` | C2S | Client reports local definition IDs |
| `halo:hello` | S2C | Handshake — server has the mod installed |
