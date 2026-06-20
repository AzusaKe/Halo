# Task 10: Halo Renderer (Core)

## Agent Type: `general-purpose`

## Goal

Implement client-side halo rendering using billboard quads with position/rotation interpolation. Halos render as textured billboards on entity heads with smooth frame-rate-independent motion.

## Dependencies

- `task-09-halo-lifecycle` complete (entity tracking works)
- `task-04-client-render` complete (rendering system available)

## Input

- `HaloInstance` and `HaloRenderState` from task-01
- `HaloDefinition` and `BillboardShape` from task-01
- Minecraft client rendering pipeline (GameRenderer, WorldRenderer)

## Task

### 1. Create `HaloRenderer.java` (main rendering class)

```java
public class HaloRenderer {
    private static final HaloRenderer INSTANCE = new HaloRenderer();

    private final Tessellator tessellator = Tessellator.getInstance();
    private BufferBuilder bufferBuilder;
    private int texture = -1;

    public static HaloRenderer getInstance() {
        return INSTANCE;
    }

    /**
     * Call from WorldRenderEvents.AFTER_ENTITIES or similar
     */
    public void renderHalos(MatrixStack matrixStack, Camera camera, double tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        for (HaloInstance instance : HaloClientManager.getInstance().getVisibleHalos(camera)) {
            renderSingleHalo(instance, matrixStack, camera, tickDelta);
        }
    }

    private void renderSingleHalo(HaloInstance instance, MatrixStack matrixStack,
                                   Camera camera, double tickDelta) {
        // Get entity
        Entity entity = MinecraftClient.getInstance().world.getEntityById(
            (int) instance.getEntityUuid().getMostSignificantBits()
        );
        if (entity == null || !(entity instanceof LivingEntity)) return;

        LivingEntity living = (LivingEntity) entity;

        // Get halo definition
        HaloDefinition def = HaloJsonLoader.getRegistry().get(instance.getDefinitionId());
        if (def == null) return;

        // Get interpolated position & rotation
        Vec3d entityPos = living.getPos();
        Vec3d headAnchor = getHeadAnchorPosition(living);
        Vec3d haloPos = instance.getInterpolatedPosition(tickDelta).add(headAnchor);
        Quaternionf haloRot = instance.getInterpolatedRotation(tickDelta);

        // Camera-relative position (for precision)
        double camX = camera.getPos().x;
        double camY = camera.getPos().y;
        double camZ = camera.getPos().z;

        Vec3d screenPos = haloPos.subtract(camX, camY, camZ);

        matrixStack.push();
        matrixStack.translate(screenPos.x, screenPos.y, screenPos.z);

        // Apply rotation
        applyQuaternionRotation(matrixStack, haloRot);

        // Apply scale
        float scale = (float) def.positioning().scale();
        matrixStack.scale(scale, scale, scale);

        // Render based on shape type
        if (def.shape() instanceof BillboardShape billboard) {
            renderBillboard(billboard, matrixStack, camera);
        }

        matrixStack.pop();
    }

    private void renderBillboard(BillboardShape billboard, MatrixStack matrixStack, Camera camera) {
        // Face camera (always)
        Vec3f cameraDir = camera.getHorizontalPlane();  // simplified
        applyLookAt(matrixStack, cameraDir);

        // Render texture quad
        float w = (float) billboard.size().x / 2.0f;
        float h = (float) billboard.size().y / 2.0f;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // Bind texture
        int textureId = MinecraftClient.getInstance().getTextureManager()
            .getTexture(billboard.texture()).getGlId();
        RenderSystem.bindTexture(textureId);

        // Draw quad
        bufferBuilder = tessellator.getBuffer();
        bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);

        bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), -w, -h, 0).texture(0, 1).next();
        bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(),  w, -h, 0).texture(1, 1).next();
        bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(),  w,  h, 0).texture(1, 0).next();
        bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), -w,  h, 0).texture(0, 0).next();

        tessellator.draw();

        // Render glow layer if present
        if (billboard.glow() != null) {
            renderGlowLayer(billboard.glow(), w, h, matrixStack);
        }
    }

    private void renderGlowLayer(GlowLayer glow, float w, float h, MatrixStack matrixStack) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        int color = glow.color();
        float r = (float) ((color >> 16) & 0xFF) / 255.0f;
        float g = (float) ((color >> 8) & 0xFF) / 255.0f;
        float b = (float) (color & 0xFF) / 255.0f;
        float a = glow.alpha();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);

        bufferBuilder = tessellator.getBuffer();
        bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        float gw = w * 1.2f;
        float gh = h * 1.2f;

        bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), -gw, -gh, -0.01f).color(r, g, b, a).next();
        bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(),  gw, -gh, -0.01f).color(r, g, b, a).next();
        bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(),  gw,  gh, -0.01f).color(r, g, b, a).next();
        bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), -gw,  gh, -0.01f).color(r, g, b, a).next();

        tessellator.draw();

        RenderSystem.disableBlend();
    }

    private void applyQuaternionRotation(MatrixStack matrixStack, Quaternionf quat) {
        // Convert quaternion to matrix and apply
        float[] matrix = new float[16];
        quat.get(matrix);
        matrixStack.peek().getPositionMatrix().multiply(new Matrix4f(matrix));
    }

    private void applyLookAt(MatrixStack matrixStack, Vec3f cameraDir) {
        // Billboard always faces camera
        matrixStack.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(-cameraDir.getX()));
        matrixStack.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(cameraDir.getY()));
    }

    private Vec3d getHeadAnchorPosition(LivingEntity entity) {
        if (entity instanceof PlayerEntity player) {
            return player.getEyePos();
        }
        return entity.getPos().add(0, entity.getHeight() * 0.85, 0);
    }
}
```

### 2. Create `HaloClientManager.java` (client-side state)

```java
public class HaloClientManager {
    private static final HaloClientManager INSTANCE = new HaloClientManager();

    private final Map<UUID, HaloInstance> visibleHalos = new ConcurrentHashMap<>();
    private final int RENDER_DISTANCE_CHUNKS = 16;  // 256 blocks

    public static HaloClientManager getInstance() {
        return INSTANCE;
    }

    public void onEntityTracking(Entity entity) {
        if (entity instanceof LivingEntity living) {
            // Check if server says this entity has a halo
            // (fetch from HaloInstance NBT if available)
            if (HaloEntityData.hasHalo(living)) {
                Identifier defId = HaloEntityData.getHaloDefinition(living);
                if (defId != null) {
                    HaloInstance instance = new HaloInstance(
                        living.getUuid(), defId,
                        new HaloPositioning(Vec3d.ZERO, 1.0),
                        new HaloDampingConfig(0.3, 0.3, 1.0, 45.0)
                    );
                    visibleHalos.put(living.getUuid(), instance);
                }
            }
        }
    }

    public void onEntityUntrack(UUID uuid) {
        visibleHalos.remove(uuid);
    }

    public Collection<HaloInstance> getVisibleHalos(Camera camera) {
        Vec3d camPos = camera.getPos();
        double distSq = RENDER_DISTANCE_CHUNKS * RENDER_DISTANCE_CHUNKS * 16 * 16;

        return visibleHalos.values().stream()
            .filter(h -> {
                Entity e = MinecraftClient.getInstance().world.getEntityById(
                    (int) h.getEntityUuid().getMostSignificantBits()
                );
                if (e == null) return false;
                return e.getPos().squaredDistanceTo(camPos) <= distSq;
            })
            .collect(Collectors.toList());
    }
}
```

### 3. Create `HaloRenderListener.java` (Fabric event hook)

```java
public class HaloRenderListener implements WorldRenderEvents.After {

    public static void register() {
        WorldRenderEvents.AFTER.register(new HaloRenderListener());
    }

    @Override
    public void onAfter(WorldRenderContext context) {
        HaloRenderer.getInstance().renderHalos(
            context.matrixStack(),
            context.camera(),
            context.tickDelta()
        );
    }
}
```

### 4. Update `HaloModClient.java`

```java
public class HaloModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HaloRenderListener.register();
        HaloClientManager.getInstance();  // Initialize

        HaloMod.LOGGER.info("Halo client initialized");
    }
}
```

### 5. Unit Tests (rendering math)

```
src/test/java/com/example/halo/render/HaloRendererTest.java
- testQuaternionToMatrix(): quat conversion correct
- testBillboardCulling(): renderer skips out-of-range halos
- testInterpolation(): frame interpolation smooth (no jumps)
```

### 6. In-Game Verification

```
/halo show @s ring_default
→ Halo renders on head as textured quad

Walk around, jump, look around
→ Halo rotates to face camera (billboard)
→ No jitter or lag (smooth interpolation)

/halo config scale 2.0
→ Halo doubles in size (live update)

View from 200+ blocks away
→ Halo culled (not rendered)

View from 5 blocks away
→ Halo renders clearly
```

## Output Artifacts

- `src/main/java/com/example/halo/render/HaloRenderer.java`
- `src/main/java/com/example/halo/render/HaloClientManager.java`
- `src/main/java/com/example/halo/render/HaloRenderListener.java`
- Updated: `src/main/java/com/example/halo/HaloModClient.java` (register listener)
- `src/test/java/com/example/halo/render/HaloRendererTest.java`

## Success Criteria

✓ Halos render as textured billboards on entity heads  
✓ Billboards always face camera (correct billboard math)  
✓ Frame-rate independent interpolation (60+ FPS smooth)  
✓ Culling works (halos beyond ~256 blocks not rendered)  
✓ Glow layer renders correctly (additive blend)  
✓ No performance degradation with 50+ active halos

## Assigned to: **Dev-2 (Graphics Lead)**

## Reviewer: **QA Lead**
