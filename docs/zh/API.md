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
| `HaloModConfig.isExperimentalYsmAnchorEnabled()` | YSM 2.6.5 Head 锚点是否启用（默认 `true`，设为 `false` 可关闭） |
| `HaloModConfig.getExperimentalYsmHeadLocalOffset()` | Head 局部偏移 `[右, 上, 后]`，单位格，默认全零 |

### YSM 2.6.5 实验性兼容

仅支持 Forge 1.20.1 的 `2.6.5-forge+mc1.20.1` 发布包。旧配置会在下次启动时自动加入
以下两个字段；开关默认为 `true`，如需关闭 YSM 捕获可设为 `false`。编辑配置后需要重启：

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

## 头部锚点 API v2

### 开发者快速接入

外部渲染模组可按以下顺序接入。

#### 添加 Halo 构建依赖和加载器依赖

把与目标 Minecraft 版本及加载器匹配的 Halo 1.3.0 jar 放入外部模组的 `libs`
目录。只参与编译，不要把 Halo 打进外部模组自己的 jar：

```groovy
dependencies {
    compileOnly files("libs/halo-<minecraft>-<loader>-1.3.0.jar")
}
```

运行时仍须把 Halo 作为独立模组安装。若该适配是必需功能，还应声明加载器依赖，
防止外部模组在缺少 Halo 或 Halo 版本过旧时启动：

```json
{
  "depends": {
    "halo": ">=1.3.0"
  }
}
```

```toml
# Forge 1.20.1：META-INF/mods.toml
[[dependencies.your_mod_id]]
modId="halo"
mandatory=true
versionRange="[1.3.0,)"
ordering="AFTER"
side="CLIENT"

# NeoForge：META-INF/neoforge.mods.toml
[[dependencies.your_mod_id]]
modId="halo"
type="required"
versionRange="[1.3.0,)"
ordering="AFTER"
side="CLIENT"
```

若 Halo 是可选依赖，应先通过加载器检查 Halo 是否存在，并把所有直接 API 引用隔离在
专用兼容类中；Halo 不存在时不得加载该类。

#### 注册一个 source 并保存句柄

每个适配注册一个 source，不要为每个实体或每一帧重复注册：

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

ID 必须匹配 `[a-z0-9_.-]+:[a-z0-9_./-]+`。非法 ID 会抛出
`IllegalArgumentException`，活动 ID 重复注册会抛出 `IllegalStateException`。
`close()` 幂等，会立即清除此 source 的缓存；关闭后可用同一 ID 重新注册。

#### 在基础头部最终变换处提交

必须在实际渲染器完成当前实体头部最终视觉变换的位置同步调用 bridge，通常是在最终
Head 模型或 locator 变换之后。包装其他渲染器的模组，应在自身最后一次调整完成后提交。

不要从客户端 tick、帧末回调、工作线程、盔甲层或无关 feature model 提交。传入的 UUID
必须属于当前正在渲染的实体。调用必须仍处于 Halo 的实体渲染范围内；如果延后到其他
回调，`submit` 将返回 `false`。

#### 将渲染坐标转换为 API 坐标

位置必须是以方块为单位的绝对世界坐标，而不是模型坐标或摄像机/view 坐标。若渲染器
提供的是 view-relative 最终矩阵，应先消除视角旋转，再加上摄像机世界位置：

```text
worldPosition = cameraWorldPosition + inverse(viewRotation) * viewPosition
worldRotation = inverse(viewRotation) * viewRotationOfHead
```

构造四元数前应提取正交归一的旋转；缩放和切变不能写入 `AnchorRotation`。JOML
四元数的分量可以保持原顺序传入：

```java
bridge.submit(entityUuid, worldX, worldY, worldZ,
    worldRotation.x(), worldRotation.y(),
    worldRotation.z(), worldRotation.w());
```

#### 处理提交结果

`submit(...) == true` 表示姿态已被当前主视角实体 pass 接受。阴影/辅助或未知 pass、
错误或空 UUID、空 pose、渲染范围外调用、已关闭 source 都会返回 `false`。被拒绝的
提交不会覆盖已有有效姿态。不要离开渲染器后再次补交；如果预期的第三人称主 pass 持续
返回 `false`，只需限频记录诊断并检查 UUID、调用点、渲染器嵌套和 pass 判定。

延迟渲染器应在头部实际绘制时提交，而不是在 extraction 入队时提交。Halo 最多保留一个
主帧的有效捕获，并根据实体当前插值位置重基准。世界切换、实体卸载和 source 关闭都会
使捕获失效。本地第一人称玩家使用 Halo 的摄像机锚点，不使用外部实体提交。

发布前应覆盖第三人称连续移动、开关光影、实体卸载、世界切换、运行时关闭 source，以及
关闭后用同一 ID 重新注册。

Halo 1.3.0 内置跨八分支一致的推送式 API v2。外部模组应把目标 Minecraft/加载器版本的 Halo jar 声明为 `compileOnly` 或 `provided`；无需也不应安装独立 API jar。API v1 已移除。

公共包 `network.azusake.halo.api.v2` 只依赖 Java 17/JDK 类型：

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

### 注册与提交

在客户端初始化时注册一次 source，并在实际渲染器完成该实体最终头部变换的位置提交：

```java
private static final AnchorSource SOURCE = HaloAnchorApi.register("example:custom_head");

// 位于实体头部最终渲染变换处：
boolean accepted = SOURCE.submit(entityUuid, new AnchorPose(
    new AnchorVec3(worldX, worldY, worldZ),
    new AnchorRotation(qx, qy, qz, qw)
));
```

`sourceId` 必须是唯一的小写 `namespace:path`。活动 ID 重复注册会抛出异常；`close()` 幂等，关闭后同一 ID 可重新注册。句柄通常持续整个客户端进程；世界切换只清理捕获，不移除注册。关闭 source 会立即删除它的缓存，之后提交返回 `false`。

同一主视角实体 pass 内，最后一次有效提交代表最终渲染结果。包装其他渲染器时，必须在自身最终变换完成后提交。`submit` 只在 Halo 当前识别的主视角实体渲染范围内、且 UUID 与当前实体一致时返回 `true`；阴影或辅助 pass、未知 shader pass、错误 UUID、范围外调用和已关闭 source 均返回 `false`，不会覆盖已有有效锚点。

### 坐标与姿态

- `AnchorVec3` 是绝对世界坐标，单位为方块：`+X` 东、`+Y` 上、`+Z` 南。
- `AnchorRotation` 是 `(x,y,z,w)` 四元数。有限且非零的输入会在构造时归一化；零或非有限值会被拒绝。
- 旋转后的本地 `+Y` 是头顶方向，本地 `+Z` 是视线前方，实体右侧是旋转后的 `-X`。

### 多 pass、延迟帧与回退

Halo 将主相机根矩阵、主视锥、当前实体以及可用的 Iris shadow-pass 状态组合为安全门。检测到渲染后端但无法确认 pass 时会拒绝提交并限频记录诊断。Iris 后续的阴影/辅助渲染不会推进主帧，也不能覆盖主锚点。

捕获最多保留一个主帧，以兼容延迟绘制；位置按实体相对坐标缓存，消费时叠加实体当前插值位置，因此上一帧摄像机或实体移动不会造成偏移。世界切换、实体卸载或 source 关闭会立即使相关捕获失效。没有有效提交时，玩家使用姿态配置回退，其他生物使用 Vanilla 高度/yaw/pitch 回退；本地第一人称玩家始终以摄像机锚点为准。

Halo 内置 `halo:vanilla`、`halo:emf` 和受支持版本上的 `halo:ysm`。Vanilla 与 EMF 仅捕获玩家头部；YSM 保留其既有全生物范围。EMF 仍仅把 ETF 作为共存验证对象。

## 网络通道

| 通道 | 方向 | 用途 |
|------|------|------|
| `halo:sync` | S2C | 玩家加入时全量状态快照 |
| `halo:update` | S2C | 增量 附加/移除 广播 |
| `halo:defs_report` | C2S | 客户端报告本地可用的定义 ID |
| `halo:hello` | S2C | 握手——宣告服务器已安装 mod |
