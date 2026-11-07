# 官方回放兼容 2.6.0 发布登记

2026-11-07：维护者已明确确认固定官方交集的五个平台全部验收通过，并另行授权提交、推送、打标签及创建 GitHub Release。本轮只发布这些 Fabric 平台。Forge、NeoForge、非官方移植和冻结分支不在范围内；未授权上传 Modrinth／CurseForge。

core 源码无新增改动，沿用已发布的 [HaloCore v2.6.0](https://github.com/AzusaKe/HaloCore/releases/tag/v2.6.0)，提交 `03f84db58dc677f405796b422a8897fcf556bda1`；保留原标签、双语变更说明、runtime／sources JAR 及校验文件，不创建空提交或替换既有资产。

以下标签按已有格式登记。正式构建必须来自干净 Halo／core 工作树，gitlink 等于固定 core，成品 `development=false`、无 `.dev`，并核对 `halo-build.json`。发布结果以对应 GitHub 标签 CI 和 Release 为准，本登记不提前宣称 CI 成功。

| 平台 | 正式版本 | 目标标签／Release | 原验收开发 JAR SHA-256 | 人工通过日期 |
| --- | --- | --- | --- | --- |
| 1.20.1 Fabric | `2.6.0+adapter.2` | [v2.6.0-fabric-1.20.1-adapter.2](https://github.com/AzusaKe/Halo/releases/tag/v2.6.0-fabric-1.20.1-adapter.2) | `c01a2438fbfe7daf5d08d643a96ab237f42c08ca1768f7a802b61c4c29915467` | 2026-10-30 |
| 1.21.1 Fabric | `2.6.0+adapter.2` | [v2.6.0-fabric-1.21.1-adapter.2](https://github.com/AzusaKe/Halo/releases/tag/v2.6.0-fabric-1.21.1-adapter.2) | `db0fdbae82f7244d2c607b3a9c8f9dffc200c868c8de7f3fb69ffeed0a464185` | 2026-10-30 |
| 26.1.2 Fabric | `2.6.0+adapter.1` | [v2.6.0-fabric-26.1-adapter.1](https://github.com/AzusaKe/Halo/releases/tag/v2.6.0-fabric-26.1-adapter.1) | `9610c37fdb1bc928138cb7281ce9d2d65658139354628f94189b8deea6006619` | 2026-10-30 |
| 26.2 Fabric | `2.6.0+adapter.1` | [v2.6.0-fabric-26.2-adapter.1](https://github.com/AzusaKe/Halo/releases/tag/v2.6.0-fabric-26.2-adapter.1) | `762161c280094a28f153070253da55839f52fae6900b32b5b71729ce6539cac7` | 2026-11-07 |
| 26.3 Fabric | `2.6.0+adapter.1` | [v2.6.0-fabric-26.3-adapter.1](https://github.com/AzusaKe/Halo/releases/tag/v2.6.0-fabric-26.3-adapter.1) | `f2e68c81a87adbd77b3cfa319b0de3c5c7058bff26a9faabdb881a4bda07cc6e` | 2026-11-07 |

原始开发 JAR、manifest 和验收回执保持原样。正式包因版本标识、提交来源和打包元数据不同而具有不同 SHA-256，不能用开发包 SHA 代替正式资产校验。发布过程中另存正式 JAR 的来源和哈希；不把生成正式包或云端构建当成新增游戏验收。

## 行为与测试边界

- 复用固定 core API v2、schema 1 和纳秒时间契约，普通回放／导出按录像源时间推进；暂停及重复视角不额外推进，连续跨过已覆盖快照保留惯性，seek 恢复相位并清除未来运动。
- UI 默认隐藏；客户端 `config/halo-azusake/halo_mod_config.json` 中 `replayScepterUiEnabled=true` 可显式启用，修改后重启生效。播放或暂停时可手动关闭，恢复播放不强制重开；下一次录制的打开事件和 seek 新 epoch 可恢复。导出始终隐藏交互 UI，不写回 C2S。
- 1.20.1 只声明官方 ReplayMod 交集：2.6.28 为主目标，并检查 2.6.23；不声明 Flashback 官方支持。1.21.1 固定 ReplayMod 1.21-2.6.28／Flashback 0.39.10；26.1.2 固定 26.1-2.6.28／0.43.3；26.2、26.3 分别使用各自 MC 文件的 2.6.28／0.43.6。两个后端分开安装。
- 26.1 分支和旧标签格式保留，但本轮人工验收范围只明确到 26.1.2；26.1／26.1.1 的构建矩阵只验证 API 链接，不据此扩大回放游戏验收范围。
- 已有 core 364 项测试、相关协议／codec／UI／配置、真实官方 JAR ABI、旧 API、合包／来源和客户端／专服隔离检查保留在各交测记录。正式构建重新提供固定官方 fixture，单独列明其它跳过项。
- 录像不内嵌资源，旧录像缺失的信息无法补录，不保证原游玩物理轨迹或跨 FPS 逐帧一致。未逐项报告的 marker、多视角、Iris/LabPBR 和第三方模型组合继续保持独立验收边界。
- 26.3 首次多个客户端同时启动期间出现过 `jvm.dll / 0xc0000005` 原生崩溃，同一成品单独复跑通过；原因尚未定位。人工通过不代表这一原生崩溃已修复，原日志与 Windows 事件继续保留。

平台清单与人工验收回执见 [验收记录](replay-platform-acceptance.md)。仅提交／发布说明整理的变化执行文档检查；正式构建结果、CI 及资产哈希在发布后另行核对。
