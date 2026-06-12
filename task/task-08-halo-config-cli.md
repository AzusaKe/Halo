# Task 08: Halo Config & CLI Commands

## Agent Type: `general-purpose`

## Goal

Implement halo configuration schema, JSON loading from data packs, and `/halo` command system for real-time halo spawning/control. Players can preview halos on themselves or other entities.

## Dependencies

- `task-07-damping-physics` complete (physics working)
- `task-01-data-model` complete (JSON deserializer exists)

## Input

- Sample halo definitions from task-01
- `HaloInstance` and damping system from task-07
- Minecraft command framework (Brigadier)

## Task

### 1. Create `HaloConfig.java` (runtime configuration)

```java
public class HaloConfig {
    private double linearDampingFactor = 0.3;    // [0, 1] where 0 = snap, 1 = never move
    private double angularDampingFactor = 0.3;
    private double maxLinearDistance = 1.0;       // blocks
    private double maxAngularDegrees = 45.0;

    private double haloScale = 1.0;               // uniform scale
    private Vec3d positionOffset = new Vec3d(0, 0.2, 0);  // relative to head anchor
    private Vec3d rotationOffset = new Vec3d(0, 0, 0);    // Euler angles in degrees

    // Getters & setters with bounds checking
    public void setLinearDampingFactor(double value) {
        this.linearDampingFactor = Math.max(0.0, Math.min(1.0, value));
    }

    public void setMaxLinearDistance(double value) {
        this.maxLinearDistance = Math.max(0.01, value);
    }

    // ... etc

    public HaloDampingConfig toDampingConfig() {
        return new HaloDampingConfig(
            linearDampingFactor,
            angularDampingFactor,
            maxLinearDistance,
            maxAngularDegrees
        );
    }

    public HaloPositioning toPositioning() {
        return new HaloPositioning(positionOffset, haloScale);
    }
}
```

### 2. Create `HaloConfigCommand.java` (Brigadier command tree)

```java
public class HaloConfigCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> haloNode = literal("halo")
            .requires(src -> src.hasPermissionLevel(2));

        // /halo show <entity> <definition_id>
        haloNode.then(literal("show")
            .then(argument("target", EntityArgumentType.entity())
                .then(argument("definition", IdentifierArgumentType.identifier())
                    .executes(ctx -> showHalo(
                        ctx.getSource(),
                        EntityArgumentType.getEntity(ctx, "target"),
                        IdentifierArgumentType.getIdentifier(ctx, "definition")
                    ))
                )
            )
        );

        // /halo hide <entity>
        haloNode.then(literal("hide")
            .then(argument("target", EntityArgumentType.entity())
                .executes(ctx -> hideHalo(
                    ctx.getSource(),
                    EntityArgumentType.getEntity(ctx, "target")
                ))
            )
        );

        // /halo config <param> <value>
        haloNode.then(literal("config")
            .then(literal("linear-damping")
                .then(argument("value", DoubleArgumentType.doubleArg(0.0, 1.0))
                    .executes(ctx -> configSet(ctx, "linear-damping",
                        DoubleArgumentType.getDouble(ctx, "value")))
                )
            )
            .then(literal("scale")
                .then(argument("value", DoubleArgumentType.doubleArg(0.1, 5.0))
                    .executes(ctx -> configSet(ctx, "scale",
                        DoubleArgumentType.getDouble(ctx, "value")))
                )
            )
            // ... more config options
        );

        // /halo list
        haloNode.then(literal("list")
            .executes(ctx -> listDefinitions(ctx.getSource()))
        );

        dispatcher.register(haloNode);
    }

    private static int showHalo(ServerCommandSource source, Entity entity, Identifier defId) {
        if (!(entity instanceof LivingEntity)) {
            source.sendError(Text.literal("Target must be a living entity"));
            return 0;
        }

        HaloManager.getInstance().showHaloOn((LivingEntity) entity, defId);
        source.sendFeedback(() -> Text.literal("Halo " + defId + " shown on " + entity.getDisplayName().getString()), true);
        return 1;
    }

    private static int hideHalo(ServerCommandSource source, Entity entity) {
        if (!(entity instanceof LivingEntity)) return 0;

        HaloManager.getInstance().hideHaloOn((LivingEntity) entity);
        source.sendFeedback(() -> Text.literal("Halo hidden"), true);
        return 1;
    }

    private static int configSet(CommandContext<ServerCommandSource> ctx, String param, double value) {
        HaloConfig config = HaloManager.getInstance().getConfig();

        switch (param) {
            case "linear-damping" -> config.setLinearDampingFactor(value);
            case "scale" -> config.setHaloScale(value);
            // ... more params
        }

        ctx.getSource().sendFeedback(
            () -> Text.literal("Set " + param + " to " + value),
            true
        );
        return 1;
    }

    private static int listDefinitions(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("Loaded halo definitions:"), false);
        HaloJsonLoader.getRegistry().forEach((id, def) -> {
            source.sendFeedback(() -> Text.literal("  - " + id), false);
        });
        return 1;
    }
}
```

### 3. Create `HaloManager.java` (singleton state controller)

```java
public class HaloManager {
    private static final HaloManager INSTANCE = new HaloManager();

    private final HaloConfig config = new HaloConfig();
    private final Map<UUID, HaloInstance> activeHalos = new ConcurrentHashMap<>();

    public static HaloManager getInstance() {
        return INSTANCE;
    }

    public void showHaloOn(LivingEntity entity, Identifier defId) {
        HaloDefinition def = HaloJsonLoader.getRegistry().get(defId);
        if (def == null) {
            HaloMod.LOGGER.warn("Unknown halo definition: " + defId);
            return;
        }

        HaloInstance instance = new HaloInstance(
            entity.getUuid(),
            defId,
            config.toPositioning(),
            config.toDampingConfig()
        );
        activeHalos.put(entity.getUuid(), instance);
    }

    public void hideHaloOn(LivingEntity entity) {
        activeHalos.remove(entity.getUuid());
    }

    public void tickAll() {
        // Called by HaloTickHandler each server tick
        for (HaloInstance instance : activeHalos.values()) {
            LivingEntity entity = getEntityByUuid(instance.getEntityUuid());
            if (entity == null || !entity.isAlive()) {
                activeHalos.remove(instance.getEntityUuid());
                continue;
            }

            Vec3d anchorPos = getHeadAnchorPosition(entity);
            instance.tickDamping(anchorPos, instance.getRelativePosition().add(anchorPos),
                config.toDampingConfig());
        }
    }

    private Vec3d getHeadAnchorPosition(LivingEntity entity) {
        // Player: eye position (roughly at head)
        // Others: (pos + size.y * 0.9)
        if (entity instanceof PlayerEntity player) {
            return player.getEyePos();
        }
        return entity.getPos().add(0, entity.getHeight() * 0.85, 0);
    }

    public HaloConfig getConfig() {
        return config;
    }

    public HaloInstance getHaloInstance(UUID entityUuid) {
        return activeHalos.get(entityUuid);
    }

    // ... etc
}
```

### 4. Wire into HaloMod

In `HaloMod.onInitialize()`:

```java
public void onInitialize() {
    HaloJsonLoader.register();
    HaloTickHandler.register();

    // Register command (can be done in separate class)
    CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
        HaloConfigCommand.register(dispatcher);
    });

    HaloMod.LOGGER.info("Halo mod initialized");
}
```

### 5. Create sample halo definition `ring_default.json`

```json
{
  "id": "ring_default",
  "shape": {
    "type": "billboard",
    "texture": "halo:halo/ring_default",
    "size": [0.5, 0.5],
    "glow": {
      "texture": "halo:halo/ring_glow",
      "size": [0.6, 0.6],
      "color": "#00FF00",
      "alpha": 0.8,
      "pulse": {
        "amplitude": 0.3,
        "frequency": 2.0,
        "phase": 0.0
      }
    }
  },
  "animation": {
    "positionCurves": [
      {
        "axis": "y",
        "curve": {
          "type": "oscillate",
          "amplitude": 0.1,
          "frequency": 1.0,
          "phase": 0.0
        }
      }
    ],
    "rotationCurves": [
      {
        "axis": "yaw",
        "curve": {
          "type": "linear",
          "start": 0.0,
          "speed": 45.0
        }
      }
    ]
  },
  "positioning": {
    "offset": [0.0, 0.3, 0.0],
    "scale": 1.0
  },
  "damping": {
    "linearFactor": 0.3,
    "angularFactor": 0.3,
    "maxLinearDistance": 1.0,
    "maxAngularDegrees": 45.0
  }
}
```

### 6. Unit Tests

```
src/test/java/com/example/halo/command/HaloConfigTest.java
- testBoundsChecking(): config values clamped correctly
- testToDampingConfig(): conversion produces valid config
```

### 7. In-Game Verification

```
/halo list
→ Shows: ring_default, other_halo, etc.

/halo show @s ring_default
→ "Halo ring_default shown on [player]"
→ Halo appears on player head

/halo config linear-damping 0.5
→ "Set linear-damping to 0.5"
→ Halo damping changes (more lag)

/halo hide @s
→ Halo disappears
```

## Output Artifacts

- `src/main/java/com/example/halo/config/HaloConfig.java`
- `src/main/java/com/example/halo/command/HaloConfigCommand.java`
- `src/main/java/com/example/halo/manager/HaloManager.java`
- Updated: `src/main/java/com/example/halo/HaloMod.java` (register command)
- `src/main/resources/data/halo/halo_definitions/ring_default.json`
- `src/test/java/com/example/halo/config/HaloConfigTest.java`

## Success Criteria

✓ `/halo show @s <defId>` spawns halo on player  
✓ `/halo config` updates halo settings in real-time  
✓ `/halo list` shows all loaded definitions  
✓ Halos persist across commands (until `/halo hide`)  
✓ Config bounds enforced (no invalid values)  
✓ All unit tests pass

## Assigned to: **Dev-1 (Backend Lead)**

## Reviewer: **QA Lead**
