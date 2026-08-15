# Halo Mod — 公开 API

## `HaloCommandInterceptor`

**包：** `network.azusake.halo.client`

`HaloCommandInterceptor` 是拦截 `/halo` 命令（在到达服务器之前）的抽象层。它将核心的阶段追踪/本地执行逻辑与依赖加载器的钩子机制隔离开。

### 接口

```java
public interface HaloCommandInterceptor {
    void register();        // 注册客户端 /halo 命令树，幂等
    boolean isRegistered(); // 是否已注册
}
```

### 行为约定

每个实现在命令 executor 中**必须**遵循以下流程：

1. 调用 `HaloPhaseTracker.getInstance().shouldIntercept()`。
2. 若为 `true`（LOCAL 阶段——服务器无 mod）：
   - 调用 `HaloLocalCommandHandler.handle(commandString)`。
   - 在本地聊天栏显示返回的反馈文本。
   - **不要**将命令转发到服务器。
3. 若为 `false`（单人游戏或 MULTIPLAYER 阶段——服务器有 mod）：
   - 通过加载器原生的发送命令机制将原始命令字符串转发到服务器
     （例如 `client.getNetworkHandler().sendCommand(cmd)`）。

### 为什么需要此层

- 核心类（`HaloPhaseTracker`、`HaloLocalCommandHandler`、`HaloLocalManager`）**零**加载器依赖。
- 基于 Mixin 的拦截方案在不同 Yarn 映射版本间脆弱且难以移植。
- 此接口让每个加载器（Fabric、NeoForge 等）可以接入自己的命令注册 API，核心逻辑完全不变。

### Fabric 实现

`FabricHaloCommandInterceptor`（同一包下）通过 `ClientCommandRegistrationCallback.EVENT` 注册 `/halo`。

### 移植到其他加载器

1. 使用目标加载器的客户端命令 API 实现 `HaloCommandInterceptor`。
2. 在客户端初始化阶段的等价位置调用 `register()`。
3. 无需修改任何核心逻辑文件。

---

## 生命周期钩子

### `HaloPhaseTracker`

| 方法 | 说明 |
|------|------|
| `getPhase()` | 返回 `LOCAL` 或 `MULTIPLAYER` |
| `transitionToMultiplayer()` | 收到 `halo:hello` 包时调用 |
| `resetToLocal()` | 断连时调用 |
| `shouldIntercept()` | 单人安全网——集成服务器运行时永远返回 `false` |

### `HaloLocalManager`

每次变更时持久化到 `config/halo-azusake/halo_local_halos.json`。ServerKey 为稳定的 `hostString:port` 格式。

| 方法 | 说明 |
|------|------|
| `showHalo(serverKey, uuid, defId)` | 记录光环 |
| `hideHalo(serverKey, uuid)` | 移除光环 |
| `getHalo(serverKey, uuid)` | 查询 |
| `getHalosForServer(serverKey)` | 获取所有 UUID 供渲染使用 |
| `clearServer(serverKey)` | 断连清理 |
| `serverKeyFromAddress(address)` | 从 `InetSocketAddress` 构造稳定 key |

### `HaloLocalCommandHandler`

```java
static String handle(String command)
```

支持：`list`、`dump`、`show @s <定义ID>`、`hide @s`、`config <参数> <值>`、`reload`、`active`。
`show`/`hide` 严格要求 `@s` 选择器。

### `HaloModConfig` / `HaloModConfigStore`

模组级配置文件 `config/halo-azusake/halo_mod_config.json`，承载命令系统的底层配置
（当前为 `/halo` 所需权限等级），与 `/halo config` 运行时调参（`HaloConfig`）完全
分离。文件缺失或空白时启动自动写入默认值；越界值钳制到 0-4；损坏 JSON 回退默认并
警告；未知键忽略（向后兼容）。

| 方法 | 说明 |
|------|------|
| `HaloModConfigStore.load()` | 启动时加载（mod 初始化调用一次） |
| `HaloModConfigStore.getPermissionLevel()` | `/halo` 命令树所需权限等级（默认 2，范围 0-4） |
| `HaloModConfig.getCommandPermissionLevel()` | 当前权限等级 |
| `HaloModConfig.setCommandPermissionLevel(int)` | 设置权限等级（钳制到 0-4） |

---

## 自定义头部锚点（Custom Head Anchor）

Halo 的锚点计算已经抽象为「头部锚点 Provider」接口，其他模组可以在客户端初始化时注册自己的 Provider，接管（或覆盖）任意实体的头部锚点计算，从而配合自定义渲染/骨骼/动画系统。

### 数据流与调用契约

- `EntityAnchorProvider.resolve(LivingEntity, float tickDelta)` 由 Halo 在**渲染线程每帧**调用一次（非每 tick），传入当前渲染帧的插值进度 `tickDelta`（0~1，低帧率时可能 >1）。
- Provider 负责在上一/当前 tick 状态之间自行插值，并返回完整的 **6 自由度** `HeadAnchor`：
  - `Vec3d headCenter`：头部中心世界坐标（3 自由度）
  - `float yaw` / `float pitch` / `float roll`：头部朝向（3 自由度，度，MC 约定）
- 模组只需要提供**头部**的 6 自由度；光环自身的位置阻尼、offset、旋转模式等仍由 Halo 内部计算。

### 帧内时序与空值契约

- **帧内时序**：Halo 可能在当前帧的摄像机/头部朝向被 provider 的动画或摄像机系统计算出来**之前**就调用 `resolve`。provider 必须**缓存上一帧的 `HeadAnchor`**，并在当前帧输入未就绪时返回缓存值，而不是返回不完整或默认的数据。
- **禁止返回 `null`**：provider 不得返回 `null`，所有分量必须为有限数值（不得含 NaN）。Halo（resolver）把 `null` 视为 provider 的 bug：记录 error 日志并回退到 `FallbackAnchorProvider`，避免渲染管线崩溃。

### 注册方式

在你的 `ClientModInitializer` 中监听 `AnchorProviderSetupEvent`。Halo 会在**所有模组客户端入口点执行完毕后（第一个客户端 tick 结束时）**触发该事件，因此无论模组加载顺序如何，你的监听器都能被收到：

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
        Vec3d headCenter = ...; // 自定义头部中心（世界坐标）
        float yaw = ...;        // 头部朝向（度）
        float pitch = ...;
        float roll = ...;       // 纯动画模组可自由提供 roll，无需摄像机
        return new HeadAnchor(headCenter, yaw, pitch, roll);
    }
}
```

### 注册表语义（`EntityAnchorProviderRegistry`）

| 场景 | 做法 | 说明 |
|------|------|------|
| 特定实体种类 | `register(ZombieEntity.class, provider)` | 精确类优先；注册父类会影响所有子类 |
| 特定个体 | `register(uuid, provider)` | UUID 命中优先于任何类型注册 |
| 任意谓词（NBT/队伍/动态状态） | 委托模式 | 注册前抓取旧 Provider，未命中时委托 |

委托模式示例（只改带特定 NBT 标记的僵尸）：

```java
EntityAnchorProvider prev = registry.getProvider(ZombieEntity.class); // 注册前抓取，永不 null
registry.register(ZombieEntity.class, (entity, tickDelta) ->
    entity.getNbt().getBoolean("my:special_head")
        ? myAnchor(entity, tickDelta)
        : prev.resolve(entity, tickDelta));
```

查找顺序：`UUID → 精确类 → 继承链向上 → FallbackAnchorProvider`。类/UUID 各自「最后注册胜出」。

### 默认行为

- 玩家使用 `PlayerAnchorProvider`（基于 `data/halo/entity_anchors/player.json`）；其他实体使用 `FallbackAnchorProvider`（高度 × 0.85 启发式）。
- 香草实体头部没有 roll；本地玩家的头部跟随摄像机，因此其 roll 继承真实摄像机的 roll（1.20.1 无 `Camera.getRoll()`，由 `Camera.getRotation()` 剥离 yaw/pitch 后恢复），光环头部坐标系随摄像机 roll 刚体倾斜。

### 6 自由度旋转约定

- 角度单位均为度，roll 符号遵循 MC 摄像机 roll 约定（等价于 `rotationYXZ(-yaw, pitch, roll)`，即 MC 摄像机四元数的 Z 分量）。
- `roll=0` 时行为与旧版 5DOF 完全一致。

---

## 网络通道

| 通道 | 方向 | 用途 |
|------|------|------|
| `halo:sync` | S2C | 玩家加入时全量状态快照 |
| `halo:update` | S2C | 增量 附加/移除 广播 |
| `halo:defs_report` | C2S | 客户端报告本地可用的定义 ID |
| `halo:hello` | S2C | 握手——宣告服务器已安装 mod |
