# Halo 2.0.0 mesh 验收记录

日期：2026-09-12 至 2026-09-13。仅适配 Minecraft 1.20.1 Fabric。用户已确认测试通过并批准 2.0.0 正式发布，发布验收见末节。前面的开发包、临时方案和未完成项保留为历史记录，不代表当前发布状态。

## 首轮开发源码与成品（历史记录）

- Halo：`codex/mesh-1.20.1`，基线 `29760d9f56ef15503888be032b4a9c34935c4e71`，适配修订 1。
- core：`codex/mesh`，基线 `dd6e24b2687a901e43de95568525cb1011f9316c`，功能版本升至 `2.0.0`，schema `1.1.0`。
- 当前 core 已本地提交至 `0eb039a5295cb2457624bf74b3e15b5a531d3b82`；包含本文的 Halo 检查点同步锁定该 gitlink。下文测试包保留提交前的来源元数据；本地固定成果记录见末节。未推送、创建标签或执行发布。
- 开发构建：`build/libs/halo-1.20.1-fabric-2.0.0+adapter.1.dev.jar`。元数据 `development=true`；其中 SHA 是构建时的工作树基线，不代表 mesh 实现已存在于那些基线提交。
- 当前修复包 SHA-256：`4d8785d3db7780964d10643ff3c72dc1e4883425bff3fd0ef6a9559f5136e5e5`（557,043 字节）。本次背景透明度修复前、用户已测试的 2.0.0 包 SHA-256 为 `26ef10349b86580d5ebd275961e0ce4285387c18af724b7c6f362a22147d74e1`（557,012 字节），保存在 `F:/codex-cache/halo-mesh/background-issue/halo-before-fix.jar`。首轮 1.4.0 开发包 SHA-256 为 `80e915fec09cc6bd988f10dce487e7bd23d5097393462ef34aec0da427cc0184`。
- 中英文使用说明：[`zh/mesh.md`](zh/mesh.md)、[`en/mesh.md`](en/mesh.md)。三个内置定义提供普通贴图、线性遮罩、阈值遮罩。

## 自动检查

使用 Microsoft Java 17.0.11、仓库 Gradle 8.8 / Loom 1.5.8。

```powershell
.\core\gradlew.bat -p core build --console=plain
$env:HALO_YSM_TEST_JAR='F:/codex-cache/halo-ysm-plan/jars/ysm-2.6.5-fabric+mc1.20.1-release.jar'
.\gradlew.bat build --console=plain
```

| 项目 | 结果 |
| --- | --- |
| core 独立构建 | 通过；262 项 JUnit，失败、错误、跳过均为 0 |
| Halo 联合构建 | 通过；87 项 JUnit，失败、错误、跳过均为 0 |
| OBJ 与尺寸 | 正负索引、独立 UV、接缝、凸四边形、非法面/数值、资源 ID/行号、原点保留、零跨度、不可变数组、独立导出样例 |
| 帧流水线 | 父子组变换、alpha/glow 继承、环境亮度、大坐标、过渡期间遮罩冻结、零整体偏移三种朝向模式、资源缺失恢复与旧图元共存 |
| 材质与资源 | 阈值边界、黑白灰映射、UV term 叠加/循环、逐实例参数隔离、深度状态、实例与三角排序、按代次资源缓存/错误去重 |
| 平台边界 | core 无 Minecraft/Fabric/Mixin/GPU 引用；无新增运行时依赖；保留旧 FrameScene/DrawBatch 构造入口 |
| 既有兼容检查 | NBT/配置/协议往返与固定字节、实际 YSM 2.6.5 ABI 签名、最终 JAR 合包和旧 API v2 调用方均通过 |

## 游戏实测

Windows 11、i9-14900HX、RTX 4060 Laptop、NVIDIA 616.64；Minecraft 1.20.1、Fabric Loader 0.15.11、Fabric API 0.92.0+1.20.1。
测试资源、运行目录、日志、截图和临时脚本保存在 `F:/codex-cache/halo-mesh`。单人使用复制的 smoke 世界，多人使用隔离测试服。

| 场景 | 结果 |
| --- | --- |
| 单人普通 mesh | 实际进入游戏，非对称彩色贴图、3D 截面和旋转可见 |
| 单人遮罩 | 线性模式观察到灰度透明区，step 模式观察到二值边缘；基础纹理保持原 UV |
| 客户端重载失败/恢复 | 将临时构建目录中的遮罩由 32×32 改为 16×16，通过资源包界面触发重载：只跳过该 mesh，尺寸错误仅报告一次；恢复 32×32 后重载，无需 show 指令即恢复；源资源未改坏 |
| 专用服双客户端 | 两个客户端成功加入，服务器 show 分发 mesh 佩戴；普通客户端看到实体 mesh |
| 无 Halo 服务端 | 原版 1.20.1 专用服，无 Fabric/Halo；最终代码客户端执行本地 show，第三人称 mesh/灰度遮罩可见，旧格式本地佩戴文件写入成功 |
| 换维度与大坐标 | 本地模式从主世界传送至下界 X/Z=1,000,000，第三人称 mesh 和遮罩保持可见；再返回主世界，无需重新佩戴 |
| 重启与重连 | 关闭客户端 JVM 后重新连接同一原版服，不执行 show，第三人称 mesh 和遮罩自动恢复 |
| 同屏 20 mesh | 20 只测试猪各佩戴 288 三角形灰度遮罩 mesh，普通客户端已观察并截图 |
| Iris 首轮测试 | 1.4.0 临时适配：Iris＋EMF/ETF＋Complementary 下 20 mesh 可见；前方石墙正确遮挡墙后 mesh，移除测试墙恢复。该轮采用流水线外补画，已被本轮 2.0.0 实现替代 |
| EMF 实际模型捕获 | 加载 `emf-test-pack` 的 player/player_slim JEM 后第三人称观察玩家 mesh；日志确认 `head matrix captured from EMFModelPart.render`，与 Iris 同时启用 |
| 稳定渲染 I/O | 普通客户端 20 mesh 场景录制 10 秒 JFR，开启所有 FileRead（threshold=0）；未记录 mesh/纹理资源读取或 Halo 栈中的文件读取，仅两次 Minecraft JAR 读取；这不能证明所有可能的本地/GPU 活动均无开销 |

Iris 会在世界流水线内屏蔽未知 shader，机制依据 [Iris 1.20.1 shader 处理](https://github.com/IrisShaders/Iris/blob/1.20.1/src/main/java/net/irisshaders/iris/mixin/MixinShaderInstance.java)。2.0.0 已移除 WorldRenderer 返回后的补画路径：在 Iris 创建流水线时建立独立材质变体，使用其 `gbuffers_textured`（或 Iris 生成的 fallback）程序和帧缓冲区，在原始基础贴图采样时应用遮罩，再由光影包处理并参与后处理。额外程序由 Iris 统一释放，不引入运行时依赖；core 不接触 Iris。独立 mesh 投射阴影通道、PBR 及不兼容的 geometry/tessellation 光影阶段未实现。

第三方测试组合：EMF 3.3.5、ETF 7.2.1、Iris 1.7.6、Iris 指定的 Sodium 0.5.12-beta.2、Complementary Reimagined r5.9.1。Iris/Sodium/光影从 Modrinth 项目 API 下载并核对 SHA-512，具体版本记录在临时目录 `compat-provenance.json`。
YSM 2.6.5 发布包通过 ABI 测试，但实际启动报告 `Unsatisfied runtime environment (err:54)`，因此不声明 YSM 模型游戏验收通过。光影组合测试中另保存了 GLFW 原生退出报告 `hs_err_pid77984.log`、`hs_err_pid55136.log`（窗口激活期间，栈在 GLFW/FPS 限制或换帧）；之后仅开一个客户端完成光影画面和深度遮挡验证。不能将后续可运行等同于此前原生退出已彻底解决，双窗口光影环境仍需复查。

本机截图证据位于 `F:/codex-cache/halo-mesh/client1/screenshots/`：`2026-09-12_21.25.40.png` 普通 mesh、`21.26.48` 灰度遮罩、`21.28.43` 阈值遮罩、`21.45.02` 光影深度遮挡、`21.45.32` 光影下 20 mesh、`21.45.52` EMF 玩家头部。`client2/screenshots/` 保存普通多人、大坐标及本地重连证据。详细日志为 `singleplayer-vanilla-renderer.log`、`iris-emf-final.log`、`multiplayer-twenty-mesh.log`、`local-mode-dimension.log`，JFR 为 `twenty-mesh.jfr`。

## CPU 基准与限制

独立 Java 17 基准通过 `ClientRuntime.render`，包括 core 动画、锚点、CPU 几何变换和透明排序，不含 Minecraft 顶点提交或 GPU。20 实体用例预热 600 帧、采样 600 帧；50,000 三角形用例预热 100 帧、采样 150 帧。JVM `-Xms512m -Xmx512m`。

| 三角形/模型 | 实例 | 中位 CPU ms/帧 | p95 ms/帧 | 分配 KiB/帧 | 批次/帧 | 提交顶点/帧 |
| --- | --- | --- | --- | --- | --- | --- |
| 1,000 | 20 | 0.702 | 1.208 | 1449.2 | 20 | 60,000 |
| 50,000 | 1 | 4.932 | 7.634 | 3441.2 | 1 | 150,000 |

这些是该机器一次本地基准，不能换算为保证的游戏 FPS。逐帧顶点列表仍有分配；GPU 常驻网格、实例化和全局透明排序不在本次实现范围。记录和可重放 Java 基准见临时目录 `benchmark.txt`、`MeshBenchmark.java`。

## 首轮发布前仍需补充的覆盖（历史记录）

未完成的游戏项目不得由自动测试代替：所有方向的单面剔除、资源包优先级/模型替换/资源移除恢复全组合、睡眠/隐身/死亡等完整生命周期、长时间压力测试，以及可正常加载的真实 YSM 模型组合。当前只交付开发版，不执行发布门禁或高版本迁移。

2026-09-12 用户确认的后续安排：

- 用户已反馈 YSM 锚点正常、其余功能正常，约 10 分钟内稳定。GLFW 原生退出原因未确定，但本轮用户测试未复现；不把此前退出记录删除或改写为已确定根因。
- 透明交叠问题即使发现也不修复，不作为本次开发的验收阻塞项。
- 用户反馈后已完成 Iris 世界流水线内材质适配；仅显示而不参与后处理的临时路径已删除。
- 功能版本已升至 **2.0.0**，schema 保持 `1.1.0`；旧测试包保留，新的开发包单独命名。

当前测试 JAR 已内置 `halo:mesh_demo`、`halo:mesh_mask_demo`、`halo:mesh_step_demo` 三个定义及其 OBJ、基础贴图、遮罩和 shader，无需额外资源包。分别执行 `/halo show @s halo:mesh_demo`、`/halo show @s halo:mesh_mask_demo`、`/halo show @s halo:mesh_step_demo` 可测试普通、线性遮罩、阈值遮罩效果；`/halo hide @s` 用于卸下。

## 用户反馈后的复验

用户提供 `2026-09-12_22.11.04.png`，报告关闭光影时 mesh 被实体错误遮挡，开启光影时正常。适配器继续在 AFTER_ENTITIES 采集同帧锚点与几何，但 mesh 改在 AFTER_TRANSLUCENT 提交，等待实体缓冲区刷出；旧图元提交时机保持原样。

使用隔离测试模组调用游戏 API 自动布置同一只静止猪与交叠 torus，保存原始帧缓冲截图。临时测试模组、额外定义和诊断光影包仅位于 `F:/codex-cache/halo-mesh`，最终 JAR 已检查无这些内容。

- Iris 1.7.6 + Sodium 0.5.12-beta.2 + Complementary Reimagined r5.9.1 + EMF 3.3.5 / ETF 7.2.1：普通贴图、线性遮罩、阈值遮罩显示正常；光影开关后原生 shader 与 Iris 材质切换正常。
- 游戏内资源重载成功，mesh 与遮罩恢复；同一 JVM 内多次关闭/开启光影、切换光影包后材质重新创建，没有新增 Halo 异常。
- 遮挡回归对照：关闭原版实体阴影，避免阴影层提前刷出猪的顶点缓冲；使用整张基础贴图、组 alpha=0.5 的 torus。测试程序在旧 AFTER_ENTITIES 时机实际提交 790 帧，前方半透明环被猪覆盖；恢复 AFTER_TRANSLUCENT 后前方环正确覆盖/混合猪表面，后方环仍被遮挡。截图 `13-native-old-submit-stage.png` / `14-native-fixed-submit-stage.png`，日志 `depth-submit-regression.log`。这是实体提交时序问题，不需要靠关闭深度测试解决。
- 白天/夜晚、实体交叠、前方石墙遮挡及移除恢复均保存截图。测试额外读取 native shader 的 MaskEnabled/MaskMode：CPU/GPU 参数一致，遮罩切换至普通材质时两者均归零。
- 自制诊断光影包只含最终反色 pass，触发 Iris fallback 几何程序。mesh 随场景一起反色，确认实际进入后处理；随后切回 Complementary 恢复。
- Blender 5.2.0 原生 OBJ 导出实测：Forward=-Z、Up=Y，转换 `(x,y,z) -> (x,z,-y)`；非居中、轴长不等的四面体在 core 中保持原点、面绕序和 UV 接缝。新增固定 OBJ 测试资源，生产导入逻辑无需换轴或镜像修补。

日志：`iris-material-integration.log`、`iris-fallback-integration.log`；截图目录：`probe-shots/`。`01`/`02` 为光影开关下线性遮罩，`03`/`04` 为原生阈值/普通材质，`05`/`06` 为 Iris 普通/阈值材质，`08` 为资源重载后，`09` 为夜间，`10`/`11` 为石墙遮挡。用户随后反馈本轮测试通过，新增视觉问题见下一节；本轮未修改 Blender 插件仓库。

隔离客户端完成测试后正常保存世界并退出，临时 probe 模组已移出客户端 mods 目录。最终成品检查确认包含三个 demo 及全部引用资源，不含 probe 类/定义，也不含已删除的流水线外补画 mixin。两个仓库仍未提交、未推送，gitlink 不变。

## 用户复测与进度暂存（2026-09-12，22:44 截图）

用户确认本轮测试通过并要求暂存进度。此前 YSM 锚点正常、约 10 分钟内稳定的用户反馈仍有效；这不代表下述新问题已解决，也不扩展为所有光影包均已验收。

**暂存时待解决：开启光影后，背景影响光环透明度。** 用户观察到光环位于玻璃等半透明方块前方，或后方只有天空时，会进一步变透明甚至直接消失。两张截图均为开启光影的实拍：

- `C:/Users/Azusa_Ke/AppData/Roaming/.minecraft/versions/1.20.1-Fabric 0.19.3/screenshots/2026-09-12_22.44.42.png`
- `C:/Users/Azusa_Ke/AppData/Roaming/.minecraft/versions/1.20.1-Fabric 0.19.3/screenshots/2026-09-12_22.44.34.png`

此时用户实际 Iris / 光影包名称、版本及两张截图的配置差异尚未提供，未用隔离测试环境的版本代替。暂存时只记录现象，尚未复现和定位根因，也未实施修复。天空背景下同样消失，因此将其单列为兼容性问题，不直接归入用户此前接受的普通透明交叠排序限制。后续定位见下一节。

后续定位时，在同一视角、同一光影配置下对照不透明方块、玻璃和天空背景，分别检查普通 mesh、线性遮罩与阈值遮罩；再对照关闭光影及旧图元。深度缓冲、材质输出及光影后处理只作为待检查方向，不能据截图认定原因。

本次仅更新验收记录并分别暂存 Halo 与 core 的现有改动，不改变渲染实现，不重新构建或运行游戏。保留上述 2.0.0 开发包及其 SHA-256；未提交、未推送、未创建标签或发布，core gitlink 仍为 `dd6e24b2687a901e43de95568525cb1011f9316c`。

## 光影背景透明度定位与修复（2026-09-12）

用户进一步缩小范围：只有 `mesh_mask_demo` 出现问题，普通与 step demo 正常；BSL 未见异常，Iteration RP 与 Bliss 均受影响，Bliss 在天空背景下几乎不可见。读取用户指定 shaderpacks 目录，复制光影包和同名设置文件至隔离客户端；未修改用户的光影包、设置或世界。用户 mods 目录的 Iris 文件为 `iris-1.7.6+mc1.20.1.jar`，与隔离测试的 Iris 版本相同。

定位到 Iris 适配层的深度提交：线性遮罩沿用普通前向透明渲染的 `depthWrite=false`，虽然已写入光影包的颜色/材质缓冲区，却没有把 mesh 的深度写入 `depthtex0`。Bliss 的 `dimensions/composite3.fsh` 从该深度还原位置并应用体积雾/云；Iteration RP 的 `Lib/Programs/Composite/Translucent_FS.glsl` 同样据此还原透明材质位置。后方是玻璃或天空时，后处理错误使用背景的位置。普通与 step demo 原本允许写深度，因此没有同样的问题。各光影包的合成流程不同，表现强弱也不同。

只在 Halo 的 Iris mesh 绑定路径启用深度写入，保持原有 AFTER_TRANSLUCENT 提交时机。Iris 已在透明阶段开始前复制不透明深度 `depthtex1`，因此此修改只为后续合成补齐 `depthtex0`。保持 alpha 遮罩计算、丢弃零 alpha、光影包混合设置和 mesh 排序；关闭光影时继续使用 core 的原始深度标志。没有修改 core、旧图元或任何光影包源码，也未按光影包名字添加特判。深度缓冲含义依据 [Iris DepthTex 文档](https://shaders.properties/current/reference/buffers/depthtex/)，阶段顺序另核对本地 Iris 1.20.1 `IrisRenderingPipeline.beginTranslucents()`。

在三个背景并排的静态对照场景中，仅由临时测试模组切换 mesh 批次的深度写入：Bliss 天空背景下缺失的片段恢复，Iteration RP 的背景相关明暗异常消除，BSL 原有显示保留。对照期间累计修改 2,778 帧的测试批次，alpha/纹理/几何均未改动。证据为 `background-issue/depth-comparison.log` 和 `0-old.png` / `0-depth.png`（Bliss）、`1-old.png` / `1-depth.png`（Iteration RP）、`2-old.png` / `2-depth.png`（BSL 10）。

移除测试模组的批次覆写逻辑后，以正式修复代码完成下表复验。统一使用 Iris 1.7.6（与用户 JAR SHA-256 一致）、Sodium 0.5.12-beta.2、EMF 3.3.5 / ETF 7.2.1；其余隔离环境配置同前。各包均观察普通贴图、线性遮罩、阈值遮罩和内置 `mesh_mask_demo` 的运动效果，背景为并排的不透明石墙、玻璃和天空。

| 光影包 | 结果 |
| --- | --- |
| Bliss v2.1.2 | 线性遮罩在天空/玻璃背景下恢复显示，普通与 step 保持正常 |
| Iteration RP Alpha 0.8.22 | 背景深度导致的异常淡出/明暗变化修复，普通与 step 保持正常 |
| BSL v10.1.1 / v8.2.09 | 两版均通过显示及深度检查，保留用户各自的光影设置 |
| Complementary Reimagined r5.9.1 | 显示及深度检查通过 |
| 临时反色诊断光影包 | Iris fallback 材质仍可显示 mesh，mesh 随场景反色，确认继续参与后处理 |
| 光影关闭、重新开启 Bliss | 三类静态材质正常；重新开启后线性遮罩恢复正确深度 |
| 客户端资源重载 | 重载后线性遮罩继续显示，深度检查通过 |

GPU 检查在世界 AFTER_TRANSLUCENT 阶段读取 `depthtex0` 与 `depthtex1`，选取天空背景的 150×40 像素区域：Bliss 线性遮罩有 3,321 像素含 mesh 深度，2,679 像素保留天空深度，而不透明深度仍为天空；Iteration RP 对应 3,312 / 2,688。测试共完成 20 次静态材质/恢复场景的深度断言，另外保存内置动画 demo 的深度读数与画面。原生模式的验收依据截图，不使用探针缓存的 Iris 深度读数。探针首轮在 GUI 阶段之后读取深度时遇到已清空的缓冲区，已移到世界渲染阶段重新验证；该次探针失败不计为通过。

最终单 JVM 复验保存 29 张截图，于 23:06:26 报告 `PROBE_COMPLETE` 并正常保存世界退出；没有 Halo 材质创建或绘制错误。日志为 `F:/codex-cache/halo-mesh/background-issue/fixed-integration.log`，截图位于同目录的 `fixed/`；光影包文件哈希与设置备份见 `packs.json`。测试 probe 仅用于隔离客户端，验收后移出 mods；发布用 JAR 中不含 probe 类或额外定义。

本轮联合 `build` 通过，Halo 87 项 JUnit 全部通过（无失败/错误/跳过），合包及旧 API v2 检查通过；core 本轮无改动，沿用上一轮独立构建的 262 项通过结果，未重复执行。文档链接、JSON 示例和 Git 差异检查通过。交付修复包时版本为 2.0.0 / schema 1.1.0 / adapter 1，Halo 修复和记录留在工作区，先前暂存内容保留，两个仓库尚未提交或推送，HEAD 和 gitlink 不变。后续用户原场景复测与本地提交结果见下节。

## 用户最终复测与本地检查点（2026-09-12）

用户使用背景透明度修复包复测后确认：“测试通过，我无法再观察到任何图形错误”。此前反馈的 YSM 锚点正常及约 10 分钟稳定性结果一并保留。本次图形问题已获用户验收；不将其解释为未测试的任意光影包、设置组合或未来版本均有保证。

按用户“提交当前进度、固定成果”的要求，先将 core 提交至 `0eb039a5295cb2457624bf74b3e15b5a531d3b82`（`codex/mesh`），再由包含本节的 Halo 提交（`codex/mesh-1.20.1`）保存完整适配实现、内置 demo、Iris 修复、文档和对应 gitlink。Halo 提交可在本文件的 Git 历史中查询。两个仓库仅作本地提交，不合并主线、不推送、不创建标签或 Release。

本次固定成果只补充验收记录，不修改已测试的源码和资源，不重新构建或运行游戏；保留上述 SHA-256 为 `4d8785d3db7780964d10643ff3c72dc1e4883425bff3fd0ef6a9559f5136e5e5` 的测试包。沿用 core 262 项、Halo 87 项自动测试及本节前的游戏验证，另检查本次文档和待提交差异。

Blender 插件由用户在另一对话基于现有插件继续开发；Halo 此后待命接收问题反馈。坐标要求是 Blender 预览与 Minecraft 实际模型在形状、朝向、比例和原点上保持所见即所得，坐标换算不能引入镜像、额外旋转或重复变换。本次未修改插件仓库。

## 整数倍遮罩与原比例缩放（2026-09-13）

用户明确本轮契约：两张贴图按较大分辨率对齐，小图的像素按完整像素块覆盖，不降低大图细节；原比例模式保留 OBJ 原始坐标，仅乘图元统一 scale，忽略 size。基于 Halo `83129f0b945d99db02087ef2c6f084d6fbed5d0c`、core `0eb039a5295cb2457624bf74b3e15b5a531d3b82` 开发。

- core 允许基础/遮罩宽高按同一正整数倍放大或缩小，非法组合报告两个资源 ID、尺寸和倍数规则。现有归一化 UV 与遮罩原生分辨率 texelFetch 已满足像素复制，不新增缩放图、不降采样、不改变 shader 或 Iris 深度逻辑。
- 图元新增 `preserve_proportions`（默认 false）与统一 `scale`（默认 1，有限非负数）。开启时 size 可省略；提供的 size 仍作格式校验，但不参与几何换算。保留导出原点、平面轴坐标和 group 累计变换；关闭时继续分轴 size 换算，图元 scale 不生效。保留旧四参数构造入口。
- Halo 新增 `mesh_preserve_demo` 和 `mesh_mask_resolution_demo`，后者用 32×32 基础纹理分别配 16×16、64×64 原生灰度遮罩；64×64 含单像素细条纹。更新中英文 mesh/参数文档，未修改 Blender 插件。

core 独立 `build` 通过：268 项测试全部通过，无失败、错误或跳过。新增覆盖 JSON 默认/非法字段、无 size 导入、非居中与零跨度几何、统一缩放与父组变换、整数倍双向尺寸、非法比例、重载恢复和实例状态保留。Halo 联合 `build` 通过：87 项测试全部通过，包括真实 YSM 2.6.5 JAR 的 ABI、旧 API v2 调用方、core 边界和合包检查。两仓库合计 355 项；未将本轮 ABI 检查写成新一次 YSM 游戏验收。

隔离单人客户端沿用 Iris 1.7.6、Sodium 0.5.12-beta.2、EMF 3.3.5 / ETF 7.2.1，在石墙、玻璃、天空三个背景前检查两个新内置 demo。关闭光影、Bliss 2.1.2、Iteration RP Alpha 0.8.22、BSL 10.1.1 均能显示两种尺寸遮罩和原比例模型；高分辨率细条纹仍可辨识。反色诊断包中的 mesh 随场景反色，Iris fallback 继续参与后处理。切回 Bliss 并执行客户端资源重载后，新资源代次为 2，两张遮罩仍保留各自的 16/64 分辨率并正常显示；最后关闭光影检查旧 `mesh_demo` 的 size 路径。

测试于 00:47:01 报告 `PROBE_COMPLETE`，00:47:02 保存世界并正常退出，日志没有 Halo mesh 资源/材质错误。保存 12 张截图；日志与截图分别位于 `F:/codex-cache/halo-mesh/scaling/game-test.log` 和同目录 `shots/`。临时 probe 已移出隔离客户端 mods，成品 JAR 包含两个 demo 及全部引用资源，不含临时 probe 类/定义。文档链接、JSON 示例和 Git 差异检查通过。本轮未重复专用服双客户端、10 分钟稳定性或用户 Blender 插件的端到端验收。

交付包：`build/libs/halo-1.20.1-fabric-2.0.0+adapter.1.dev.jar`，559,821 字节，SHA-256 `77e9d73b8cf95661d097bb3a354da3323bbf57f8fb159ff53915fa6bb733875d`；留存副本为 `F:/codex-cache/halo-mesh/scaling/halo-after-scaling.jar`。版本仍为 2.0.0 开发版 / schema 1.1.0 / adapter 1。本轮 core 源码/测试/文档和 Halo 资源/文档均为未提交改动，HEAD 与 gitlink 仍为本节基线；未推送、未创建标签或 Release。

## 保留同倍数规则并固定开发版本（2026-09-13）

用户提供的 `mika_3d_mask.zip` 在 128×128 基础纹理上使用 512×256 遮罩，宽度为 4 倍、高度为 2 倍。对比两个资源包确认 OBJ 和基础纹理相同，新增遮罩引用正确；用户客户端日志明确记录该尺寸组合被同倍数校验拒绝。用户决定当前不实现 U/V 独立整数倍支持，固定现有版本；因此保留当前规则及其非法比例测试，不修改资源包或 shader。

按用户要求，先在 `codex/mesh` 提交 core 至 `004111050982bad5a27452977e2387a9523ae63b`，保存原比例缩放、同倍数遮罩和相关测试/文档；再由包含本节的 Halo 提交（`codex/mesh-1.20.1`）保存 demo、纹理、作者文档、验证记录和此 core gitlink。Halo SHA 可在本文件的提交历史中查询。两个仓库仅本地提交，不合并主线、不推送、不创建标签或 Release。

版本保持 2.0.0 开发版 / schema 1.1.0 / adapter 1。此次只补充决定和提交记录，未修改已验证的功能源码/资源，未重新构建或运行游戏；沿用上一节 core 268 项、Halo 87 项以及隔离客户端验证，另检查文档和待提交差异。保留上一节 SHA-256 为 `77e9d73b8cf95661d097bb3a354da3323bbf57f8fb159ff53915fa6bb733875d` 的测试包；其构建来源仍记录提交前 Halo `83129f0` / core `0eb039a` 基线及开发工作树状态，不将其标记为新提交的正式发布构建。

## 2.0.0 正式发布验收（2026-09-13）

用户确认“通过测试，可以将其作为 2.0.0 正式版”，授权收尾、提交和推送，并明确要求 Halo 合并至 `1.20.1-fabric` 后删除本次开发分支。发布保留上述功能与限制：OBJ、原比例缩放、宽高同倍数遮罩、Iris 世界流水线材质，以及五个内置 demo；不新增 U/V 独立整数倍支持。最后两个功能提交为 Halo `93d67f5` / core `0041110`；本次发布收尾只修改文档、发布状态及 gitlink，不修改经过验证的功能代码和资源。

core 发布提交为 `2800fb5af483753e589c7e9952f6056127b96f8c`，集成分支 `main`，标签 `v2.0.0`；Halo 以本发布提交的 gitlink 锁定它，集成分支 `1.20.1-fabric`，标签 `v2.0.0-fabric-1.20.1-adapter.1`。本次开发分支为 core `codex/mesh` 和 Halo `codex/mesh-1.20.1`，其他平台/flash 分支不属于本次发布或清理范围。

已完成的验收证据为 core 268 项、Halo 87 项自动测试，上一节隔离客户端的原生/Bliss/Iteration RP/BSL/Iris fallback 与资源重载检查，以及用户本次最终复测。YSM 锚点和约 10 分钟稳定性沿用用户反馈；历史未覆盖项不改写为逐项通过。正式构建使用 `build -Prelease=true`，门禁要求两仓库干净、core HEAD 等于已提交 gitlink、且该 core SHA 可从远端获取；产物为 `halo-1.20.1-fabric-2.0.0+adapter.1.jar`，内含 `halo-build.json` 记录确切来源，`development=false`，schema 仍为 1.1.0，存档/协议/锚点 API v2 保持兼容。

远端构建与发布结果以 [Halo Actions](https://github.com/AzusaKe/Halo/actions)、[HaloCore Actions](https://github.com/AzusaKe/HaloCore/actions) 和 [2.0.0 Release](https://github.com/AzusaKe/Halo/releases/tag/v2.0.0-fabric-1.20.1-adapter.1) 为准。正式产物由发布标签的 CI 构建并上传，不将前述 `.dev` 测试包重命名作为正式版。
