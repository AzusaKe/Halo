# 回放兼容 2.6.0 验证与发布范围

日期：2026-10-27（Asia/Hong_Kong）。维护者已确认本轮修复通过，并要求提交、推送及创建 GitHub Release。

## 人工反馈

首次 1.21.1 Fabric 测试发现 ReplayMod 在回放和导出时周期性 snap；Flashback 暂未发现问题。
修复后维护者明确反馈：“经过检查，该bug已消失，且未观测到其他异常”。
本记录据此确认已报告问题修复通过，不补写未经逐项报告的测试场景、FPS、光影或第三方组合。

问题来自连续推进时重新安装周期快照，清空了已有物理惯性。共享 timeline 现在跳过已被事件水位覆盖的
周期快照，仍保留跳转、较新快照补齐缺失变化和首个零序号快照恢复。已有 Halo 轨道的录像无需重录。

## 源码与范围

- HaloCore：`2.6.0`，确定提交 `03f84db58dc677f405796b422a8897fcf556bda1`，发布标签 `v2.6.0`。
- 正式版相对已验收 `2.6.0-alpha.2 / 2af7543e8faa62ad0c5c0e6eb31b3d786d907a77` 只调整版本与发布文档，功能源码相同。
- 1.21.1 Fabric：发布 `2.6.0+adapter.1`，标签 `v2.6.0-fabric-1.21.1-adapter.1`，目标分支 `1.21.1-fabric`。
- 1.20.1 Fabric：仅共享宿主、codec、UI 和写回隔离；提交并推送 `codex/replay-compat-1.20.1`，不打标签，不创建该平台 Release，尚无具体录制后端验收结论。
- 其它游戏版本、加载器和冻结 flash 分支不迁移。

## 自动检查与游戏环境

| 检查 | 本地结果 |
| --- | --- |
| core Java 17 独立构建、依赖/平台边界、旧调用方、独立回放/预览消费者 | 364 项测试全部通过，无失败/错误/跳过 |
| 1.20.1 Fabric 共享接入 | 共 132 项测试，131 通过，1 跳过 |
| 1.21.1 Fabric 具体后端 | 共 136 项测试，135 通过，1 跳过 |
| 指定 ReplayMod / Flashback 官方 JAR ABI | 两项均实际运行并通过 |
| 回放 snap 行为回归 | 修复前复现失败；修复后普通回放/2× 输出的连续 body/animation 对照一致 |
| 旧 API 字节码调用方、平铺合包和来源检查 | 通过；正式发布另用 `build -Prelease=true` 校验最终已提交 gitlink |

本地唯一跳过项为缺少外部 fixture 的 `YsmReleaseSignatureTest`。
云端若没有两个录制模组 fixture，ABI 用例会另外跳过；其实际 JAR 检查证据来自本地，不声称云端执行了这些外部用例。

1.21.1 使用 Java 21 构建，依赖 Fabric Loader >=0.16.14、Fabric API 0.116.15+1.21.1。
成品启动检查使用 Fabric Loader 0.19.3，两个录制模组分别运行：

| 模组 | 版本 | SHA-256 |
| --- | --- | --- |
| ReplayMod | `1.21-2.6.28` | `b6ac7feb7f8eeefcfbf90ad3f1a45d8cda8df35005d8a308748adbe4e1652ada` |
| Flashback | `0.39.10` for MC 1.21.1 | `34e5b1105a7d5496efaec24c7d64d2343b79fd8fa43f5d29cd948aea0dbd2e62` |

未安装录制模组、两个官方后端各自启动加载已有检查；维护者负责实际游戏操作与画面验收。
marker 裁剪/拆分、多视角、Iris/LabPBR 及其它未逐项确认的组合继续保留独立验收边界。
录像仅保存定义标识和状态，不包含资源；复现需固定资源包、模型、纹理及配置。

## 发布与追溯

core 先提交、推送并打标签，Halo 再提交确定 gitlink；正式 JAR 的 `development=false`，
`coreCommit == coreLock == 03f84db58dc677f405796b422a8897fcf556bda1`。玩家只安装对应 Halo JAR，不额外安装 core 库。

[HaloCore Release](https://github.com/AzusaKe/HaloCore/releases/tag/v2.6.0) ·
[Halo 1.21.1 Fabric Release](https://github.com/AzusaKe/Halo/releases/tag/v2.6.0-fabric-1.21.1-adapter.1)

本轮指令只创建 GitHub Release；Modrinth/CurseForge 不另行分发。
Release 页面及标签 CI 保存最终发布结果、成品附件和来源 SHA；本地发布回执保存在根 `.local/replay-release-2.6.0/`。
详细开发及回归过程见 [alpha.1](replay-verification-2.6.0-alpha.1.md)、[alpha.2](replay-verification-2.6.0-alpha.2.md) 历史记录。
