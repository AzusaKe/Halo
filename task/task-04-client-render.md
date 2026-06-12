# Task 04: Client Rendering

## Agent Type: `general-purpose`

## Goal
Render halo billboards in world space, positioned behind entity heads, with camera-facing orientation, correct lighting, and tickDelta interpolation. After this task, a textured ring is visible floating behind the player's head and follows with damping.

## Dependencies
- `task-01-data-model` complete (definitions, shapes)
- `task-03-networking` complete (client receives halo state)
- Physics engine available (client runs identical damping for smoothness, or replays server state)

## Input
- `HaloDefinition`, `BillboardShape`, `HaloInstance`, `HaloRenderState`
- `HaloJsonLoader` registry (for looking up definitions by ID)
- Client-side halo state map (populated by networking)
- `WorldRenderEvents.AFTER_TRANSLUCENT` from Fabric API
- `org.joml` (Quaternionf, Matrix4f, Vector3f) in MC classpath
- `WorldRenderContext` providing: `matrixStack()`, `consumers()`, `camera()`, `tickDelta()`, `frustum()`, `projectionMatrix()`

## Task

### 1. Implement `RenderUtils.java` (`util/` package)

Static helper methods:
```java
// Build a camera-facing rotation matrix (billboard)
public static Matrix4f billboardMatrix(Camera camera);

// Convert world position to camera-relative (subtract camera pos, add camera offset)
public static Vec3d cameraRelative(Vec3d worldPos, Camera camera);

// Check if a position is inside the view frustum
public static boolean isInFrustum(Frustum frustum, Vec3d pos, double radius);

// Get lightmap coordinates at a world position
public static int getLight(World world, BlockPos pos);

// Build a textured quad into a VertexConsumer
public static void emitTexturedQuad(VertexConsumer vc, Matrix4f model, 
    float x0, float y0, float x1, float y1,
    float u0, float v0, float u1, float v1,
    int light, int overlay, int color);
```

### 2. Implement `HaloRenderInterpolator.java` (`physics/` package)

Lerp between previous and current halo state:

```java
public static HaloRenderState interpolate(HaloInstance halo, float tickDelta) {
    Vec3d pos = lerp(halo.prevRelativePosition, halo.relativePosition, tickDelta);
    Quaternionf rot = slerp(halo.prevRelativeRotation, halo.relativeRotation, tickDelta);
    return new HaloRenderState(pos, rot);
}
```

### 3. Implement `HaloQuadBuilder.java` (`render/` package)

Takes a `BillboardShape` + texture + size → emits vertices into a `VertexConsumer`:

- Load texture via `MinecraftClient.getInstance().getTextureManager()`
- Bind to appropriate `RenderLayer`:
  - Main texture: `RenderLayer.getEntityTranslucent(textureId)` — standard translucency with depth
  - Glow texture (future): custom render layer with additive blending
- Emit 4 vertices forming a quad with correct UVs

### 4. Implement `HaloRenderer.java` (`render/` package)

Main rendering orchestrator. Registered via `WorldRenderEvents.AFTER_TRANSLUCENT`:

```java
public static void onWorldRender(WorldRenderContext context) {
    // 1. Collect all active halos from client state
    List<HaloInstance> halos = getClientHalos();

    // 2. Sort by distance from camera (far → near) for correct transparency
    halos.sort(byCameraDistance(context.camera()));

    MatrixStack matrices = context.matrixStack();
    VertexConsumerProvider.Immediate consumers = context.consumers();
    float tickDelta = context.tickDelta();
    Camera camera = context.camera();
    Frustum frustum = context.frustum();

    for (HaloInstance halo : halos) {
        // 3. Find entity in client world
        Entity entity = context.world().getEntity(halo.entityUuid);
        if (entity == null) continue;

        // 4. Get definition
        HaloDefinition def = HaloJsonLoader.get(halo.definitionId);
        if (def == null) continue;

        // 5. Interpolate position
        HaloRenderState state = HaloRenderInterpolator.interpolate(halo, tickDelta);

        // 6. Calculate world position
        Vec3d anchor = entity.getEyePos(); // or lerped eye pos if entity has lerp state
        Vec3d worldPos = anchor.add(def.positioning.offset()).add(state.position());

        // 7. Frustum culling (skip if outside view)
        if (!RenderUtils.isInFrustum(frustum, worldPos, def.positioning.scale())) continue;

        // 8. Push matrix, translate to camera-relative world position
        matrices.push();
        Vec3d camPos = camera.getPos();
        matrices.translate(worldPos.x - camPos.x, worldPos.y - camPos.y, worldPos.z - camPos.z);

        // 9. Apply billboard rotation (camera-facing)
        matrices.multiply(RenderUtils.billboardMatrix(camera));

        // 10. Apply scale from definition
        float s = (float) def.positioning.scale();
        matrices.scale(s, s, s);

        // 11. Apply animation transform
        Transform anim = HaloAnimator.evaluate(def.animation(), context.world().getTime());
        matrices.multiply(anim.rotation()); // animation rotation on top

        // 12. Sample lightmap
        int light = RenderUtils.getLight(context.world(), BlockPos.ofFloored(worldPos));

        // 13. Render shape
        BillboardShape shape = (BillboardShape) def.shape();
        HaloQuadBuilder.render(shape, matrices, consumers, light, tickDelta);

        matrices.pop();
    }
}
```

### 5. Client State Management

In `HaloModClient.java` or a new `HaloClientManager.java`:
- Maintain `Map<UUID, HaloInstance>` populated by network receivers
- Expose via `getClientHalos()` for the renderer

### 6. Debug Features (temporary)

- Render a small colored dot at the anchor point (entity eye position) for visual alignment
- Log the first few frames of halo rendering to confirm positions
- Add `/halo debug` command that toggles debug rendering (anchor points, interpolation lines)

### 7. Verify

- Launch game, join world
- `/halo apply @p halo:ring_default`
- Switch to third-person view (F5)
- **Check**: Halo ring visible behind player's head
- Walk around: Halo follows with damping lag
- Sprint/fly: Damping creates trailing effect
- Look in different directions: Halo stays behind head, camera-facing billboard
- Teleport: Halo snaps, no fly-across
- First-person view: Halo should not be visible (it's behind the camera)

## Output Artifacts
- `util/RenderUtils.java`
- `util/MathUtils.java` (if needed for lerp/slerp helpers)
- `physics/HaloRenderInterpolator.java`
- `render/HaloQuadBuilder.java`
- `render/HaloRenderer.java`
- `render/HaloClientManager.java` (or inline in HaloModClient)
- Updated `HaloModClient.java` with render event registration
