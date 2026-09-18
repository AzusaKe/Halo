# 更新记录

## 2.4.0 Forge 适配器 — 2026-09-19

- 新增 Minecraft 1.20.1 Forge 原生适配层，使用 ForgeGradle 6、Mojmap、Java 17 与 Forge 47.4.x；不依赖 Fabric Loader、Fabric API 或 Connector。
- 服务端生命周期、资源重载、实体事件、命令、权杖与可选对端网络迁移到 Forge 事件总线、注册表和 `SimpleChannel`，保持 2.4.0 的存档、配置、定义及逻辑负载契约。
- 世界与物品栏预览继续共用 HaloCore 2.4.0 的锚点、物理、动画和缓存几何；受光路径使用 Forge 实体 RenderType/`NEW_ENTITY` 状态，透明与自发光内容在透明阶段后提交。
- YSM 精确适配目标改为 `2.6.5-forge+mc1.20.1`；EMF 使用 Forge 1.20.1 ABI 检测并支持 3.1.1 及以上；Oculus 桥复用实体 solid/translucent 程序与 LabPBR 采样契约。
- 修复正式合包未正确加载 Mixin refmap 导致客户端无法进入主界面；生产 JAR 现在显式携带并引用 `halo.refmap.json`，分发门禁会检查全部 Mixin 配置。
- 修复多人服务器阶段执行 `/halo` 时再次进入 Forge 客户端命令树并无限递归的问题；服务器转发仅旁路一次本地分发，仍使用原版签名命令及 last-seen 消息流程，LOCAL 模式保持不变。
- 用户已完成 Forge 实际游戏测试，渲染及多人命令回归均通过，未观察到明显错误；自动门禁、正式构建、单客户端专用服转发与无 Halo 服务端 LOCAL 模式记录保存在独立验收目录。

正式版本：`2.4.0+adapter.1`；平台标签为 `v2.4.0-forge-1.20.1-adapter.1`，成品名为 `halo-1.20.1-forge-2.4.0+adapter.1.jar`。

## 2.4.0 — 2026-09-17

- 新增 core `api.v2` 服务端光环来源 API；饰品等外部模组可按 UUID 提交定义，Halo 仍只同步并渲染唯一胜者。
- WorldData 迁移为 `halo:world_data` 普通来源；`/halo show`、`hide` 和权杖仍只修改该来源，不触碰更高优先级的外部来源。
- 新增 `halo_source_priorities.json` 及 `/halo priority list|set|reload`，支持完整有符号整数、运行时覆写、首次冲突降一级和二次冲突禁用诊断。
- 保留旧世界 NBT、网络字节、客户端副本、渲染/预览和现有锚点 API；外部模组仍负责随客户端提供定义及素材。
- 已通过 Forge 专用服双客户端实际验收：Curios 饰品效果、后加入与重连完整同步、运行时来源切换和回退均正常；完整 Forge 客户端也可同时进入原版服，本地 `/halo` 状态彼此隔离且不会向原版服务端发送命令。

正式版本：`2.4.0+adapter.1`；平台标签为 `v2.4.0-fabric-1.20.1-adapter.1`。

## 2.3.2 — 2026-09-17

- OBJ 提交作用域内复用矩阵临时对象，保留原浮点运算及命令不可变性；透明索引仅在精确深度变换命中时复用排序，flat/lit EBO 分别记录实际上传状态。
- OBJ 材质准备在单次提交内缓存程序、uniform 和纹理查找结果；仍逐 draw 设置状态并执行原 shader 生命周期，保持 Iris 阶段、纹理槽和透明深度约定。
- 新增排序冻结参照、旧 writer 二进制调用方及矩阵/缓存回归检查。本轮优化获用户验收通过，正式版本为 `2.3.2+adapter.1`；实测范围与未执行项保留在[验收记录](docs/mesh-submit-optimization.md)。
- 已知历史兼容问题：IterationRP 的 mesh 半透明层可能覆盖后层；官方 2.0.0 Release 已复现，不属于本轮优化引入，本版不包含此问题的修复。详见[排查记录](docs/iterationrp-transparency-investigation.md)。

## 2.3.1 — 2026-09-16

- 修复服务端存在同名 `/halo` 命令时 `renderer` 客户端候选丢失；保留其他客户端候选，覆盖权限变化、命令树重载和重连。
- 撤回相机跌落平台的无效性能对照；修正探针并增加逐帧位置/可见性断言及每轮截图。另在移除 RenderScale 的隔离副本中统一实际 1080p/4K 分辨率，对照无光影、BSL 和 iterationRP，结果见验收记录。
- 默认 `compatibility` 模式共用预生成 billboard/ring 几何，复用共享顶点计算，仅合并连续且绘制状态相同的批次；每次渲染调用内去重纹理查询。
- 新增持久化客户端设置 `primitiveRenderBackend` 和 `/halo renderer [compatibility|cached]`。命令不需要服务器权限、不转发到服务器；在下一帧同时切换世界与预览，不重置佩戴、动画或物理。
- 可选 `cached` 模式复用现有 mesh 缓冲设施，保留旧图元材质、源顺序、透明截断、颜色量化、深度/剔除及提交阶段；OBJ mesh 保持原行为。失败或代次不匹配时从同一帧命令展开回退。
- 在资源加载/定义刷新时准备旧图元几何与纹理；按资源代次及光影顶点格式管理 GPU 缓存，切回兼容模式释放旧图元缓冲。
- 保留 Iris 受光 billboard 的四角切线/UV 中点生成；为共用上传缓冲明确释放原生暂存内存，避免反复切换和重载累积。
- 保留旧 core 构造器、API v2、定义、存档和网络协议。版本为 `2.3.1+adapter.1`，仅 1.20.1 Fabric 主线；模式说明及实测边界见[渲染优化验收记录](docs/render-optimization-verification.md)。

## 2.3.0 — 2026-09-15

- 将预览锚点纳入 core 统一的 `api.v2`：共用 `HaloAnchorApi.register` 和 `AnchorSource`，新增 `submitPreview` 与当前预览上下文查询，所有采用统一 core 的平台共用同一份签名与坐标/生命周期契约；冻结 flash 分支不迁移。
- YSM／EMF 预览捕获改为注册公开来源并提交当前上下文，移除对内部 `PlayerPreviewCapture` 的依赖。版本门槛、第三方符号和头部计算保持不变。
- core 统一处理模型/原版回退选择、嵌套窗口与实体隔离、临时 UI 实体映射、线程及失效检查；兼容包无需管理物理会话或 GPU 绘制。
- 防止嵌套 GUI 预览通过世界 API v2 误写世界锚点；保留自动物理默认值、schema、存档及协议。正式版本 `2.3.0+adapter.1`，详见[预览锚点 API](docs/preview-anchor-api.md)。
- 移除没有外部调用方的 Minecraft 专用公开门面 `HaloPreviewApi`，将必要功能收回内部 `PlayerPreviewRenderer`；删除无人使用的会话/绘制重载和旧预览模型提交桥接。外部模型兼容只使用 core 统一的 API v2。

- 世界与预览可独立使用，未提交的一侧不受影响；关闭句柄清理该来源在两侧的捕获，需要独立注销时使用不同 ID。新增单侧提交/注销及共享句柄回归测试。
- 生存物品栏首次绘制后重置一次预览物理，让实际鼠标姿态的首帧重新 snap，避免从默认鼠标坐标对应的头部位置产生短暂追赶；后续帧继续正常物理模拟。

## 2.2.0 — 2026-09-15

- 为调用原版 `InventoryScreen.drawEntity` 的玩家预览增加光环，覆盖生存物品栏和创造模式的生存物品栏页。读取当前模型的真实头部姿态；定义偏移、缩放、静态变换、视觉动画与三种图元复用世界实现。
- 世界与预览共享佩戴和显隐时钟；第一人称及世界模型未入镜不影响预览，打开界面不重播启动动画。正交预览中的 `face_camera` 使用平行相机方向。
- 抽取公共 GPU 提交器，共享 mesh 缓存和材质。GUI 立即提交，使用原生着色器、满亮光照和 GUI 方向光，隔离世界延迟队列及 Iris 世界路径，并恢复调用方的绘制状态。
- 增加默认启用的 `playerPreviewHaloEnabled` 客户端配置；增加 core `PreviewPort` / `PreviewSession` 和客户端 `HaloPreviewApi`，供后续兼容包或外部 UI 接入。
- 预览捕获不进入世界头部缓存或 API v2。`adapter.2` 增加 YSM／EMF 真实预览头部：YSM 复用 Head 定位器层级及局部偏移，EMF 复用动画后的命名头部及变换；沿用世界版本与 ABI 门槛。隐身允许显示时仍获取当帧姿态；嵌套实体不能覆盖佩戴者锚点。
- `adapter.3` 增加默认启用的 `playerPreviewHaloPhysicsEnabled`，每个预览复用世界的阻尼、朝向模式、角动量和参数，保持独立运动状态。旧配置缺失字段自动补齐为 `true`，显式 `false` 保留无阻尼的刚性随头。core 增加 `PreviewOptions` 及兼容的会话重置/有效性查询入口；无参 API 继续刚性随头。
- 自动视图跨帧保留会话，界面/世界/布局改变或视图消失后释放；原版、YSM、EMF 共用同一物理和绘制路径。不经现有作用域的独立第三方 UI 接入仍留待后续。core 为 `2.2.0`，schema、存档、协议和 API v2 世界语义不变。验证范围见[玩家预览说明](docs/player-preview.md)。

正式版本：`2.2.0+adapter.3`；平台标签为 `v2.2.0-fabric-1.20.1-adapter.3`。玩家预览、两套兼容锚点及物理均已获用户验收。

## 2.1.2 — 2026-09-14

- 恢复按光环定义命名空间前缀搜索：`/halo show` 补全和光环权杖现在都可用 `hal`、`halo` 或 `halo:` 找到 `halo:*` 定义。
- `glowing:false` 改为在光环位置保留方块光/天空光双通道，并通过 Minecraft 原生 lightmap 呈现；露天地表会随昼夜、天气、维度环境光及客户端视觉光照变化，不再把原始天空光 15 直接当作满亮灰度。
- `glowing:true` 继续使用 full-bright 与 `animation.glow`；旧 core 单浮点亮度与构造器保留为未来未迁移适配器的兼容回退。
- OBJ 法线现在会保留并归一化；缺失或导出器写出的零法线按面生成。`glowing:false` 图元在原生模式下使用 Minecraft 的双方向实体光照并继续乘当前位置 lightmap，减少高面数模型整面同亮的失真。
- 1.20.1 Fabric 为每个 mesh 缓存原有扁平格式和新增实体法线格式两套 VBO。法线流按三角形角点展开，使 Iris 能按真实三角形生成切线；`glowing:true` 仍走原有扁平全亮程序，遮罩、动画、透明排序、深度与剔除规则不变。
- Iris 同样保留自发光粒子变体，仅为 `glowing:false` 增加普通实体漫反射的不透明与透明变体。不透明非自发光 mesh 在 Iris 消费 solid G-buffer 前提交，避免 Bliss 将其按玻璃/水透明管线合成；真正混合透明、自发光与原版路径保持既有晚期提交。兼容层支持光影包自定义基础采样函数而不改写 normal/specular/noise 采样，并以不含光影包专有标识的通用 GLSL 数据流检测，将 world-space 位置导数重建的面法线替换为上传的平滑法线。本版不承诺 Halo LabPBR 材质映射或独立投影。
- 保留按 path 前缀搜索、JSON schema 1.1.0、存档、网络协议和锚点 API v2。

正式版本：`2.1.2+adapter.1`；平台标签为 `v2.1.2-fabric-1.20.1-adapter.1`。

## 2.1.0 — 2026-09-13

Minecraft 1.20.1 Fabric 正式版；用户已在约 130 模组的重度整合包中完成 4K 与软件光追场景复测并确认发布。

- mesh 基础纹理与 alpha 遮罩可使用任意正分辨率及长宽比，继续共享归一化 UV 并按各自原生尺寸采样。
- core 新增轻量 mesh 帧输出和可复用逐三角形排序，旧适配器的展开批次接口保持兼容。
- Minecraft 1.20.1 Fabric 在资源重载时缓存索引 mesh 到 GPU；不透明绘制不再逐帧上传几何，透明绘制只更新排序后的索引。
- 保留 Iris 世界流水线材质、透明深度修复、逐面排序、旧 JSON/schema、存档、协议和锚点 API v2。

成品版本：`2.1.0+adapter.1`。验证范围与实测数据见 [mesh 验证记录](docs/mesh-verification.md)。

## 2.0.0 — 2026-09-13

Minecraft 1.20.1 Fabric 正式版，用户已完成复测并确认发布。

- 新增资源包 OBJ mesh 图元：三维 size、保留导出原点、复用父组变换和动画。
- 新增 `preserve_proportions` 开关：开启后忽略 size，以图元统一 scale 保留导出比例，默认保持旧行为。
- 遮罩与基础纹理允许宽高按同一整数倍放大/缩小，保留大图细节；新增原比例和倍数遮罩内置 demo。
- 新增 mesh 灰度遮罩材质：线性/阈值模式、U/V 函数动画、逐像素透明度相乘。
- 新增模型/纹理资源缓存、重载恢复及 mesh 透明绘制路径；保留旧图元行为。
- mesh 等待实体缓冲区刷出后绘制，修复透明 mesh 被后绘制实体覆盖的问题。
- 新增独立 Iris 材质变体，在光影世界流水线内处理遮罩并参与后处理；修复整体零偏移的锚点方向异常。
- Iris 路径补齐线性遮罩 mesh 的透明阶段深度，修复光影后处理误用玻璃或天空背景深度导致的异常淡出；原生渲染的深度规则不变。
- 验证 Blender 原生 OBJ 导出的坐标、原点及面绕序，补充外部模型导入/导出约定。
- 提供 `mesh_demo`、`mesh_mask_demo`、`mesh_step_demo` 示例和中英文作者文档。
- JSON schema 升至 1.1.0；存档、网络和锚点 API v2 不变。暂未迁移其他平台。

成品版本：`2.0.0+adapter.1`。验证范围与已知限制见 [mesh 验证记录](docs/mesh-verification.md)。遮罩宽高仍须使用同一整数倍；U/V 独立倍数支持不在本次发布范围。

## 1.3.1 — 2026-09-12

Minecraft 1.20.1 Fabric / HaloCore 重构的正式验收基准。用户完成约 10 分钟多人游戏测试，未见异常。

- 使用独立版本的 HaloCore，通过 Git 子模块精确锁定；玩家仍只需安装一个 Halo JAR。
- 修复权杖 GUI 滚动条的鼠标拖动范围。
- 恢复光环定义缺失时的中英文聊天提醒，保留佩戴关系并支持资源恢复后继续绘制。
- 隔离开发客户端和专用服务端的运行目录，避免日志冲突及误加载其他游戏版本的存档。
- 保留 JSON schema 1.0.10、存档与网络格式、锚点 API v2，以及已验证的 EMF/YSM/Iris 适配。

成品版本：`1.3.1+adapter.1`。验收过程见 [重构验收记录](docs/refactor-verification.md)。
其他 flash 分支保持冻结。
