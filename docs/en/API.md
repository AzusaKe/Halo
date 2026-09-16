# Halo Mod — Public API

## Start here: integrate API v2

This guide targets **Halo 2.3.0, Minecraft 1.20.1 Fabric, Java 17**. Other integrated hosts can reuse the neutral API; older/frozen adapters do not automatically provide previews. API v2 supplies **head anchors for equipped halos**, not equipment or arbitrary geometry. First use `/halo list` and `/halo show @s <definition-id>` to equip a halo.

Read in order: [1. Dependencies](#dependency-setup) → [2. Model provider](#provider-routing) → [3. Preview panel](#preview-host-integration) → [4. API reference](#api-v2-reference) → [5. Verification](#integration-checks). [Other interfaces](#other-interfaces) follow the API v2 guide.

| Your integration | Route |
| --- | --- |
| A world entity/model renderer | [World submission](#world-anchor-api), after the shared setup |
| A renderer also used in previews | Reuse its source and [route each invocation](#provider-routing) |
| A panel drawing the real player with `InventoryScreen.drawEntity` | Call the vanilla helper; Halo automatically handles the player preview |
| Proxy entities, direct model draws or a custom camera | Implement an [independent preview host](#independent-preview-host) |

Providers need only `network.azusake.halo.api.v2`; hosts also use `core.runtime` and `core.render`. Preview-only integrations also start with the [shared dependency setup](#dependency-setup).

<a id="dependency-setup"></a>

## 1. Dependencies and client initialization

Place the Halo 2.3.0 jar matching the Minecraft version and loader in your mod's `libs` directory. Compile against it without bundling it into your mod:

```groovy
dependencies {
    modCompileOnly files("libs/halo-1.20.1-fabric-2.3.0+adapter.1.jar")
    modLocalRuntime files("libs/halo-1.20.1-fabric-2.3.0+adapter.1.jar")
}
```

This is Fabric Loom syntax: the production mod jar is remapped for development, and `modLocalRuntime` also loads it in `runClient`. Use the actual downloaded filename. A plain Java project using only neutral API types can use `compileOnly files(...)`; other loader toolchains need their own remapping setup. Do not use `include` or shade Halo/core, or install a separate API jar. The current supported release is [2.3.0 for 1.20.1 Fabric](https://github.com/AzusaKe/Halo/releases/tag/v2.3.0-fabric-1.20.1-adapter.1).

Halo must be installed separately at runtime. For a required integration, add this dependency to `fabric.mod.json`:

```json
{
  "depends": {
    "halo": ">=2.3.0"
  }
}
```

The following metadata is only a reference for **future/matching Forge or NeoForge adapters**, not a claim that a 2.3.0 build exists for them:

```toml
# Forge 1.20.1: META-INF/mods.toml
[[dependencies.your_mod_id]]
modId="halo"
mandatory=true
versionRange="[2.3.0,)"
ordering="AFTER"
side="CLIENT"

# NeoForge: META-INF/neoforge.mods.toml
[[dependencies.your_mod_id]]
modId="halo"
type="required"
versionRange="[2.3.0,)"
ordering="AFTER"
side="CLIENT"
```

For an optional integration, first use the loader's mod-presence check and isolate all direct API references in a compatibility-only class. Do not load that class when Halo is absent or older than the API you call. On Fabric, use `FabricLoader.getInstance().isModLoaded("halo")` plus a version check (or an optional `"suggests": {"halo": ">=2.3.0"}` with `"breaks": {"halo": "<2.3.0"}`). Register only from your client entrypoint; server-side class loading must not initialize the bridge.

<a id="preview-anchor-provider-api"></a>
<a id="provider-routing"></a>

## 2. Model provider: world and preview

One `HaloAnchorApi.register(id)` returns one source for either or both spaces. Built-in YSM/EMF integrations use this same public path. Providers supply heads only; the host owns sessions, physics, resources and drawing.

### One model hook, two spaces

Keep one source for the client lifetime. Call this bridge **synchronously after the final base-head transform**, inside the active entity/model draw. Both callbacks are YOUR renderer's capture functions, not Halo methods. Return a converted pose as described below, or `null` when unavailable.

```java
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import network.azusake.halo.api.v2.*;

public final class ModelHaloBridge implements AutoCloseable {
    private final AnchorSource source = HaloAnchorApi.register("example:final_head");

    public boolean capture(UUID drawnUuid, int drawnRuntimeId,
            Function<PreviewAnchorContext, PreviewAnchorPose> previewHead,
            Supplier<AnchorPose> worldHead) {
        if (HaloAnchorApi.isPreviewRendering()) {
            PreviewAnchorContext context = HaloAnchorApi.currentPreviewContext();
            if (context == null || !context.isActive() || context.hasModelAnchor()
                    || !context.renderedEntity().equals(drawnUuid)
                    || context.renderedRuntimeId() != drawnRuntimeId) return false;
            PreviewAnchorPose pose = previewHead.apply(context);
            return pose != null && source.submitPreview(context, pose);
        }
        AnchorPose pose = worldHead.get();
        return pose != null && source.submit(drawnUuid, pose);
    }

    @Override public void close() { source.close(); }
}
```

Instantiate once in client initialization after checking the dependency; close when permanently disabling the integration, not per frame/screen. Preview-only providers can return `null` from the world callback; world-only providers should skip the entire preview branch. **A null preview context is not permission to submit in world space**: unrelated nested entities and invalidated preview scopes also return null. Never retain contexts or defer callbacks to another thread/frame.

<a id="world-anchor-api"></a>

### World submission: choose the hook

Pass the currently drawn entity's UUID/runtime ID to `ModelHaloBridge.capture`, and return an `AnchorPose` from its world callback. Use the final base-head model or locator transform; a renderer wrapper must finish its own adjustment first. Do not capture from a client tick, end-of-frame callback, worker thread, armour layer or unrelated feature model. Deferred renderers must submit during the actual deferred head draw, not when extraction is queued, and still inside Halo's accepted entity-render scope.

For a world-only integration, this method connects already-converted coordinates to the shared bridge (imports: `java.util.UUID` and `network.azusake.halo.api.v2.*`):

```java
static boolean submitWorldHead(ModelHaloBridge bridge, UUID uuid, int runtimeId,
        double worldX, double worldY, double worldZ, AnchorRotation worldRotation) {
    return bridge.capture(uuid, runtimeId, context -> null,
        () -> new AnchorPose(new AnchorVec3(worldX, worldY, worldZ), worldRotation));
}
```

For preview draws, the same bridge calls the preview callback with the current context. Match the **rendered** entity, which may differ from the wearer. Unlike world capture, the first valid preview model wins; wrappers must coordinate the final submitter rather than overwrite it. Full selection and lifetime rules are in the [reference](#api-v2-reference).

### Produce a pose from your renderer

Capture the final head visual center/pivot chosen by the model, before halo offsets, scale, animation or physics. API local **+Y = head-up, +Z = head-forward, -X = head-right**. A model quaternion is usable directly only if its axes already match.

With column vectors, let `H` map head-local coordinates to view space and `C` map an API-oriented, block-sized head frame into model-local units/axes, including the chosen center:

```text
previewHeadInScene = inverse(context.sceneToView()) * H * C
worldHead          = Translate(cameraWorldPosition) * inverse(viewRotation) * H * C
```

`H` must include the same view/GUI root as the context; neither includes projection. Do not undo the root again if capture already yields scene/world coordinates. For camera-relative world matrices without view rotation, only add camera position. Convert 16 model units per block only if the model uses that convention, and only once.

Example using JOML in your renderer (not part of the public API); import `org.joml.Matrix4f`, `Vector3f`, and `Quaternionf`:

```java
// headToView and apiHeadToModel are supplied by YOUR renderer.
Matrix4f headToScene = new Matrix4f().set(context.sceneToView()).invert()
    .mul(headToView).mul(apiHeadToModel);
Vector3f center = headToScene.transformPosition(new Vector3f());
// Only after axis conversion, with positive uniform scale and no shear.
Quaternionf rotation = headToScene.getUnnormalizedRotation(new Quaternionf()).normalize();
PreviewAnchorPose pose = new PreviewAnchorPose(center.x, center.y, center.z,
    new AnchorRotation(rotation.x, rotation.y, rotation.z, rotation.w));
```

Remove GUI reflection through the root inverse first. For nonuniform scale/shear, orthonormalize final up/forward directions and reconstruct a right-handed rotation; quaternions cannot represent reflection. Skip singular matrices, degenerate axes and non-finite values. Value constructors validate input and can throw `IllegalArgumentException`; a `false` submission result does not catch constructor errors. Keep world translation in doubles, particularly far from the origin.

<a id="preview-host-integration"></a>

## 3. Preview panel integration

### Use the vanilla player preview helper

The easiest Minecraft panel integration is to render the **real player** through `InventoryScreen.drawEntity`. Halo 1.20.1 Fabric wraps that player draw, flushes model buffers, captures the head and draws the halo. Non-player entities are not automatically covered. Do not wrap the helper again. This path honors `playerPreviewHaloEnabled` and `playerPreviewHaloPhysicsEnabled`, both enabled by default; set the latter to `false` and restart to select rigid following.

The Minecraft-specific `HaloPreviewApi` is removed in 2.3.0 without a forwarding shim. Required
vanilla integration lives in the internal `render.PlayerPreviewRenderer`; Java public visibility
allows calls between adapter packages and does not make it a supported external API. Providers
should not depend on it or copy its physics/geometry pipeline.

<a id="independent-preview-host"></a>

### Independent preview panel: host walkthrough

For a completely independent panel, the neutral API provides computation, not a Minecraft draw helper. You need a platform bridge that supplies the **owning client's existing `PreviewPort`**, resource snapshot and GPU consumer. There is no supported static API v2 getter for Halo's live client runtime or resources. Do not create a new `ClientRuntime` and expect it to inherit Halo's equipment. If your UI cannot use the vanilla helper, arrange this bridge in the platform adapter; the following is its implementation contract.

1. Keep one `PreviewAnchorHost` per logical client and one `PreviewSession` per view. Obtain the session from `previewPort.openPreview(PreviewOptions.PHYSICS)` (or `RIGID`; no-argument `openPreview()` is rigid). Two panels showing the same wearer need two sessions.
2. The owning adapter advances `ClientPort.renderFrame` once per logical render frame, with loaded wearers included even when culled or in first person. Preview rendering consumes the latest completed appearance snapshot; it does not advance ownership/animation itself. Never run an extra world frame per panel.
3. Open a capture scope around the target model draw, matching the matrix used to draw that model. The platform entity dispatcher must bracket **every** entity, including nested entities, with `PreviewAnchorHost.beginEntityRender(uuid, runtimeId)` / `endEntityRender()` in `try/finally`. Halo 1.20.1 Fabric already installs these hooks: do not duplicate them. Direct model drawing without a dispatcher may use the scope only while drawing its target model.
4. Providers submit from model hooks. The host may call `scope.submitFallback(sceneHead, PreviewAnchorScope.Fallback.RENDERED)` for a captured vanilla head, or `POSED` if only pose preparation occurred. All fallback poses use the same scene coordinates. Resolve after drawing; `null` means skip the halo for this draw.
5. Flush entity buffers, call `session.render(frame)`, then consume `FrameOutput.legacyBatches()` followed by `meshes()` while the intended projection/lighting is active. Batch vertices are already in view space; mesh commands provide local-to-view transforms. Do not apply the GUI root a second time. Honor materials, textures, masks, light, depth/blend/cull state and restore changed GPU state. The [frame/output contract](../../core/README.md#frame-and-coordinate-contract) and [mesh contract](../../core/README.md#mesh-adapter-contract-210) define the submission details.

The following method is a complete neutral **capture → frame → output** bridge. It does not implement your platform's entity drawing or GPU backend. Its callbacks and parameters are supplied by that adapter. Imports use `java.util.UUID`, `java.util.function.Consumer`, `network.azusake.halo.api.v2.*`, `network.azusake.halo.core.render.*` and `network.azusake.halo.core.runtime.*`.

```java
static void drawPanel(PreviewAnchorHost host, PreviewSession session,
        UUID wearer, int wearerId, UUID rendered, int renderedId,
        float[] sceneToView, float[] rootTransform, FrameScene.CameraSample camera,
        long timeMillis, long frameNanos, LightSample light,
        FrameScene.TextureLookup textures, VisualResources visuals,
        Consumer<PreviewAnchorScope> drawModel, Runnable flushModel,
        Consumer<FrameOutput> submitGpu) {
    try (PreviewAnchorScope scope = host.open(
            wearer, wearerId, rendered, renderedId, sceneToView)) {
        drawModel.accept(scope); // Actual draw; providers/fallback capture here.
        flushModel.run();
        PreviewAnchorPose head = scope.resolved();
        if (head == null) return;
        PreviewFrame frame = new PreviewFrame(wearer, wearerId,
            new AnchorPose(new AnchorVec3(head.x(), head.y(), head.z()), head.rotation()),
            camera, rootTransform, timeMillis, frameNanos, light, textures, visuals);
        submitGpu.accept(session.render(frame));
    }
}
```

This overload uses orthographic projection. For a perspective panel, use the `PreviewFrame` constructor's final `PreviewFrame.Projection.PERSPECTIVE` argument. The conversion to `AnchorPose` here is only a `PreviewFrame` value; do not send it to world `source.submit`.

| Input | What the adapter must supply |
| --- | --- |
| Wearer UUID/runtime ID | The live real entity in the owning client's appearance snapshot; a proxy only changes rendered identity |
| `sceneToView` | Column-major 4×4, `rootTransform * Translate(-camera.position)`, excluding projection |
| Camera and root | Position in scene blocks; up/right in resulting view space. For a zero camera, root equals `sceneToView`. Apply camera translation exactly once |
| Time | Current milliseconds and monotonic nanoseconds; reuse the same nanos for repeated draws of one sample |
| Light/textures/visuals | Valid native light (often `LightSample.FULL_BRIGHT` in UI), texture availability and the owning adapter's matching `VisualResources` generation |

Keep the session across frames, close it when its view ends, and recreate it when `isValid()` becomes false. `clear()`, `replace()` and world-token changes invalidate client sessions; resource reload leaves output empty until a fresh appearance/resource snapshot is available. Call `session.resetMotion()` after a discontinuous scene/model change. On world replacement, disconnect or host disposal, call `host.clear()` on its owning thread and still close active scopes in reverse nesting order. Host capture lifetime and view physics lifetime are separate; closing a scope every draw must not close the retained session.

Further host details: [capture contract](../../core/README.md#preview-anchor-providers-since-230) and [session contract](../../core/README.md#preview-contract-since-220).

<a id="api-v2-reference"></a>

## 4. API v2 reference

World submission dates from Halo 1.3.0; 2.3.0 adds preview support to the same entry point. All types below are in `network.azusake.halo.api.v2` and use Java 17/JDK types. This is a signature summary, not code to copy into your mod:

```java
public final class HaloAnchorApi {
    public static AnchorSource register(String sourceId);
    public static PreviewAnchorContext currentPreviewContext();
    public static boolean isPreviewRendering();
}

public interface AnchorSource extends AutoCloseable {
    boolean submit(UUID entityUuid, AnchorPose pose);
    boolean submitPreview(PreviewAnchorContext context, PreviewAnchorPose pose);
    @Override void close();
}

public record AnchorPose(AnchorVec3 position, AnchorRotation rotation) {}
public record AnchorVec3(double x, double y, double z) {}
public record AnchorRotation(double x, double y, double z, double w) {}
public record PreviewAnchorPose(double x, double y, double z, AnchorRotation rotation) {}

public interface PreviewAnchorContext {
    UUID wearer();
    int runtimeId();
    UUID renderedEntity();
    int renderedRuntimeId();
    long renderId();
    float[] sceneToView();
    boolean isActive();
    boolean hasModelAnchor();
}
```

### Source identity and lifetime

`sourceId` must match `[a-z0-9_.-]+:[a-z0-9_./-]+`. Invalid IDs throw `IllegalArgumentException`; active duplicates throw `IllegalStateException`. The ID is unique across both spaces. Registration survives world changes; the idempotent `close()` immediately invalidates this source's contributions in both spaces and makes later submissions return `false`. The same ID can then be registered again; closing the old handle again does not revoke the replacement. Use separate IDs for independent world/preview disposal. Submitting in only one space does not affect the other.

### Coordinates and values

| Value | Contract |
| --- | --- |
| World `AnchorPose.position` | Absolute world position in blocks: +X east, +Y up, +Z south |
| `PreviewAnchorPose` position | Block-sized preview scene before `sceneToView`, without GUI pixels, projection or world position |
| `AnchorRotation` | Normalized `(x,y,z,w)` quaternion; local +Y head-up, +Z head-forward, -X head-right |

Positions must be finite. Rotation construction normalizes finite non-zero input and rejects zero, non-finite or overflowing norms. Neither pose includes halo offsets, physics or animation. See [pose conversion](#produce-a-pose-from-your-renderer) for matrix handling.

### Preview context

| Member | Meaning |
| --- | --- |
| `wearer()` / `runtimeId()` | Real entity supplying equipment and appearance |
| `renderedEntity()` / `renderedRuntimeId()` | Actual model, possibly a UI proxy; match these against your hook |
| `renderId()` | Draw diagnostic ID, not a persistent session/view key |
| `sceneToView()` | Copied column-major 4×4 scene-to-view matrix, excluding projection |
| `isActive()` | Whether this thread/entity/view currently permits submission |
| `hasModelAnchor()` | A still-registered model provider has won; excludes vanilla fallback |

Contexts belong to one draw and cannot be implemented or retained by providers. Nested views suspend the outer context until they close. Unrelated nested entities and invalidated scopes return a null `currentPreviewContext()` while `isPreviewRendering()` remains true until scope exit.

### Acceptance, selection and fallback

| Rule | World `submit` | Preview `submitPreview` |
| --- | --- | --- |
| Accepted scope | Recognized main-camera entity pass with matching UUID | Active context on its owning render thread, matching target entity/view |
| Selection | Last accepted sample wins | First valid model wins; model > rendered-vanilla fallback > posed-only fallback |
| Rejection (`false`) | Null/wrong UUID, null pose, closed source, out-of-scope, GUI, shadow/auxiliary or unknown shader pass | Null pose, closed source, stale/forged/inactive context, wrong thread/entity, or model already selected |
| Retention | Current/previous main frame, rebased from entity-relative position onto current interpolation | This draw only; no previous-frame or world-cache reuse |
| Invalidation | World change, entity unload or source close | Scope exit/host clear or source close; nested views temporarily suspend submission |

Rejected submissions do not replace valid poses. In previews, closing the winning source permits fallback or a new model submission; previously rejected samples are not replayed. Each vanilla fallback kind also accepts its first valid sample. Without any preview provider/fallback, there is no halo for that draw.

The world host classifies passes using the main camera root matrix, main frustum, current entity and an Iris shadow-pass probe when available. Unclassifiable detected backends are rejected with rate-limited diagnostics; later shadow/auxiliary passes do not advance the main frame. Without a valid world capture, players use the configured pose fallback and other living entities use vanilla height/yaw/pitch. The local first-person player always uses the camera anchor.

Built-in sources are `halo:vanilla`, `halo:emf` and, for supported YSM versions, `halo:ysm`. Vanilla and EMF capture player heads; YSM captures living entities. ETF is a coexistence test target, not a separate capture source.

<a id="integration-checks"></a>

## 5. Verification and troubleshooting

1. Equip a known halo, check third person, then open the player preview. If disabled, enable `playerPreviewHaloEnabled` in `config/halo-azusake/halo_mod_config.json` and restart.
2. Move/rotate the head: it should follow once, with authored halo offsets applied by Halo. Moving/rescaling the GUI alone should not create preview physics forces.
3. Rate-limit diagnostics for acceptance, source ID, drawn UUID/runtime ID and preview `renderId()`. `true` means accepted, not necessarily visible: hidden/dead/unloaded wearers and missing definitions/resources/appearance can still produce no draw. Do not retry rejected samples outside the renderer.
4. Test two panels for one wearer, close/reopen, resource reload, entity unload, world replacement, third person, shaders, live source close and re-registration. A proxy requires host identity mapping; its UUID does not automatically inherit the real wearer's appearance.

| Symptom | Check |
| --- | --- |
| Always-null preview context | Supported player helper or custom host scope, drawn identity and synchronous hook |
| Preview submission always loses | Another provider submitted first; world last-wins rules do not apply |
| Expected main-pass world submissions fail | UUID, final-head hook, render nesting and pass classification |
| Offset changes with GUI scale/camera distance | Double root/camera transform, wrong units or projection included |
| Correct anchor but no halo | Equipment, display flag, wearer runtime ID, latest appearance and resource generation |
| Physics snaps every frame | Retain one `PreviewSession` per view instead of reopening per draw |

---

<a id="other-interfaces"></a>

## Other interfaces

The following sections cover command interception, lifecycle/configuration and networking.

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

This integration supports only the Fabric 1.20.1 release
`2.6.5-fabric+mc1.20.1`. Existing configs receive the two fields automatically
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

## Network Channels

| Channel | Direction | Purpose |
|---------|-----------|---------|
| `halo:sync` | S2C | Full state snapshot on join |
| `halo:update` | S2C | Incremental attach/remove |
| `halo:defs_report` | C2S | Client reports local definition IDs |
| `halo:hello` | S2C | Handshake — server has the mod installed |
