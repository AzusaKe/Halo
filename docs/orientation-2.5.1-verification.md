# HaloCore 2.5.1 朝向修复逐平台验证

## 结论与来源

维护者于 2026-10-22 手动确认 1.20.1 Fabric 的 SYNC 和 LOCKED 两项 bug 全部消失，
随后授权继续其余 9 个版本。该人工确认限于维护者的主线实测；其余平台在下表记录
各自的自动定向游戏结果。10 条标准分支均完成联合构建与原生游戏验证。

游戏验收基线为 HaloCore **2.5.1 / `77cb1d62b3a3c8ce3ba2e56c8d16366e2518e433`**。
正式发布锁定 **`59e3bb2e3139b1c0982c0d992525bc3032b4c69b`**：后续提交仅将 core README／CHANGELOG 从开发阶段标记为正式 2.5.1，生产源码不变。
根据维护者的发布指令，10 条分支的 adapter revision 统一重置为 **1**。
SYNC 的独立 core 提交为 `4c43c38`，LOCKED 的后续独立提交为 `77cb1d6`；
主线 SYNC 首次集成为 `0d81a89`。没有修改 JSON schema、存档、协议或公开 API。
core 的 347 项测试与冻结旧调用方、平台边界检查通过。

维护者确认的主线测试包为 `halo-1.20.1-fabric-2.5.1+adapter.3.dev.jar`，
SHA-256 为 `84575742d6c2ea63215c9544ed52e7a73b7b24c820eedb42947f8a43b1ae4ce8`。
后续提交和清洁构建保持同一 core 生产源码，更新 gitlink 与构建来源元数据。
26.1 两条分支的实际游戏基线是 Minecraft 26.1.2，不据此新增对 26.1/26.1.1 的兼容承诺。

## 各平台结果

| 分支 | JDK | 联合构建／平台测试 | 原生单人定向游戏 |
| --- | --- | --- | --- |
| 1.20.1-fabric | jdk-17.0.11.9-hotspot | 通过，126 项通过／1 项跳过 | 15 场景通过，657 样本；维护者手动确认 |
| 1.20.1-forge | jdk-17.0.11.9-hotspot | 通过，130 项通过／2 项跳过 | 15 场景通过，656 样本 |
| 1.21.1-fabric | jdk-21 | 通过，128 项通过／1 项跳过 | 15 场景通过，662 样本 |
| 1.21.1-neoforge | jdk-21 | 通过，129 项通过／1 项跳过 | 15 场景通过，657 样本 |
| 26.1-fabric | jdk-25.0.4.7-hotspot | 通过，131 项通过／0 项跳过 | 15 场景通过，658 样本 |
| 26.1-neoforge | jdk-25.0.4.7-hotspot | 通过，140 项通过／1 项跳过 | 15 场景通过，662 样本 |
| 26.2-fabric | jdk-25.0.4.7-hotspot | 通过，136 项通过／0 项跳过 | 15 场景通过，659 样本 |
| 26.2-neoforge | jdk-25.0.4.7-hotspot | 通过，142 项通过／0 项跳过 | 15 场景通过，662 样本 |
| 26.3-fabric | jdk-25.0.4.7-hotspot | 通过，140 项通过／0 项跳过 | 15 场景通过，661 样本 |
| 26.3-neoforge | jdk-25.0.4.7-hotspot | 通过，146 项通过／0 项跳过 | 15 场景通过，660 样本 |

跳过的外部发布包 ABI 检查（不计为通过）：

- `1.20.1-fabric`：official YSM 2.6.5 jar retains every pinned adapter signature。
- `1.20.1-forge`：official Forge EMF 3.1.1 jar retains the pinned model-part ABI；official YSM 2.6.5 jar retains every pinned adapter signature。
- `1.21.1-fabric`：official YSM 2.6.5 jar retains every pinned adapter signature。
- `1.21.1-neoforge`：official YSM 2.6.5 jar retains every pinned adapter signature。
- `26.1-neoforge`：officialHotfixRetainsPinnedContract()。

## 实际构建与游戏依赖

下表记录本轮默认构建和客户端实际使用的依赖；没有额外覆盖加载器版本。JDK 见上表，core 固定为 2.5.1。

| 分支 | Minecraft | 加载器 | 配套 API | 映射 |
| --- | --- | --- | --- | --- |
| 1.20.1-fabric | 1.20.1 | Fabric Loader 0.15.11 | Fabric API 0.92.0+1.20.1 | 1.20.1+build.10 |
| 1.20.1-forge | 1.20.1 | Forge 47.4.0 | — | Mojang 官方名称 |
| 1.21.1-fabric | 1.21.1 | Fabric Loader 0.16.14 | Fabric API 0.116.15+1.21.1 | 1.21.1+build.3 |
| 1.21.1-neoforge | 1.21.1 | NeoForge 21.1.248 | — | Parchment 2024.11.17 |
| 26.1-fabric | 26.1.2 | Fabric Loader 0.19.3 | Fabric API 0.155.3+26.1.2 | Mojang 官方名称 |
| 26.1-neoforge | 26.1.2 | NeoForge 26.1.2.95 | — | Mojang 官方名称 |
| 26.2-fabric | 26.2 | Fabric Loader 0.19.3 | Fabric API 0.157.0+26.2 | Mojang 官方名称 |
| 26.2-neoforge | 26.2 | NeoForge 26.2.0.59 | — | Mojang 官方名称 |
| 26.3-fabric | 26.3 | Fabric Loader 0.19.5 | Fabric API 0.160.6+26.3 | Mojang 官方名称 |
| 26.3-neoforge | 26.3 | NeoForge 26.3.0.64-beta | — | Mojang 官方名称 |

## 游戏用例与证据

每个版本独立使用隔离测试世界副本，以对应加载器和 JDK 启动实际客户端。
临时诊断代理通过游戏自身的客户端执行器设置姿态、挂载和资源重载，读取实际世界
渲染产生的 `BodyPose`，不修改 Halo 源码、不加入安装包。

每个平台均执行 15 个场景：HUD 与 Seia 参数复现各 4 组首次 yaw/pitch；保持实例
并改变 Seia 距离和 `sync_offset` 后重载；内置 `mesh_demo` 在 -2°、-0.1°、0°、
+0.1°、+2°各充分静止；最后进行 14 秒 ±2°慢速俯仰。LOCKED 保留原始模型的组旋转
和视觉动画，以动画之前的根姿态做断言。SYNC 单位轴距离容差为 0.002；LOCKED
头部前向与光环前向点积须大于 0.995，拒绝持续反向和小幅俯仰引入的翻转。

诊断工具位于 `F:/codex-cache/halo-orientation-2.5.1/verifier/`。每个结果文件夹包含
完整结果和 15 张实际帧缓冲截图，游戏输出日志为相邻的 `game.log`：

- `1.20.1-fabric`：`F:/codex-cache/halo-orientation-2.5.1/1.20.1-fabric/game`；15 张截图，`complete.txt`、`results.txt`。
- `1.20.1-forge`：`F:/codex-cache/halo-orientation-2.5.1/1.20.1-forge/game-continued`；15 张截图，`complete.txt`、`results.txt`。
- `1.21.1-fabric`：`F:/codex-cache/halo-orientation-2.5.1/1.21.1-fabric/game-continued-2`；15 张截图，`complete.txt`、`results.txt`。
- `1.21.1-neoforge`：`F:/codex-cache/halo-orientation-2.5.1/1.21.1-neoforge/game-continued`；15 张截图，`complete.txt`、`results.txt`。
- `26.1-fabric`：`F:/codex-cache/halo-orientation-2.5.1/26.1-fabric/game-continued`；15 张截图，`complete.txt`、`results.txt`。
- `26.1-neoforge`：`F:/codex-cache/halo-orientation-2.5.1/26.1-neoforge/game-continued`；15 张截图，`complete.txt`、`results.txt`。
- `26.2-fabric`：`F:/codex-cache/halo-orientation-2.5.1/26.2-fabric/game-continued-2`；15 张截图，`complete.txt`、`results.txt`。
- `26.2-neoforge`：`F:/codex-cache/halo-orientation-2.5.1/26.2-neoforge/game-continued`；15 张截图，`complete.txt`、`results.txt`。
- `26.3-fabric`：`F:/codex-cache/halo-orientation-2.5.1/26.3-fabric/game-continued`；15 张截图，`complete.txt`、`results.txt`。
- `26.3-neoforge`：`F:/codex-cache/halo-orientation-2.5.1/26.3-neoforge/game-continued`；15 张截图，`complete.txt`、`results.txt`。

1.20.1 Forge 的首次诊断因工具过早反射加载客户端类而干扰 Forge 构造，已改为
等待客户端进入主循环并使用实际类加载器。1.21.1 资源包接口名称及新版本异步截图
也在诊断工具中适配。早期失败尝试保留，不计作验收；没有为这些工具问题修改产品代码。

## 验收边界与交付

本次为两项朝向修复的原生单人定向验收，不扩展为专用服双客户端、本地模式、
EMF/Iris/YSM 组合、LabPBR 或性能压测全部通过。构建中的跳过项亦单独列出。
冻结和特殊适配分支保持原样。

各分支分别提交 gitlink 和配套说明，固定同一 core SHA；只做本地提交和快进集成。
以上本地验证阶段没有推送或创建标签／Release。2026-10-22 维护者授权发布准备，
随后将按 core 在先、Halo 在后的顺序推送并创建 2.5.1 标签；云端构建及 Release
审阅结果另行记录。旧标签保持不动。最终安装包、SHA-256 和
每个平台 Halo SHA 记录于 `F:/codex-cache/halo-orientation-2.5.1/artifacts/manifest.json`。
