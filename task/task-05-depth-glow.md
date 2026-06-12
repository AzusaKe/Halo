# Task 05: Depth Occlusion & Glow Rendering

## Agent Type: `general-purpose`

## Goal
Fix depth ordering so the halo is correctly occluded by the entity model when viewed from the front, and implement the additive glow layer. After this task, the halo renders behind the entity body (not floating in front of it) and the glow effect works.

## Dependencies
- `task-04-client-render` complete (basic rendering works)

## Input
- Working `HaloRenderer` + `WorldRenderEvents.AFTER_TRANSLUCENT` handler
- `MultiBillboardShape` and `GlowLayer` from data model
- `HaloQuadBuilder` that can render a single texture layer
- Mixin infrastructure (`halo.mixins.json` already created in task-00)

## Task

### 1. Implement `LivingEntityRendererMixin.java` (`mixin/` package)

Target: `net.minecraft.client.render.entity.LivingEntityRenderer.render(LivingEntity, float, float, MatrixStack, VertexConsumerProvider, int)`

Injection point: `@Inject` before `renderModel()` call (or at `HEAD` of the render method, after the main matrix setup).

Purpose: Render the halo body **before** the entity model, with depth write enabled. This way, when viewed from the front, the entity model's pixels write to the depth buffer AFTER the halo, and the halo pixels behind the entity will fail the depth test.

```java
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity> {
    @Inject(
        method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;scale(...)"),
        require = 0  // Don't crash if another mod changes this
    )
    private void renderHaloPreModel(T entity, float yaw, float tickDelta,
                                     MatrixStack matrices, VertexConsumerProvider vcp,
                                     int light, CallbackInfo ci) {
        // Delegate to HaloRenderer.renderForEntity(entity, tickDelta, matrices, vcp, light, true);
    }
}
```

Alternatively, if the specific injection point is fragile, inject at `HEAD` and render the halo first. The key is that halo draws BEFORE the entity model.

### 2. Implement `HaloGlowRenderer.java` (`render/` package)

Render the glow layer with additive blending:

```java
public class HaloGlowRenderer {
    // Custom RenderLayer: translucent, no depth write, additive blending
    private static final RenderLayer GLOW_LAYER = RenderLayer.of(
        "halo_glow",
        VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
        VertexFormat.DrawMode.QUADS,
        256,
        false,  // no delegate
        true,   // translucent
        RenderLayer.MultiPhaseParameters.builder()
            .texture(new RenderPhase.Texture(glowTextureId, false, false))
            .transparency(ADDITIVE_TRANSPARENCY)
            .depthTest(LEQUAL_DEPTH_TEST)      // visible through entities but not walls
            .writeMaskState(COLOR_WRITE)        // write color, NOT depth
            .cull(DISABLE_CULLING)
            .lightmap(ENABLE_LIGHTMAP)
            .build(false)
    );

    public static void renderGlow(MultiBillboardShape shape, MatrixStack matrices,
                                   VertexConsumerProvider vcp, int light, float tickDelta) {
        GlowLayer glow = shape.glow();
        // Calculate pulse alpha modulation if configured
        float alpha = glow.alpha();
        if (glow.pulse() != null) {
            alpha *= (float)(0.5 + 0.5 * Math.sin(tickDelta * glow.pulse().frequency()));
        }
        // Emit quad with glow texture, additive blending, modulated alpha
        VertexConsumer vc = vcp.getBuffer(GLOW_LAYER);
        // ... emit quad vertices with alpha in the color channel
    }
}
```

### 3. Split HaloRenderer into Two Passes

**Pass 1 — Pre-model (depth):**
- Called from `LivingEntityRendererMixin`
- Renders main halo body for the entity being rendered
- Depth write ON, standard translucency
- Skip if entity is invisible

**Pass 2 — Post-translucent (glow):**
- Called from `WorldRenderEvents.AFTER_TRANSLUCENT`
- Renders glow layers for ALL halos in the world
- Additive blending, no depth write
- Sorted by camera distance for correct overlap

### 4. Update `HaloQuadBuilder` for Color Tinting

Modify quad emission to accept an ARGB color multiplier:
- Main texture: white (0xFFFFFFFF) — no tint
- Glow texture: apply `glow.color` hex value as vertex color with `glow.alpha` in the alpha channel

### 5. Handle Edge Cases

- **Entity invisibility**: Check `entity.isInvisible()` — if true, skip main body render but still render glow at reduced alpha (or skip both, configurable)
- **Spectator mode**: Skip all halo rendering for spectators viewing an entity
- **First-person camera**: Skip halo rendering for the camera entity itself
- **Sodium/Oculus compatibility**: If shader mods are present, the pre-model mixin may not fire. Add a fallback: if mixin fails, render everything in `AFTER_TRANSLUCENT` with depth test but no depth write (accept slightly worse depth behavior but no crash)

### 6. Verify

- View entity from the front in third-person → halo should be BEHIND the entity body (occluded)
- View from the side → half of halo visible, half behind body
- View from behind → full halo visible
- Glow layer visible as a soft additive halo around the main ring
- Glow pulsates if pulse config is set
- Entity turns invisible (potion effect) → halo behaves correctly
- `/halo apply @p halo:ring_default` — verify depth + glow both work

## Output Artifacts
- `mixin/LivingEntityRendererMixin.java`
- `render/HaloGlowRenderer.java`
- Updated `render/HaloRenderer.java` (split into two-pass)
- Updated `render/HaloQuadBuilder.java` (color tint support)
- Updated `halo.mixins.json` (add mixin entry)
