# 锚点提供器 API

> 面向未来扩展的头部锚点计算接口，支持以模型驱动实现替换数据驱动实现，无需改动框架代码。

---

## 1. 核心接口

`EntityAnchorProvider`（`src/main/java/network/azusake/halo/physics/EntityAnchorProvider.java`）是一个 `@FunctionalInterface`，每渲染帧调用一次：做实体的 tickDelta → 输出世界空间头部中心 + 朝向。

```java
@FunctionalInterface
public interface EntityAnchorProvider {
    HeadAnchor resolve(LivingEntity entity, float tickDelta);
}
```

`HeadAnchor` 仅携带三个字段：

```java
public record HeadAnchor(Vec3d headCenter, float yaw, float pitch) {}
```

唯一的消费者是 `AnchorFrameCalculator.calculate()`。该函数只依赖这三个字段，完全不关心它们是怎么算出来的。更换 provider 实现因此无需改动 `AnchorFrameCalculator` 之外的任何代码。

---

## 2. 当前实现

| 类 | 适用范围 | 方式 |
|---|---|---|
| `PlayerAnchorProvider` | 玩家 | 姿态感知 JSON 数据驱动（pivot + head_center_vector） |
| `FallbackAnchorProvider` | 所有其他实体 | height × 0.85 粗略近似 |

分发逻辑位于 `AnchorFrameCalculator.calculate()` 第 100–103 行：

```java
EntityAnchorProvider provider = (entity instanceof PlayerEntity)
    ? playerProvider
    : fallbackProvider;
HeadAnchor ha = provider.resolve(entity, tickDelta);
```

---

## 3. 未来扩展点

### 3.1 模型驱动提供器

当你需要直接从 Mojang 渲染代码中以完全一致的输入和过程计算准确的视觉头部中心时，只需写一个新的 `EntityAnchorProvider` 实现：

```java
public class ModelBasedAnchorProvider implements EntityAnchorProvider {

    @Override
    public HeadAnchor resolve(LivingEntity entity, float tickDelta) {
        // 1. EntityRenderDispatcher → EntityModel (PlayerEntityModel / ZombieEntityModel / …)
        // 2. 调用 setAngles(entity, limbAngle, limbDistance, age, headYaw, pitch)
        //    与 Mojang 渲染路径的输入完全一致
        // 3. 读取 head ModelPart 的最终 world-space 变换
        //    (pivot + 父级级联)
        // 4. 投影得出世界空间 headCenter
        // 5. return new HeadAnchor(headCenter, yaw, pitch)
    }
}
```

替换只需修改 `AnchorFrameCalculator` 中的一行分发代码：

```java
// 旧：
EntityAnchorProvider provider = (entity instanceof PlayerEntity)
    ? playerProvider : fallbackProvider;

// 新：
EntityAnchorProvider provider = modelBasedProvider; // 或按实体类型联动
```

### 3.2 非玩家实体 JSON 档案

为牛、鸡、末影人等实体创建 `data/halo/entity_anchors/<entity_id>.json` 即可生效，无需代码改动。例如 `minecraft:cow.json`：

```json
{
  "entity": "minecraft:cow",
  "default_pose": "standing",
  "poses": {
    "standing": {
      "pivot": [0.0, 1.0, 0.8],
      "head_center_vector": [0.0, 0.15, 0.0]
    }
  }
}
```

`EntityAnchorLoader` 在 `/reload` 时自动加载。接入时只需将分发逻辑从 `instanceof PlayerEntity` 推广为通用的 profile 查找：

```java
EntityAnchorProvider provider = profileBasedProvider;
// 内部：先查 EntityAnchorLoader.getProfile(entity.getType()) → JSON 驱动
//       → 缺失？instanceof PlayerEntity → 玩家 JSON
//       → 仍然缺失？→ fallback
```

### 3.3 混合策略

同一个 provider 可以组合多种策略——例如先查 JSON，没有则走模型驱动，模型驱动失败走 fallback。单方法签名对内部复杂度无限制。

---

## 4. 向后兼容

| 变更 | 受影响文件 | 是否破坏 JSON 档案？ |
|---|---|---|
| 新增 `EntityAnchorProvider` 实现 | `AnchorFrameCalculator` 中一行分发 | 否 |
| 新增 `entity_anchors/*.json` | 零代码文件 | 否（自动加载） |
| 扩展 `HeadAnchor` record | `AnchorFrameCalculator` + provider 实现 | 仅当删除字段时 |

JSON schema（`PoseAnchor` + `EntityAnchorProfile`）是稳定的。切换到模型驱动方案后，已有的 `player.json` 自动成为回退路径——模型路径提供主数据，profile JSON 补充缺失的兜底。

---

## 5. 关键不变式

1. **`calculate()` 之后的代码永不感知 provider 切换。** 阻尼、朝向模式、相机相对坐标均只消费 `HeadAnchor`。
2. **JSON 档案是可选覆盖层。** 存在则用，不存在则降级到 provider 内置默认值。
3. **Provider 解析发生在渲染线程。** 可以安全访问 `MinecraftClient`、模型实例等客户端资源，无需考虑线程安全。
4. **所有姿态判定集中在 provider 内部。** `AnchorFrameCalculator` 不包含任何 `EntityPose` 或 `isSneaking()` 调用。
