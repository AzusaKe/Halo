# IterationRP mesh 半透明叠加排查（2026-09-17）

用户在 `halo:mesh_mask_demo` 中观察到：IterationRP 下前方半透明面会使后方半透明面不可见，而关闭光影与 BSL 会叠加；用户已在优化前的 2.3.1 adapter.1 复现。本轮先保存优化检查点，再按要求从 GitHub Release 下载并实测主线 2.0.0。

## 优化检查点

- core：`9990753efdb056c736bc05b9478be010bb7dc7db`，分支 `codex/mesh-submit-optimization`。
- Halo：`0efffaba581a37bc61308b8733242a39d76117fe`，分支 `codex/mesh-submit-optimization-1.20.1`，gitlink 指向上述 core。
- 检查点阶段按 core 在前、Halo 在后的顺序提交，当时未合入主线、未推送、未打标签。后续用户确认本问题不属于本次优化引入，决定将本轮优化验收设为通过，并授权发布 2.3.2；实际测试范围仍保留，详见 [mesh-submit-optimization.md](mesh-submit-optimization.md)。

## Release 来源与隔离环境

实测文件来自 [2.0.0 Fabric 1.20.1 adapter.1 Release](https://github.com/AzusaKe/Halo/releases/tag/v2.0.0-fabric-1.20.1-adapter.1)，不是本地重编译替代品：

- `halo-1.20.1-fabric-2.0.0+adapter.1.jar`，559,713 字节，SHA-256 `d10ecff0cf18ac17ccb6510963995d5d90207eac8c94f40de72d2ecdccd2389b`。
- Release 标签解析到 Halo `f9e6779cbc4cfd21a45b22b0208d9b9894dd36f1`，对应 core gitlink `2800fb5af483753e589c7e9952f6056127b96f8c`。
- 同时下载了主线 2.1.0 adapter.1、2.1.2 adapter.1、2.2.0 adapter.3、2.3.0 adapter.1 的运行 JAR 与 sources JAR，留作后续排查。**下载不等于实测**；本轮版本对照先测 2.0.0。

所有产物位于 `F:/codex-cache/halo-transparency-regression/`，`release-artifacts.json` 记录各文件哈希，`releases/release-2.0.0.json` 保存 Release 元数据。测试使用独立客户端副本；未覆盖用户日常客户端、光影包、设置或世界。

设备沿用 RTX 4060 Laptop、Java 17、Minecraft 1.20.1、Fabric Loader 0.19.3、Iris 1.7.6、Sodium 0.5.13 及原测试模组组合。实际全屏、主 framebuffer 与 Iris targets 均为 1920×1080。BSL 与 IterationRP 在分辨率稳定至少 2 秒后重载光影，再等待稳定。各轮 `inputs.json` 保存 JAR、探针、mods 和参数指纹，`display.txt` 保存尺寸与重载证据。

## 对照方法与结果

固定相机、实体、时间和几何，以不透明石墙、玻璃及天空为三个背景。一个 OBJ 含前红、后蓝两层面，base alpha 均为 128/255。依次观察两层、仅前层、仅后层、重复两层；单层对照只把另一层纹理 alpha 置零，保留同一 OBJ 和边界。另装备原版 `halo:mesh_mask_demo` 核对实际定义。

探针无 Halo 类链接、无渲染状态覆写，不依赖新 core API。v2 完成以下三轮，每轮 9 个截图阶段，均正常退出、记录 `glError=0`，装备命令成功：

| 2.0.0 Release 环境 | 两层重叠的表现 | 证据目录 |
| --- | --- | --- |
| Iris 安装、关闭光影 | 红蓝叠加，重叠区为紫色 | `evidence-release200-off-1080-v2` |
| BSL 10.1.1 | 红蓝叠加，后层贡献可见 | `evidence-release200-bsl-1080-v2` |
| IterationRP Alpha 0.8.28 | 重叠区只表现前层，后层仅在露出的区域可见 | `evidence-release200-iteration-1080-v2` |

选取石墙背景内部固定区域 `[600,530)–[650,610)`，对比 8-bit RGB 的平均绝对差异（逐通道），用于辅助区分后层贡献与时间变化，不作为跨光影像素一致性验收：

| 环境 | 两层 vs 仅前层 | 两层 vs 重复两层 |
| --- | ---: | ---: |
| 关闭光影 | 21.638 | 0.000 |
| BSL | 29.027 | 0.075 |
| IterationRP | 0.772 | 0.270 |

各目录的 `overlap.json` 保留原始 RGB 均值和区域。IterationRP 仍有云、抖动及历史累积，不能将 0.772 写成像素完全相同。画面中后蓝层单独显示正常，排除后层本身未加载；关闭光影/BSL 对照也证明模型具备可观察的叠加区域。

`mesh_mask_demo` 的 2.0.0 与当前候选 JSON 语义相同，OBJ 仅换行不同，base/mask PNG 字节相同。该 demo 有动画，各截图时间不相同；不将其动画截图用作精确像素差异证据。探针还包含 `glowing=false` 变体，但 2.0.0 尚无后来加入的方向受光路径，不能据此宣称已覆盖新版 lit shader。

首轮 `evidence-release200-iteration-1080-v1` 使用旧版不支持的多实体 `halo show` 选择器，装备失败，已写入 `INVALIDATED.md` 并排除。v2 改为逐实体执行，未把首轮进程正常退出当作功能通过。

## 合成前 GPU 证据与通道约束

v3 探针保持同一 2.0.0 Release 和场景，在 Halo 的 AFTER_TRANSLUCENT 提交之后、光影合成之前，只读 `colortex0/3` 主/备用纹理的固定像素 `(625,570)`。本轮 `evidence-release200-iteration-1080-v3-gbuffer` 完成 9 个阶段、36 次纹理读取和 9 张截图，均无 GL 错误；探针未更改 blend、depth 或 shader。

IterationRP Alpha 0.8.28 的 `shaders.properties` 将 `gbuffers_textured` 与 `gbuffers_textured_lit` 的混合设为 `off`。`Lib/Programs/Gbuffers/Textured_FS.glsl` 只输出到 `colortex3`，用 `Pack2xU8_to_U16` 将 `albedo.rg`、`albedo.ba`、材质 ID 和光照打包；之后由透明合成流程读取。2.0.0 的 Iris 适配器通过 `TEXTURED_COLOR` 克隆的就是该通道。

按照该包 `Lib/Utilities.glsl` 的解码公式，还原读取的材质 RGBA8：

| 场景 | 合成前 `colortex3` 内的 RGBA8 |
| --- | --- |
| 两层 | `[254,29,29,127]` |
| 仅前红层 | `[255,30,30,128]` |
| 仅后蓝层 | `[30,60,255,128]` |
| 重复两层 | `[254,29,29,127]` |

这表明在最终光影合成前，重叠像素已经只保存前红层材质及约 0.5 的 alpha，没有累积后蓝层。小于等于 1/255 的编码差异不改变这一结论。原始数据在 `gbuffer.txt`，解码数据在 `decoded-gbuffer.json`。同期 `colortex0` 保持石墙数据 `[0.49803925,0.49803925,0.49803925,1.0]`，后蓝层并未先合入该缓冲。

因此，当前证据指向 **Halo 所选 Iris 通道与该包单层打包材质缓冲的兼容限制**，不能简单归结为本次透明索引缓存、最终屏幕 alpha 或只是一处 depthMask 设置。关闭深度写入也不会令一个关闭混合的单层缓冲自动累积颜色，而且已有 [背景深度修复记录](mesh-verification.md) 表明它会影响 IterationRP/Bliss 的位置还原。

本地历史 Alpha 0.8.22 包也有相同的 blend 配置和打包输出；本轮只做了该旧包的源码核对，未运行游戏，不能据此声称所有历史版本/设置均有同样结果。两个包的 SHA-256 与相关语句保存在 `shader-contract.json`。

直接对 `colortex3` 启用常规 alpha 混合会对打包材质字段插值，不能作为安全修复。本轮没有修改光影包或交付新的渲染行为。若继续解决这一兼容限制，应在 Iris 适配器中独立研究能够保留分层颜色贡献的提交/合成路径，重新验证光照、雾、深度、其他光影包及成本；不能在 core 加光影包特判，也不能把换一个视觉效果不同的 shader 通道称为无损修复。

## 已确认边界

2.0.0 是首个 mesh Release，而本轮已在它的正式构建中复现。因此目前没有“2.0.0 正常、之后某个优化版本退化”的证据，不能把本问题归因于本次矩阵/索引/材质缓存优化，也没有据此回退优化或修改 core。

后续诊断应从现有 Iris 材质通道的兼容性入手；若要追溯用户记忆中的正常表现，需要能复现的具体光影版本、配置及渲染路径，不能以记忆代替一个通过的版本端点。
