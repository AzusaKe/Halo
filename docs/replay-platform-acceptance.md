# 固定官方回放交集与串行验收

2026-11-07 更新。本轮沿用 2026-10-28 锁定的官方交集；各平台开始时核对并固定实际文件，不自动改用更新发布或扩展游戏版本。官方依据：[ReplayMod 官网](https://www.replaymod.com/download/)、[ReplayMod 文件元数据](https://api.modrinth.com/v2/project/replaymod/version)、[Flashback 文件元数据](https://api.modrinth.com/v2/project/flashback/version)。精确文件 ID、内嵌依赖与 SHA 见各平台 fixtures/manifest.json。

| MC | Halo 加载器／维护分支 | ReplayMod | Flashback | 验收状态 |
| --- | --- | --- | --- | --- |
| 1.20.1 | Fabric / 1.20.1-fabric | 1.20.1-2.6.28；另检查 2.6.23 | 无 | 2026-10-30 已明确通过 |
| 1.20.1 | Forge | 无 | 无 | 无官方交集，不适配 |
| 1.21.1 | Fabric / 1.21.1-fabric | 1.21-2.6.28 | 0.39.10 | 基准沿用；UI 开发包 2026-10-30 已明确通过 |
| 1.21.1 | NeoForge | 无 | 无 | 无官方交集，不适配 |
| 26.1.2 | Fabric / 26.1-fabric | 26.1-2.6.28 | 0.43.3，明确声明 26.1.2 | 2026-10-30 已明确通过 |
| 26.1.2 | NeoForge | 无 | 无 | 无官方交集，不适配 |
| 26.2 | Fabric / 26.2-fabric | 26.2-2.6.28 | 0.43.6 MC26.2 文件 | 2026-11-07 已明确通过 |
| 26.2 | NeoForge | 无 | 无 | 无官方交集，不适配 |
| 26.3 | Fabric / 26.3-fabric | 26.3-2.6.28 | 0.43.6 MC26.3 文件 | 2026-11-07 已明确通过 |
| 26.3 | NeoForge | 无 | 无 | 无官方交集，不适配 |

共 5 个平台至少支持一个官方录制器，其中 4 个同时相交。非官方移植、Connector、网易环境及冻结分支不进入此队列。26.1.2 不因 Flashback 的 MC26.1 文件名推定其精确版本支持。

验收回执独立保存，不篡改交付时的待验收 manifest。已通过回执：

- `F:/Halo/.local/replay-ui-1.21.1-20261030/artifacts/acceptance.json`
- `F:/Halo/.local/replay-ui-1.20.1-20261030/artifacts/acceptance.json`
- `F:/Halo/.local/replay-26.1.2/artifacts/acceptance.json`
- `F:/Halo/.local/replay-26.2/artifacts/acceptance.json`
- `F:/Halo/.local/replay-26.3/artifacts/acceptance.json`

最后一个平台（26.3 Fabric）已获维护者“测试通过”确认；原测试包、来源及步骤见 `F:/codex-cache/worktree/replay-compat-263/Halo/docs/replay-verification-26.3.md`，固定包位于 `F:/Halo/.local/replay-26.3/artifacts/`。两个后端分开安装，使用同一个 Halo JAR。自动检查和启动不能替代实际回放、导出和画面验收；既有通过不推定未逐项报告的 Iris/LabPBR、第三方模型和多视角组合已测。

固定 core 2.6.0 / `03f84db58dc677f405796b422a8897fcf556bda1`，本轮适配未改 core 源码。维护者已于 2026-11-07 明确确认 26.3“测试通过”，固定队列的 5 个平台全部完成验收；随后另行明确授权提交、推送、打标签并创建 GitHub Release。原始交付清单和验收回执保持不变，正式提交／标签登记见 [发布记录](replay-release-2.6.0.md)，实际发布状态以远端引用和 CI／Release 为准。26.3 首次启动的 JVM 原生崩溃仍保留在历史证据中，不能由人工通过推定其原因已定位或已修复。
