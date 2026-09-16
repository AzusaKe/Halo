# Halo Mod — 公开 API

## 从这里开始：接入 API v2

本文以 **Halo 2.3.0、Minecraft 1.20.1 Fabric、Java 17** 为基准。其他已接入宿主可以复用中立接口，但旧版／冻结适配器不因此自动拥有预览能力。
API v2 为**已经佩戴的光环提供头部锚点**，不负责佩戴光环或绘制任意几何。先用 `/halo list` 查找定义，再用 `/halo show @s <定义ID>` 佩戴一个光环。

阅读顺序：[1. 依赖配置](#dependency-setup) → [2. 模型提供者](#provider-routing) → [3. 预览面板](#preview-host-integration) → [4. 接口参考](#api-v2-reference) → [5. 验证排错](#integration-checks)。[其他接口](#other-interfaces)位于 API v2 指南之后。

| 你要实现什么 | 接入路线 |
| --- | --- |
| 世界中的自定义实体／模型渲染器 | 完成共用配置后，接入[世界提交](#world-anchor-api) |
| 同时用于物品栏／预览的模型渲染器 | 复用来源句柄，[按每次绘制的空间分流](#provider-routing) |
| 可以通过 `InventoryScreen.drawEntity` 绘制真实玩家的面板 | 调用原版 helper，Halo 自动管理玩家预览 |
| 使用代理实体、直接绘制模型或自定义相机的面板 | 实现[独立预览宿主](#independent-preview-host) |

模型提供者只需要 `network.azusake.halo.api.v2`；宿主另需 `core.runtime` 和 `core.render` 契约。即使只接预览，也先完成[共用依赖配置](#dependency-setup)。

<a id="dependency-setup"></a>

## 1. 依赖配置与客户端初始化

把与目标 Minecraft 版本及加载器匹配的 Halo 2.3.0 jar 放入自己模组的 `libs` 目录。编译时引用，不要把 Halo 打进自己的模组：

```groovy
dependencies {
    modCompileOnly files("libs/halo-1.20.1-fabric-2.3.0+adapter.1.jar")
    modLocalRuntime files("libs/halo-1.20.1-fabric-2.3.0+adapter.1.jar")
}
```

这是 Fabric Loom 配置：将生产模组 jar 重映射到开发环境，`modLocalRuntime` 还会在 `runClient` 中加载它。文件名替换为实际下载名称。仅使用中立 API 类型的普通 Java 工程可用 `compileOnly files(...)`；其他加载器使用相应工具链的重映射配置。不要 `include` 或 shade Halo/core，也不安装独立 API jar。当前受支持发布为 [1.20.1 Fabric 的 2.3.0](https://github.com/AzusaKe/Halo/releases/tag/v2.3.0-fabric-1.20.1-adapter.1)。

运行时仍须把 Halo 作为独立模组安装。若该适配是必需功能，在 `fabric.mod.json` 添加以下依赖：

```json
{
  "depends": {
    "halo": ">=2.3.0"
  }
}
```

以下元数据仅供**未来／匹配的 Forge、NeoForge 适配器**参考，不表示这些平台已有 2.3.0 成品：

```toml
# Forge 1.20.1：META-INF/mods.toml
[[dependencies.your_mod_id]]
modId="halo"
mandatory=true
versionRange="[2.3.0,)"
ordering="AFTER"
side="CLIENT"

# NeoForge：META-INF/neoforge.mods.toml
[[dependencies.your_mod_id]]
modId="halo"
type="required"
versionRange="[2.3.0,)"
ordering="AFTER"
side="CLIENT"
```

若 Halo 是可选依赖，应先通过加载器检查 Halo 是否存在，并把所有直接 API 引用隔离在专用兼容类中；Halo 不存在或版本低于所调用 API 时不得加载该类。Fabric 可使用 `FabricLoader.getInstance().isModLoaded("halo")` 加版本检查（或可选的 `"suggests": {"halo": ">=2.3.0"}` 配合 `"breaks": {"halo": "<2.3.0"}`）。仅在客户端入口注册，服务端类加载不得初始化 bridge。

<a id="preview-anchor-provider-api"></a>
<a id="provider-routing"></a>

## 2. 模型提供者：世界与预览

一次 `HaloAnchorApi.register(id)` 返回一个可用于世界、预览或两者的来源句柄。内置 YSM／EMF 也使用这条公开路径。提供者只负责头部；会话、物理、资源和绘制由宿主管理。

### 一个模型钩子，两种空间

来源句柄保留整个客户端生命周期。**在基础头部最终变换完成后、实体／模型绘制尚未退出时同步调用**下面的 bridge。两个回调是你自己的模型采集函数，不是 Halo 方法：按下文转换为对应姿态，无法采集时返回 `null`。

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

在确认依赖可用后，从客户端初始化入口创建一次实例。永久禁用适配时关闭，不要在帧结束或关闭界面时关闭。仅接预览时让世界回调返回 `null`；仅接世界时跳过整个预览分支。**预览上下文为空不代表可以提交世界姿态**：无关嵌套实体和正在退出的失效预览也会返回空上下文。不得保存上下文，或把回调延迟到其他线程／帧。

<a id="world-anchor-api"></a>

### 世界提交：选择钩子位置

向 `ModelHaloBridge.capture` 传入当前绘制实体的 UUID／运行时 ID，并从世界回调返回 `AnchorPose`。使用基础头部模型或 locator 的最终变换；包装渲染器应先完成自身调整。不要从客户端 tick、帧末回调、工作线程、盔甲层或无关 feature model 采集。延迟渲染器应在头部实际绘制时提交，而不是 extraction 入队时，且必须仍处于 Halo 接受的实体渲染作用域。

仅接世界时，用下面的方法将已换算的坐标接到共用 bridge（导入 `java.util.UUID` 和 `network.azusake.halo.api.v2.*`）：

```java
static boolean submitWorldHead(ModelHaloBridge bridge, UUID uuid, int runtimeId,
        double worldX, double worldY, double worldZ, AnchorRotation worldRotation) {
    return bridge.capture(uuid, runtimeId, context -> null,
        () -> new AnchorPose(new AnchorVec3(worldX, worldY, worldZ), worldRotation));
}
```

预览绘制时，同一个 bridge 会将当前上下文交给预览回调。匹配的是**实际绘制实体**，它可能不同于佩戴者。与世界不同，预览首次有效模型提交获选，包装层需要协调最终提交者而非事后覆盖。完整选择和生命周期规则见[接口参考](#api-v2-reference)。

### 从自己的渲染器生成姿态

采集模型选定的最终头部视觉中心／枢轴，不要叠加光环定义偏移、缩放、动画或物理。API 本地轴为 **+Y 头顶、+Z 头前、-X 头右**；只有模型本身采用这些轴向时，才能直接使用模型四元数。

按列向量约定，设 `H` 为头部局部坐标到视图空间的最终矩阵，`C` 将“API 轴向、方块单位的头部坐标系”转换到模型局部单位和轴向（包括选定中心），则：

```text
previewHeadInScene = inverse(context.sceneToView()) * H * C
worldHead          = Translate(cameraWorldPosition) * inverse(viewRotation) * H * C
```

`H` 必须包含与上下文一致的视图／GUI 根矩阵；两者均不含投影。已经采集到场景／世界坐标时不要再次消除根变换。世界矩阵若只减去了相机位置、未包含视图旋转，则只需加回相机位置。仅当模型确实使用每格 16 单位时进行该单位换算，且只能换算一次。

例如渲染器使用 JOML 时（JOML 不属于公开 API）：

```java
// headToView、apiHeadToModel 由你自己的渲染器提供。
Matrix4f headToScene = new Matrix4f().set(context.sceneToView()).invert()
    .mul(headToView).mul(apiHeadToModel);
Vector3f center = headToScene.transformPosition(new Vector3f());
// 仅适用于已转换轴向、正的均匀缩放且无切变的矩阵。
Quaternionf rotation = headToScene.getUnnormalizedRotation(new Quaternionf()).normalize();
PreviewAnchorPose pose = new PreviewAnchorPose(center.x, center.y, center.z,
    new AnchorRotation(rotation.x, rotation.y, rotation.z, rotation.w));
```

此片段需导入 `org.joml.Matrix4f`、`Vector3f`、`Quaternionf`。先通过根矩阵逆变换去除 GUI 镜像；非均匀缩放／切变需要对最终上方和前方方向正交归一化，再重建右手旋转，四元数不能表达反射。矩阵不可逆、轴退化或存在非有限值时跳过提交。姿态值的构造器会校验输入，可能抛出 `IllegalArgumentException`；`submit` 的 `false` 返回值不能替你捕获构造错误。世界平移保留 double 精度，尤其是大坐标场景。

<a id="preview-host-integration"></a>

## 3. 预览面板接入

### 使用原版玩家预览 helper

Minecraft 面板最简单的接法是通过 `InventoryScreen.drawEntity` 绘制**真实玩家**。Halo 1.20.1 Fabric 会包装这次玩家绘制、刷新模型缓冲、捕获头部并绘制光环；非玩家实体不在自动覆盖范围内。不要重复包装 helper。此路径遵循 `playerPreviewHaloEnabled` 和 `playerPreviewHaloPhysicsEnabled`，两者默认开启；将后者设为 `false` 并重启可切换到刚性随头。

2.3.0 已移除 Minecraft 专用的 `HaloPreviewApi`，不提供兼容转发类。原版入口所需功能移至
`render.PlayerPreviewRenderer` 内部实现；其 Java `public` 可见性仅用于适配器包间调用，
不属于对外稳定 API。外部兼容包不应依赖它，也不需要复制它的物理或图元代码。

<a id="independent-preview-host"></a>

### 独立预览面板：宿主接入流程

完全独立的面板需要平台桥接：中立 API 提供计算，不提供 Minecraft 绘制 helper。桥接必须提供**所属客户端现有的 `PreviewPort`**、资源快照与 GPU 消费端。API v2 没有受支持的静态方法获取 Halo 当前运行实例或资源；新建一个 `ClientRuntime` 不会自动继承 Halo 的佩戴状态。如果 UI 不能复用原版 helper，需要在平台适配器中提供此桥接，下文就是它的实现契约。

1. 每个逻辑客户端保留一个 `PreviewAnchorHost`，每个视图保留一个 `PreviewSession`。通过 `previewPort.openPreview(PreviewOptions.PHYSICS)` 创建物理会话，或选择 `RIGID`；无参 `openPreview()` 是刚性会话。同一佩戴者的两个面板需要两个会话。
2. 所属适配器每个逻辑渲染帧调用一次 `ClientPort.renderFrame`，即使佩戴者被剔除或处于第一人称，也应包含已加载的佩戴者。预览消费最近完成的表现快照，不自行推进佩戴／动画；不能每个面板额外跑一次世界帧。
3. 用与目标模型绘制一致的矩阵，在绘制周围开放采集作用域。平台实体 dispatcher 必须对**所有**实体（包括嵌套实体）配对调用 `PreviewAnchorHost.beginEntityRender(uuid, runtimeId)` / `endEntityRender()`，用 `try/finally` 保证退出。Halo 1.20.1 Fabric 已安装这些钩子，不要重复安装。绕过 dispatcher 直接画模型时，只能在实际绘制目标模型期间保持作用域。
4. 提供者在模型钩子中提交；宿主可对原版实际捕获调用 `scope.submitFallback(sceneHead, PreviewAnchorScope.Fallback.RENDERED)`，只有姿态准备时用 `POSED`。回退使用同一场景坐标。绘制后解析结果；`null` 表示本次跳过光环。
5. 刷新实体缓冲，调用 `session.render(frame)`，然后在所需投影／光照仍有效时依次消费 `FrameOutput.legacyBatches()` 和 `meshes()`。批次顶点已经处于视图空间，mesh 命令提供局部到视图矩阵，不能再次应用 GUI 根矩阵。遵循材质、纹理、遮罩、光照、深度／混合／剔除状态并恢复所修改的 GPU 状态。具体提交规则见 [帧输出契约](../../core/README.md#frame-and-coordinate-contract)和 [mesh 契约](../../core/README.md#mesh-adapter-contract-210)。

下面的方法完整连接中立的**采集 → 帧输入 → 输出**；实体绘制和 GPU 后端仍由平台实现，所有回调和参数都由适配器传入。导入 `java.util.UUID`、`java.util.function.Consumer`、`network.azusake.halo.api.v2.*`、`network.azusake.halo.core.render.*` 和 `network.azusake.halo.core.runtime.*`。

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
        drawModel.accept(scope); // 实际绘制；提供者／回退在此采集。
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

该重载默认正交投影。透视面板使用 `PreviewFrame` 构造器最后一个参数 `PreviewFrame.Projection.PERSPECTIVE`。此处转换出的 `AnchorPose` 仅作为 `PreviewFrame` 的值，不能传给世界 `source.submit`。

| 输入 | 适配器应提供什么 |
| --- | --- |
| 佩戴者 UUID／运行时 ID | 所属客户端表现快照中的真实存活实体；代理只改变绘制身份 |
| `sceneToView` | 列主序 4×4，`rootTransform * Translate(-camera.position)`，不含投影 |
| 相机与根矩阵 | 相机位置为场景方块坐标，上／右方向处于最终视图空间；零相机时根矩阵等于 `sceneToView`，相机平移只能计算一次 |
| 时间 | 当前毫秒时间与单调纳秒时间；同一样本重复绘制复用同一纳秒值 |
| 光照／纹理／资源 | 有效原生光照（UI 通常用 `LightSample.FULL_BRIGHT`）、纹理可用性、所属适配器同代次 `VisualResources` |

跨帧保留会话，视图结束时关闭，`isValid()` 为 false 时重建。客户端 `clear()`、`replace()`、世界 token 变化会使会话失效；资源重载后需等待新的表现／资源快照才恢复输出。场景／模型不连续变化时调用 `session.resetMotion()`。换世界、断线或销毁宿主时，在所属线程调用 `host.clear()`，仍须按嵌套逆序关闭活动作用域。捕获作用域和视图物理会话是两个生命周期：每次绘制关闭作用域，不应同时关闭保留的会话。

宿主细节另见[采集契约](../preview-anchor-api.md#平台宿主接入)和 [core 会话契约](../../core/README.md#preview-contract-since-220)。

<a id="api-v2-reference"></a>

## 4. API v2 接口参考

世界提交始于 Halo 1.3.0；2.3.0 将预览加入同一入口。以下类型均位于 `network.azusake.halo.api.v2`，仅使用 Java 17/JDK 类型。这是签名摘要，不是需要复制到自己模组中的源码：

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

### 来源身份与生命周期

`sourceId` 必须匹配 `[a-z0-9_.-]+:[a-z0-9_./-]+`。非法 ID 抛出 `IllegalArgumentException`；活动 ID 重复注册抛出 `IllegalStateException`。ID 在世界与预览之间共用且唯一。注册跨世界切换保留；`close()` 幂等，立即撤销此来源在两侧的贡献，后续提交返回 `false`。之后可用同一 ID 重新注册，再次关闭旧句柄不会撤销新句柄。两侧需要独立注销时使用不同 ID；只提交一侧不影响另一侧。

### 坐标和值类型

| 值 | 契约 |
| --- | --- |
| 世界 `AnchorPose.position` | 方块单位的绝对世界坐标：+X 东、+Y 上、+Z 南 |
| `PreviewAnchorPose` 位置 | `sceneToView` 之前的方块单位预览场景，不含 GUI 像素、投影或世界位置 |
| `AnchorRotation` | 归一化 `(x,y,z,w)` 四元数；本地 +Y 头顶、+Z 头前、-X 头右 |

位置必须有限。旋转构造时归一化有限非零输入，拒绝零、非有限值或溢出的模长。两种姿态均不含光环偏移、物理和动画。矩阵处理见[姿态换算](#从自己的渲染器生成姿态)。

### 预览上下文

| 成员 | 含义 |
| --- | --- |
| `wearer()` / `runtimeId()` | 提供佩戴与表现状态的真实实体 |
| `renderedEntity()` / `renderedRuntimeId()` | 实际绘制的模型实体，可以是 UI 代理；用这组身份匹配模型钩子 |
| `renderId()` | 本次绘制诊断编号，不是跨帧视图／会话键 |
| `sceneToView()` | 列主序 4×4 场景到视图矩阵副本，不含投影 |
| `isActive()` | 当前线程、实体和视图作用域是否允许提交 |
| `hasModelAnchor()` | 已有仍注册的模型来源获选，不包含原版回退 |

上下文属于一次绘制，提供者不能自行实现或跨帧保存。嵌套视图暂停外层上下文，退出后恢复。无关嵌套实体及失效作用域的 `currentPreviewContext()` 返回空值，但 `isPreviewRendering()` 在作用域退出前仍为 true。

### 接受、选择与回退

| 规则 | 世界 `submit` | 预览 `submitPreview` |
| --- | --- | --- |
| 接受范围 | 已识别的主相机实体 pass，且 UUID 匹配 | 所属渲染线程上的活动上下文，目标实体／视图匹配 |
| 选择 | 最后一次有效样本获选 | 第一个有效模型获选；模型 > 原版实际绘制回退 > 仅姿态准备回退 |
| 拒绝（`false`） | 空／错误 UUID、空姿态、已关闭来源、作用域外、GUI、阴影／辅助或未知 shader pass | 空姿态、已关闭来源、过期／伪造／非活动上下文、错误线程／实体或已有模型获选 |
| 保留 | 当前／前一个主帧；实体相对位置按当前插值位置重基准 | 仅本次绘制；不复用上一帧或世界缓存 |
| 失效 | 换世界、实体卸载或来源关闭 | 作用域退出／宿主 clear 或来源关闭；嵌套视图暂时暂停提交 |

拒绝的提交不覆盖有效姿态。预览中注销获选来源后可使用回退或接受新的模型提交，之前被拒绝的样本不会重放。每种原版回退也只接受第一次有效样本。没有任何预览提供者／回退时，本次不绘制光环。

世界宿主通过主相机根矩阵、主视锥、当前实体及可用的 Iris shadow-pass 探测判定 pass。检测到但无法分类的后端会被拒绝并限频诊断；后续阴影／辅助 pass 不推进主帧。没有有效世界捕获时，玩家使用姿态配置回退，其他生物使用原版高度／yaw／pitch。本地第一人称玩家始终使用摄像机锚点。

内置来源为 `halo:vanilla`、`halo:emf` 和受支持 YSM 版本的 `halo:ysm`。Vanilla 与 EMF 捕获玩家头部，YSM 捕获生物。ETF 是共存验证对象，不是独立捕获来源。

<a id="integration-checks"></a>

## 5. 验证与排错

1. 佩戴一个已知光环，确认第三人称显示，再打开玩家预览。如果曾关闭预览，在 `config/halo-azusake/halo_mod_config.json` 中启用 `playerPreviewHaloEnabled` 并重启。
2. 移动／旋转模型头部：锚点应只跟随一次，光环偏移由 Halo 处理。单独测试 GUI 移动／缩放，它们不应产生预览物理作用力。
3. 限频记录接受／拒绝结果、来源 ID、绘制 UUID／运行时 ID、预览 `renderId()`。`true` 仅表示已接受，不保证可见：隐藏、死亡、未加载、缺少定义／资源／表现快照仍可能没有绘制。不要离开渲染器后补交被拒绝的样本。
4. 测试同一佩戴者的双面板、关闭重开、资源重载、实体卸载、换世界、第三人称、光影、运行时注销来源及重新注册。代理实体必须由宿主映射身份，其 UUID 不会自动继承真实佩戴者的表现。

| 现象 | 检查 |
| --- | --- |
| 预览上下文始终为空 | 使用受支持的玩家 helper 或开放自定义宿主作用域；检查绘制身份和同步钩子 |
| 预览提交总是未获选 | 是否已有其他模型提供者提交；不能依赖世界的后提交覆盖规则 |
| 预期的世界主 pass 提交失败 | UUID、最终头部钩子、渲染嵌套和 pass 判定 |
| 偏移随 GUI 缩放／相机距离变化 | 根矩阵／相机应用了两次、单位错误或采集含投影 |
| 锚点正确但无光环 | 佩戴状态、显示开关、真实佩戴者运行时 ID、最新表现帧和资源代次 |
| 物理每帧都 snap | 每个视图保留一个 `PreviewSession`，不要每次绘制重新创建 |

---

<a id="other-interfaces"></a>

## 其他接口

以下章节介绍命令拦截、生命周期／配置与网络。

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

仅支持 Fabric 1.20.1 的 `2.6.5-fabric+mc1.20.1` 发布包。旧配置会在下次启动时自动加入
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

## 网络通道

| 通道 | 方向 | 用途 |
|------|------|------|
| `halo:sync` | S2C | 玩家加入时全量状态快照 |
| `halo:update` | S2C | 增量 附加/移除 广播 |
| `halo:defs_report` | C2S | 客户端报告本地可用的定义 ID |
| `halo:hello` | S2C | 握手——宣告服务器已安装 mod |
