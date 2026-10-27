# 回放兼容 2.6.0-alpha.2 验证记录

本文件保留对应 alpha 构建时的历史状态；维护者后续已确认修复通过，当前发布记录见 [2.6.0](replay-verification-2.6.0.md)。

日期：2026-10-27（Asia/Hong_Kong）。状态：自动回归通过，等待维护者复测 ReplayMod 画面。

## 问题与修复

维护者使用 alpha.1 反馈：ReplayMod 在回放及渲染时偶尔出现光环 snap/复位；Flashback 暂未发现问题。
Flashback 的反馈只记录为本轮已测试场景未发现问题，不扩展为所有组合和场景正式验收通过。

ReplayMod 每约两秒记录完整快照。共享 timeline 原先在连续推进时也调用完整恢复，
导致表现实例、物理惯性和预览会话被重建。该行为不是正常播放或导出的预期。

core 现在跟踪已恢复/应用的序号水位：连续播放时，已被事件覆盖的快照保留为后续跳转基线，
不重新安装表现。跳转时间轴仍使用最近快照；快照水位较新时仍补齐缺失事件。
初始空白区间之后序号为零的真实快照也能恢复。公开 API、schema 1 与容器布局未改变，
现有包含 Halo 轨道的录像无需重录；Flashback 专属接线未修改。

## 自动验证

- 修复前新增回归可复现：连续播放实例保持测试、实际 body/animation 管线对照测试均失败。
- 修复后 core 364 个测试全部通过，0 失败/错误/跳过。
- 对照相同连续动作有/无 2 秒周期快照，普通回放及按 2× 输出的物理姿态和动画时间均一致。
- 覆盖跳转仍恢复、较新快照补齐缺失事件、首个零序号快照，以及已有的迟到记录和同时间顺序测试。
- 1.20.1 Fabric 联合构建：132 个测试，0 失败/错误、1 跳过。
- 1.21.1 Fabric 联合构建：136 个测试，0 失败/错误、1 跳过；两个官方 JAR ABI 测试实际运行并通过。
- 唯一跳过项仍为缺少外部 fixture 的 YsmReleaseSignatureTest。
- 旧调用方、平台边界、合包来源检查通过。alpha.2 尚未重新做人工回放/导出验收。

修复前后日志：根 `.local/replay-snap-regression-before.log`、`.local/replay-snap-regression-after.log`。
完整构建日志：`.local/replay-core-build-alpha2.log`、`.local/replay-build-1.20.1-alpha2.log`、
`.local/replay-build-1.21.1-alpha2.log`。官方模组版本及 alpha.1 启动检查见[首版记录](replay-verification-2.6.0-alpha.1.md)。

## 源码与测试包

core 为 `2.6.0-alpha.2`，确定提交 `2af7543e8faa62ad0c5c0e6eb31b3d786d907a77`，本地分支 `codex/replay-api-v2` 干净。
两个 Halo 工作区均使用此提交；已提交的 gitlink 仍为 `59e3bb2e3139b1c0982c0d992525bc3032b4c69b`。

- Halo 1.20.1：`codex/replay-compat-1.20.1`，HEAD `66af36090f791a89f0ef9dd0e385d62ef02839b2`，适配改动未提交。
- Halo 1.21.1：`codex/replay-compat-1.21.1`，HEAD `fc6492ba2c93cf25d5c96e05f823bdc24486c27f`，适配改动未提交。
- 适配修订 1；测试包带 `.dev`，双方均未推送、打标签或发布。
- 成品与 SHA-256 清单保存在 `F:/Halo/.local/replay-testing/artifacts/manifest.json`。

## 本轮人工复测

替换 1.21.1 Fabric 的 Halo 为 alpha.2，仅启用 ReplayMod，直接打开原来出现复位的录像：

1. 连续播放至少 10 秒，包含移动和转头；观察原复位位置及约两秒快照边界。
2. 原渲染设置重新导出相同片段，检查复位是否消失，再检查暂停和倍速。
3. 前后拖动后继续播放，确认目标状态恢复且之后保持连续；若使用 Quick 模式，也各复测一次。

真实跳转时仍允许按约定清除未来惯性。若连续播放/导出仍复位，请提供录像、模组版本、
发生时间点和 latest.log，以区分其它实体/时间边界问题。
