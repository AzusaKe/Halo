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

模组级配置文件 `config/halo-azusake/halo_mod_config.json`，承载命令权限及实验性兼容开关，
与 `/halo config` 运行时调参（`HaloConfig`）完全
分离。文件缺失或空白时启动自动写入默认值；已有旧配置缺少字段时会自动补齐并保留
未知键；越界值钳制到 0-4；损坏 JSON 回退默认并警告。

| 方法 | 说明 |
|------|------|
| `HaloModConfigStore.load()` | 启动时加载（mod 初始化调用一次） |
| `HaloModConfigStore.getPermissionLevel()` | `/halo` 命令树所需权限等级（默认 2，范围 0-4） |
| `HaloModConfig.getCommandPermissionLevel()` | 当前权限等级 |
| `HaloModConfig.setCommandPermissionLevel(int)` | 设置权限等级（钳制到 0-4） |
| `HaloModConfig.isExperimentalYsmAnchorEnabled()` | YSM 2.6.5 实验性 Head 锚点是否启用（默认 `false`） |
| `HaloModConfig.getExperimentalYsmHeadLocalOffset()` | Head 局部偏移 `[右, 上, 后]`，单位格，默认全零 |

### YSM 2.6.5 实验性兼容

仅支持 Fabric 1.20.1 的 `2.6.5-fabric+mc1.20.1` 发布包。旧配置会在下次启动时自动加入
以下两个实验字段；开关仍默认为 `false`。编辑配置后需要重启：

```json
{
  "commandPermissionLevel": 2,
  "experimentalYsmAnchorEnabled": true,
  "experimentalYsmHeadLocalOffset": [0.0, 0.0, 0.0]
}
```

偏移在 YSM `Head` 骨骼的最终局部坐标系中应用；零向量直接使用模型作者定义的 Head
枢轴。捕获适用于由 YSM 接管渲染的玩家及其他生物；非生物实体会忽略。开启光影时，
本地第一人称玩家固定使用摄像机锚点，避免 Iris 阴影/辅助渲染 pass 的矩阵污染；第三人称
及其他生物继续使用 YSM Head 捕获。YSM 缺失、版本不匹配、模型没有可用 Head 骨骼或
矩阵退化时，Halo 会安全回退到原有实体锚点计算。YSM 不是 Halo 的必需依赖。

---

## 自定义头部锚点（Custom Head Anchor）

Halo 提供一个按实体类型/UUID查找头部锚点的注册表。下文描述的是当前分支的真实接口。

> 重要：8个分支共享相同的核心解析语义，但不是跨 Minecraft 版本的同一个二进制 API；注册入口也会随 Fabric、Forge、NeoForge 和具体版本变化。外部模组必须依赖对应分支的 Halo，并使用该分支的 Minecraft 映射类型与加载器注册方式。

### 数据流与调用契约

`EntityAnchorProvider.resolve(entity, tickDelta)` 在客户端实体渲染线程上计算某个光环的锚点时调用。同一实体拥有多个光环时，一个渲染帧内可能调用多次。实现应返回非空、有限值的 `HeadAnchor`；若本帧数据尚未准备好，应保留上一帧有效值或返回合适的 Vanilla/fallback 锚点。

本分支实体参数类型：`net.minecraft.entity.LivingEntity`。

本分支 `HeadAnchor.headCenter` 类型：`net.minecraft.util.math.Vec3d`；yaw、pitch、roll 使用角度（度，Minecraft 约定）。

注册表的解析顺序为：UUID 精确匹配 → 实体类精确匹配 → 父类链匹配 → fallback。相同键重复注册时，后注册的 provider 覆盖先注册的 provider。

`getProvider(Class<?>)` 在没有专用 provider 时也会返回可用的 fallback provider，不应把返回值当作 nullable API。

### 注册方式

Fabric 分支在客户端初始化时使用 Halo 自己的事件入口：

```java
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.entity.LivingEntity;
import network.azusake.halo.api.AnchorProviderSetupEvent;

public final class MyModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AnchorProviderSetupEvent.EVENT.register(registry ->
            registry.register(LivingEntity.class, new MyHeadProvider()));
    }
}
```

其中 `EVENT` 是 Fabric 侧的 Halo 事件对象；不要把它当成 Forge/NeoForge 原生事件总线来注册。

### 默认优先级与兼容行为

- 外部 provider 只负责提供锚点；注册表的 UUID、精确类、父类链和 fallback 解析规则不因加载器改变。
- 玩家优先使用 Halo 的玩家锚点 provider；EMF 捕获到的玩家头部数据可覆盖该帧锚点，捕获不可用时回退到玩家 provider。
- 当前 EMF 兼容代码只接管玩家。非玩家实体不使用 EMF 捕获，继续走本分支已有的 YSM、Vanilla 或 fallback 路径；这是有意保留的保守策略。
- YSM 兼容是否存在及其配置方式取决于具体分支，和 EMF 兼容相互独立；请以当前分支的 YSM 章节和配置为准。

### EMF/ETF 兼容

EMF 兼容默认开启，不增加 Halo 配置开关；支持版本下界为 EMF 3.1.1，不人为设置上界，并在运行时检测实际 ABI。ETF 不增加独立捕获代码，只进行与 EMF/ETF 共存验证。

若检测到当前 EMF ABI 不兼容，会在日志及聊天栏提示：

> 当前Halo模组的EMF兼容代码无法再适用于加载版本的emf模组，请前往源码库汇报

### API v2 规划（尚待讨论，尚未实现）

当前公开接口是 API v1。以下内容是 API v2 的阶段性结论草案，不是当前可用的接口，也不应据此添加依赖或编写实际调用代码。类型名称、方法签名和具体基准仍可能调整。

- **交付方式**：建立独立的 Java 17 `network.azusake:halo-anchor-api` artifact；Halo mod 在运行时提供相同的 API classes，外部 mod 仅以 compile-only/provided 方式使用该 artifact，不另行安装重复的 API jar。artifact 坐标仍属于暂定值。
- **包与版本**：公共包暂定为 `network.azusake.halo.api.v2`，API 使用独立语义版本；在合同冻结前建议使用 `0.1.x`，达到稳定标准后再发布 `1.0.0`。
- **平台边界**：独立 artifact 不依赖 Minecraft、Fabric、Forge、NeoForge 或 JOML；各分支通过 loader-specific adapter 接入，YSM、EMF 和 Vanilla 捕获逻辑继续留在分支内部。
- **姿态与坐标**：使用世界坐标 `AnchorVec3` 和四元数 `AnchorRotation`，四元数为唯一规范表示，分量顺序暂定为 `(x, y, z, w)`。坐标固定遵循 Minecraft 世界轴：`+X` 东、`+Y` 上、`+Z` 南；姿态表示锚点局部轴到世界轴的旋转。
- **请求上下文**：provider 接收实体快照和渲染帧上下文，不直接接收 Minecraft entity。核心帧上下文只要求 `frameId` 与 `tickDelta`，不把相机信息作为必选 API；第三方头部捕获由各分支 adapter 转换为承诺的头部信息。
- **实体标识**：使用校验型 `EntityTypeId` 或 UUID selector，替代 v1 的 `Class<?>`；不把 loader-specific 类型泄漏到公共合同。
- **解析规则**：UUID selector 的具体性高于 type selector；同一具体性内按有序整数 priority 排序，priority 相同时按注册顺序决定结果。provider 返回 `unavailable()` 时继续尝试下一个匹配项，全部不可用时再进入 v1 fallback。
- **生命周期**：注册返回可 `close()` 的句柄，句柄默认持续到客户端生命周期结束或显式关闭；世界切换只清理帧缓存，不强制移除客户端级注册。
- **失败语义**：使用显式的 `AnchorResult.resolved(...)` 与 `AnchorResult.unavailable()`，禁止用 `null` 表示正常不可用。adapter 负责有限值校验、隔离运行时异常并记录诊断，单个 provider 不应破坏当前 halo 渲染。
- **兼容顺序**：v2 provider 先解析；v2 没有返回可用姿态时调用现有 v1 registry。现有 `EntityAnchorProvider`、YSM/EMF 行为和外部 v1 mod 保持不变。

以下是非编译性的类型草案，仅用于讨论合同形状：

```java
interface AnchorProvider {
    AnchorResult resolve(AnchorRequest request);
}

record AnchorRequest(EntitySnapshot entity, FrameContext frame) {}

record EntitySnapshot(
    UUID uuid,
    EntityTypeId typeId,
    AnchorVec3 interpolatedPosition,
    AnchorRotation headRotation,
    double height
) {}

record FrameContext(long frameId, double tickDelta) {}

record AnchorPose(AnchorVec3 position, AnchorRotation rotation) {}
```

实施顺序暂定为：先冻结 RFC 和固定向量测试，再建立纯 Java API 子项目；随后在八个分支分别实现 adapter，在 `AnchorFrameCalculator` 接入 v2-first/v1-fallback；最后迁移或包装 Vanilla、YSM、EMF provider，并执行八分支编译、合同测试和迁移验证。

公共类型的最终命名、`frameId` 在辅助渲染 pass 中的规则、内置 provider 的 priority 保留区间、`unavailable` 是否携带公开 reason，以及支持基准版本，均尚待后续讨论后冻结。

### 注册建议

外部模组通常应注册自己的具体实体类，而不是无条件覆盖 `LivingEntity.class`；如需包装已有 provider，应先保存已有 provider，再在自定义 provider 中委托并只调整头部锚点。不要在渲染回调之外缓存跨实体的临时状态，也不要假设所有实体都有相同的模型部件结构。

## 网络通道

| 通道 | 方向 | 用途 |
|------|------|------|
| `halo:sync` | S2C | 玩家加入时全量状态快照 |
| `halo:update` | S2C | 增量 附加/移除 广播 |
| `halo:defs_report` | C2S | 客户端报告本地可用的定义 ID |
| `halo:hello` | S2C | 握手——宣告服务器已安装 mod |
