# API v2：世界与预览锚点（预览能力自 2.3.0 起）

API v2 统一提供世界和预览锚点入口，让模型兼容包只提供真实头部，预览宿主负责会话、物理、资源和绘制。公开类位于 core 的 `network.azusake.halo.api.v2`，保持 Java 17，仅使用 core/JDK 类型。各采用统一 core 的游戏版本和加载器共用同一份接口；不要求迁移冻结的 flash 分支，也不表示那些分支已经提供该能力。

## 提供者接入

```java
import network.azusake.halo.api.v2.*;

// 初始化一次，来源可以跨界面和重连保留。id 使用自己模组的命名空间。
AnchorSource source = HaloAnchorApi.register("example:custom_model");

// 在自己的模型渲染钩子中：头部准备/动画已经完成后。
PreviewAnchorContext context = HaloAnchorApi.currentPreviewContext();
if (context != null && !context.hasModelAnchor()) {
    PreviewAnchorPose head = captureMyPreviewHead(context); // 自己的实际模型采集
    source.submitPreview(context, head);
}

// 永久卸载时注销此来源在世界和预览中的贡献；不要每帧注册/注销。
source.close();
```

`captureMyPreviewHead` 是调用方的方法，并非 Halo API。YSM／EMF 已通过同样的来源注册和提交路径接入，没有内部锚点提交特权。

仅提供头部的兼容包不需要创建 `PreviewSession`、构造完整 `PreviewFrame` 或调用 GPU 绘制。它仍需适配自己模组/游戏版本的模型渲染钩子；统一 API 不消除第三方模型实现本身的差异。依赖 Halo 提供 API 实现，不要把 core/API 类复制或打包进兼容模组。

新提供者要求 Halo/core 2.3.0 或更高版本，应在对应加载器的依赖元数据中声明最低版本。`api.v2` 在后续适配器中保留现有类名、方法签名、构造入口及坐标/生命周期语义；扩展优先新增能力，不兼容的新契约应使用新 API 命名空间并保留已发布的 v2 契约。第三方模型钩子的跨版本差异仍由兼容包处理。

### 上下文中的身份

| 字段 | 含义 |
| --- | --- |
| `wearer()` / `runtimeId()` | 提供佩戴和表现状态的真实实体 |
| `renderedEntity()` / `renderedRuntimeId()` | 本次实际绘制的模型实体；可以是 UI 临时实体 |
| `renderId()` | 进程内本次绘制的诊断编号；每次调用都不同，不是跨帧物理会话 ID |
| `sceneToView()` | 预览场景到视图空间的列主序 4×4 矩阵副本，不含投影 |
| `isActive()` | 当前线程、视图和实体是否仍允许这个上下文提交 |
| `hasModelAnchor()` | 当前是否已有未注销的模型提供者提交；不包含原版回退 |

上下文是本次绘制的句柄，不应自行实现、复制或跨帧使用。嵌套新预览时，外层上下文暂时不能提交；内层关闭后恢复外层。绘制其他实体（例如宠物）时，`currentPreviewContext()` 返回 `null`。同 UUID 的多个预览按各自作用域隔离。

`isPreviewRendering()` 在预览中的其他实体绘制阶段也返回 `true`，用于区分 GUI 与世界路径。宿主失效后的作用域在 `close()` 前仍被认作 GUI，避免退出过程中误进入世界路径；此时 `currentPreviewContext()` 返回 `null`。

### 坐标

`PreviewAnchorPose(x, y, z, rotation)` 表示本次最终头部视觉中心：

- 位置以方块为单位，处于 `sceneToView` 之前的预览场景空间；不含 GUI 像素定位、缩放或镜像。
- `rotation` 使用现有的中立 `AnchorRotation` 四元数：本地 +Y 为头顶，+Z 为头部前方。该值类型的复用不代表通过世界 API v2 提交。
- 不包含光环定义偏移、光环缩放、阻尼、物理或光环视觉动画；这些由共享管线计算。
- 如果采集到的模型矩阵已经包含 GUI 根变换，需要先去掉根变换，再按模型的枢轴、轴向和模型单位换算。不能直接提交像素坐标或实体世界坐标。
- `sceneToView()` 返回副本。位置必须有限，旋转必须有限且非零；`AnchorRotation` 会归一化四元数。矩阵/模型无效时应跳过本次提交。

原版、YSM 和 EMF 的现有采集数学保留在相应适配层，最终均提交上述中立姿态。新接口未新增 YSM 反编译符号或算法。

### 选择与失败规则

1. 当前绘制内第一个有效模型提供者获选，内置兼容与外部来源同级；后续模型提交返回 `false`，不覆盖已接受的头部。
2. 模型提供者优先于原版实际渲染回退；实际渲染回退优先于仅完成姿态准备的回退。每种回退同样只接受第一次提交。
3. 来源注销后，其尚在作用域中的贡献失效，可使用原版回退或接受新的模型提交；之前被拒绝的提供者不会自动重放。
4. 来源关闭、姿态为空、上下文过期/伪造/被嵌套视图暂停、实体不匹配或错误线程时，`submitPreview` 返回 `false`。
5. 来源 ID 格式为小写 `namespace:path`，重复注册尚未关闭的 ID 抛出异常。来源关闭幂等；注册/注销可跨线程，实际采集和提交必须在宿主渲染线程进行。
6. 不复用上一帧或世界锚点，不推进生命周期或物理。没有有效提供者及回退时，本次没有预览光环。

这是一套确定的“首次有效模型提交”规则，没有隐藏优先级或来源名称白名单。需要改变模型归属的多个兼容包应避免同时声明同一次模型渲染。

## 平台宿主接入

宿主桥接位于 core 的 `core.runtime.PreviewAnchorHost` / `PreviewAnchorScope`，同样没有 Minecraft 类型。

```java
PreviewAnchorHost host = new PreviewAnchorHost(); // 每个逻辑客户端一个，跨帧保留

try (PreviewAnchorScope scope = host.open(
        wearerUuid, wearerRuntimeId, renderedUuid, renderedRuntimeId, sceneToView)) {
    // 平台实体 dispatcher 的 begin/end 钩子只安装一次。
    // 在正常的实体 render 调用周围执行，嵌套实体也需要配对。
    PreviewAnchorHost.beginEntityRender(renderedUuid, renderedRuntimeId);
    try {
        renderEntity(); // 提供者在自己的钩子中通过公开 API 提交
    } finally {
        PreviewAnchorHost.endEntityRender();
    }
    PreviewAnchorPose head = scope.resolved();
    // 提交模型缓冲，用 head 构造预览输入并执行已有物理/绘制会话。
}
```

真实佩戴者与绘制实体相同时可以使用三参数 `open(wearerUuid, runtimeId, sceneToView)`。有临时 UI 实体时使用映射重载；绘制实体只控制提交资格，显示条件仍来自真实佩戴者。数据持有 UUID/运行时 ID，不把游戏实体对象保存到 core。

`sceneToView` 是完整的场景到视图变换。底层 `PreviewFrame` 会先减去 `camera.position` 再应用 `rootTransform`，因此列向量约定下两者满足 `sceneToView = rootTransform × Translate(-camera.position)`。原版 GUI 相机位置为零，两者相同；自定义透视 UI 的非零相机必须计入，不能重复减去相机位置。投影始终在此矩阵之外。

原版模型由宿主通过 `submitFallback(pose, Fallback.RENDERED/POSED)` 提供回退；模型兼容包使用来源提交。当前无实体 dispatcher 调用的直接模型绘制也可提交，前提是宿主确实只在绘制该目标模型时开放作用域。

宿主首次使用时绑定线程，禁止在其他线程打开或清理。换世界、断线或宿主销毁时调用 `host.clear()`：立即撤销现有上下文的提交资格和姿态，但仍须按嵌套逆序执行 `close()`，完成正在进行的渲染退出。来源注册保持有效。宿主的物理会话寿命继续按既有 `PreviewSession` 契约管理。

Minecraft 1.20.1 Fabric 已在实体 dispatcher 中接入上述钩子。使用 `InventoryScreen.drawEntity` 时，不要再次手动开放作用域或重复安装 begin/end。只新增锚点提供者即可。完全独立的 UI 需要对应平台适配器实现上述宿主接入，并通过 core 的 `PreviewPort` / `PreviewSession` / `PreviewFrame` 获取 `FrameOutput`；平台负责资源、帧更新和绘制提交。

## 一个 API，两种提交空间

`HaloAnchorApi.register(id)` 统一返回 `AnchorSource`。世界与预览可以共用一个句柄，也可以只使用其中一种提交方法。

| 契约 | 世界提交 | 预览提交 |
| --- | --- | --- |
| 方法 | `source.submit(uuid, worldPose)` | `source.submitPreview(context, previewPose)` |
| 输入 | 实体 UUID + `AnchorPose` | `HaloAnchorApi.currentPreviewContext()` + `PreviewAnchorPose` |
| 坐标 | 方块单位的世界坐标 | 方块单位的预览场景坐标，去掉 GUI 根变换 |
| 有效作用域 | 被接受的主相机实体绘制 | 当前预览调用及其目标实体 |
| 缓存寿命 | 当前或前一个世界帧，校验世界/实体 ID；以实体相对偏移补偿平移 | 只在当前绘制内有效，关闭即丢弃；同 UUID 多视图互不覆盖 |
| 选择规则 | 后提交的有效样本覆盖先前样本 | 第一个有效模型来源获选；模型 > 渲染回退 > 姿态回退 |

两类姿态、预览上下文、`AnchorSource` 和 `HaloAnchorApi` 均位于 `network.azusake.halo.api.v2`。预览仍用独立姿态类型，避免混用坐标；`AnchorRotation` 是共用的四元数值。底层 `PreviewFrame` 中的 `AnchorPose` 按该帧的预览场景契约解释，不会提交到世界缓存。

### 只修改一侧的模组

- 只修改世界：注册后仅调用 `submit(uuid, worldPose)`。预览继续使用其他提供者或原版回退，不会复制世界锚点。
- 只修改预览：仅调用 `submitPreview(context, previewPose)`，不会写入世界锚点缓存，也不要求实现世界采集。
- 同时修改两侧：注册一次，在各自渲染钩子调用相应方法；YSM／EMF 内置兼容已采用此方式。通过 `HaloAnchorApi.isPreviewRendering()` 区分预览与世界，预览中的无关实体返回空上下文，不得转入世界提交。
- `source.close()` 同时清除此来源在两侧的捕获；未使用的一侧没有贡献可清除，其他来源不受影响。需要独立启停时使用不同 ID，例如 `example:world` 和 `example:preview`。
- 来源 ID 在统一注册表中唯一，不能为世界和预览各注册一次相同 ID。关闭后可重新注册，旧句柄再关闭不会撤销新句柄。

统一的是公开包、入口、来源身份与注册生命周期。两种空间的缓存和选择规则保持隔离，最终复用同一套佩戴表现、物理算法、动画、材质和图元实现。GUI 期间的世界 `submit` 被拒绝；退出预览后原世界作用域恢复可提交。

独立的 `api.preview.v1`、`PreviewAnchorApi`、`PreviewAnchorSource` 已移除，不保留兼容别名。此前开发包的调用方应迁移到上述 v2 方法。当前没有真实外部调用方，本次按维护者要求合并公开契约。

## 公开门面精简（2.3.0）

- 移除 `api.client.preview.v1.HaloPreviewApi`，不保留转发类、废弃重载或二进制兼容垫片。维护者确认当前没有外部调用方，本次无需维持该门面的向下兼容。
- 必要的原版捕获、会话管理和立即绘制移至 `render.PlayerPreviewRenderer`。该内部类使用 Java `public` 只是为了让适配器生命周期和 Mixin 跨包调用，不提供外部稳定性承诺。
- 删除无人使用的公开 `openPreview` / `draw` / 显式会话包装重载，以及 `PlayerPreviewCapture` 的旧模型提交桥接。模型兼容统一使用 core 公开锚点来源。
- core 的 `PreviewPort`、`PreviewSession`、`PreviewFrame` 是平台宿主的计算接入层，继续使用。自动预览物理默认启用，无参 core `openPreview()` 仍创建刚性会话。
- 模型兼容包无需接触内部绘制器；已有原版函数调用保持自动覆盖。独立 UI 不再有 Minecraft 专用公开快捷门面，由其平台适配器提供宿主接入。
- 新版本为 core `2.3.0` / Halo `2.3.0+adapter.1` 开发版。定义 schema、存档、网络和物理算法不变。2.2.0 的提交及发布标签保持不变。

## 验证与交付记录

工作分支：Halo `codex/preview-anchor-api-1.20.1`，core `codex/preview-anchor-api`；基线分别为 `8c1d58a` 和 `1f3bbd7`。临时入口、外部提供者测试、隔离游戏、截图及日志统一放在 `F:/codex-cache/halo-preview-anchor-api/`。

自动检查包含来源注册和失效、模型/回退选择、多窗口、嵌套实体、代理实体映射、线程隔离、异常退出、失效期间的路由及世界 API v2 缓存保护。外部提供者只使用裁出的公开 API JAR 编译，然后在完整 core JAR + JDK 的环境运行；宿主和实现类不在它的编译类路径中。YSM／EMF 有字节码依赖边界检查，防止重新依赖内部作用域或物理会话。

### 首轮锚点 API 验证（公开门面精简前，已获用户验收）

2026-09-15 验证结果：

| 检查 | 结果 |
| --- | --- |
| core 独立构建 | 通过；304 项测试，0 失败、0 跳过；旧 2.1.2 `ClientPort` 调用方链接通过 |
| Halo 联合构建 | 通过；104 项测试，0 失败、0 跳过；旧 API v2 调用方及合并 JAR 检查通过 |
| 独立预览提供者 | 公开 API JAR 编译、core JAR + JDK 运行通过；同一份提供者字节码进入真实游戏测试 |
| 原版实际游戏 | 17 个场景通过，含外部来源提交并替换原版回退；该场景成功提交 235 次 |
| EMF / YSM / 共存实际游戏 | 各 16 个场景通过，合计游戏回归 65 个场景 |

实际游戏环境为 Minecraft 1.20.1、Fabric Loader 0.19.3、Fabric API 0.92.0、YSM `2.6.5-fabric+mc1.20.1`、EMF `3.1.1`、ETF `7.1`。EMF 单独场景启用了改变玩家头部位置和翻滚的 CEM 资源包，确认模型接管后再检查锚点。共存场景的玩家使用 YSM 模型，不能代替 EMF 单独接管测试。

游戏场景涵盖三类图元、生存/创造物品栏、双预览入口、开关、显隐与隐身恢复、`face_camera`、遮罩、重载、GUI 缩放、配方书，以及默认启用的持续物理。每个场景检查 GL 错误、作用域退出、世界缓存隔离和物理会话连续性；截图和报告位于 `evidence-vanilla`、`evidence-emf`、`evidence-ysm`、`evidence-both`。本轮未重跑 Iris 光影、专用服双客户端及其他版本/加载器；冻结分支的迁移和实测不属于本次范围。

该轮交付开发包：`F:/codex-cache/halo-preview-anchor-api/deliverables/halo-1.20.1-fabric-2.3.0+adapter.1.dev.jar`。SHA-256：`b17feae1c9feccf872155ec8d672331f2fb11d45b6bfd34f7199d2e1b9367589`；同目录提供 sources JAR、`verification.json` 与校验文件。该包及证据保留，未被精简版覆盖。

### 公开门面精简后的复查

- core 独立构建、Halo 联合构建通过；core 304 项、Halo 104 项测试均无失败、错误或跳过。独立提供者运行、API 依赖边界和世界 API v2 检查继续通过。
- 成品检查新增已移除门面包的禁止项，确认最终 JAR 不含 `api/client/preview/`，包含内部 `PlayerPreviewRenderer` 及四个 core 预览锚点 API 类。生产兼容包不依赖内部预览绘制器或捕获作用域。
- 使用精简后的同一成品重新执行原版 17、EMF 16、YSM 16、共存 16，共 65 个真实游戏场景，全部通过。EMF 继续启用实际替换/动画化玩家头部的 CEM 资源包；外部提供者场景成功提交 235 次。
- 检查多视图、世界缓存及实体角度恢复、异常退出、嵌套实体排除、原生 GL 状态恢复、持续物理和同帧重复采样。新的测试入口直接使用 core 公开锚点来源，未恢复被移除的旧提交桥接。截图、阶段报告及构建日志保存在 `F:/codex-cache/halo-preview-api-cleanup/`。
- 本轮未重跑 Iris、专用服双客户端及其他平台；未修改冻结分支。此次主要精简适配器入口，core 的计算和两套来源选择算法均未改变。

精简后开发包：`F:/codex-cache/halo-preview-api-cleanup/deliverables/halo-1.20.1-fabric-2.3.0+adapter.1.dev.jar`。SHA-256：`e97f53b218572effe449032a8c137adb716b8d491705ddc239330091eb012e6c`。同目录含 sources JAR、`verification.json` 和校验文件；该开发包对应本次复查结果。

### 合并到 API v2 后的复查

2026-09-15，公开预览类型和入口合并到 `api.v2`，来源注册及注销统一到 `AnchorSource` 后，完成以下验证：

- core 独立构建通过，307 项测试；Halo 联合构建通过，104 项测试。合计 411 项，0 失败、0 错误、0 跳过。旧 `ClientPort`、世界 API v2 调用方和独立预览提供者编译/运行检查通过。
- 新增三项来源隔离回归：只提交世界不影响预览回退及其他来源；只提交预览及注销不改变世界缓存；同一句柄可提交两侧，关闭后均失效，同 ID 重注册不受旧句柄再次关闭影响。
- 成品包含统一 `api.v2` 预览类型，不含已删除的 `api/preview/` 和 `api/client/preview/` 包。YSM／EMF 预览采集复用各自世界来源句柄，并通过公开 API 依赖边界检查。
- 使用同一成品重新执行原版 17、EMF 16、YSM 16、共存 16，共 65 个游戏内自动测试阶段，全部通过。原版外部提供者阶段成功提交 236 次；EMF 单独环境仍启用实际替换并动画化玩家头部的 CEM 资源包。
- 游戏检查继续覆盖多视图、嵌套及异常退出、显隐、世界缓存隔离、物理连续性与 GL 状态；抽查 EMF 头部位移/翻滚及 YSM 双预览截图。运行环境与前两轮一致。本轮未重跑 Iris、专用服双客户端及其他版本/加载器；冻结分支保持不变。

本轮日志、测试入口、隔离客户端、阶段报告和截图位于 `F:/codex-cache/halo-anchor-api-v2/`。开发包为 `deliverables/halo-1.20.1-fabric-2.3.0+adapter.1.dev.jar`，SHA-256：`7400fa2071f5bb4d571070c2252c9d73b0c92eb2d5aa35a6e86d673d3e026f29`。同目录含 sources JAR、`verification.json` 和校验文件。

上述开发包在提交前构建，内嵌提交号仍是两仓库基线，`.dev` 标记表示包含当时工作树中的修改。源码按 core 在前、Halo gitlink 在后分别提交；最终提交号另记于本轮 `deliverables/commits.json`。本次仅作本地提交，不推送或发布，不移动既有 2.2.0 标签；正式发布仍需从最终锁定提交构建并记录正式包校验值。
