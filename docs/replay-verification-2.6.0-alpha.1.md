# 回放兼容 2.6.0-alpha.1 验证记录

本文件保留对应 alpha 构建时的历史状态；维护者后续已确认修复通过，当前发布记录见 [2.6.0](replay-verification-2.6.0.md)。

日期：2026-10-27（Asia/Hong_Kong）。状态：首版实现，等待维护者人工录制/回放/导出验收。


维护者后续反馈：ReplayMod 回放/渲染偶发 snap，Flashback 暂未发现问题。周期快照恢复问题已在
[alpha.2 修复记录](replay-verification-2.6.0-alpha.2.md)处理；下文保留 alpha.1 的构建/启动证据。

## 工作分支与源码

| 仓库/工作区 | 分支与基线 | 当前状态 |
| --- | --- | --- |
| Halo `F:/Halo` | `codex/replay-compat-1.20.1`，基线 `66af36090f791a89f0ef9dd0e385d62ef02839b2` | 共享宿主、codec、UI/写回隔离及文档；未提交 |
| Halo 1.21.1 工作区 | `codex/replay-compat-1.21.1`，基线 `fc6492ba2c93cf25d5c96e05f823bdc24486c27f` | 共享接线和两个后端；未提交 |
| HaloCore | `codex/replay-api-v2`，基线 `59e3bb2e3139b1c0982c0d992525bc3032b4c69b` | 已本地提交到 `f0f9cd97e05910b2941397650e5582da611297c9` |

1.21.1 工作区为 `F:/codex-cache/worktree/replay-compat-1211/Halo`。
两个工作区的 core 均使用上述确定提交；未自动追踪上游。Halo 已提交的 gitlink 仍为旧基线，
工作树 gitlink 指向本次 core，故成品带 `.dev`；core 与 Halo 均未推送、打标签或发布。

## 自动验证

| 检查 | 结果 |
| --- | --- |
| core 独立构建、边界、旧调用方、独立回放 API 调用方 | 通过；361 个测试，0 失败/错误/跳过 |
| 1.20.1 Fabric 联合构建 | 通过；132 个测试，0 失败/错误、1 跳过 |
| 1.21.1 Fabric 联合构建 | 通过；136 个测试，0 失败/错误、1 跳过 |
| 两个平台旧 API v2 字节码调用方、平铺 core/JAR provenance | 通过 |
| ReplayMod / Flashback 指定官方 JAR ABI | 两项均实际运行并通过 |
| 文档/工作树 whitespace 检查 | 通过 |

唯一跳过项为 `YsmReleaseSignatureTest`，未提供其外部 YSM fixture；不写成兼容通过。
测试覆盖快照、水位、同时间顺序、空状态、关闭中间态、延迟实体、暂停/重复视角、源/输出时间分离、
UI 会话、断开前关闭事件、失效句柄、原始时间折叠与中立 timeline 恢复；不替代真实客户端画面验收。

日志集中保存于根 `.local/replay-core-build.log`、`.local/replay-build-1.20.1.log`、
`.local/replay-build-1.21.1.log`；JUnit 明细在各工作区 `build/test-results/test/`。

## 官方 JAR 与加载检查

| 模组 | 目标 | SHA-256 |
| --- | --- | --- |
| ReplayMod | `replaymod-1.21-2.6.28.jar` | `b6ac7feb7f8eeefcfbf90ad3f1a45d8cda8df35005d8a308748adbe4e1652ada` |
| Flashback | `Flashback-0.39.10-for-MC1.21.1.jar` | `34e5b1105a7d5496efaec24c7d64d2343b79fd8fa43f5d29cd948aea0dbd2e62` |

使用互相隔离的客户端目录，只复制 Halo、Fabric API 和当前录制模组，不修改日常实例和存档。
无录制模组的开发客户端完成初始化/资源加载；两个官方模组分别按成品模式完成初始化和资源加载。
后端在初始化时加载并检查实际转换后的可选目标；日志确认回调/边界 hook 可用，导出 `.mixin.out`
中包含 Recorder、ReplayReader、ReplayServer、ExportJob 以及 PacketListener、VideoRenderer、marker/filter 等目标。

启动检查修复了 ReplayMod `Event.register` 非公开的问题，改用公开 `EventRegistrations`。
Flashback 官方 JAR 的 Loom 1.17.21 标记不能直接交给分支 Loom 1.10.5 开发重映射，因此加载检查使用
既有 Fabric Loader 0.19.3 的成品类路径；未升级构建链或改写官方 JAR。
该限制仅针对开发重映射，不当作游戏不兼容结论。截图未作为可靠视觉验收证据。

## 尚待人工验收

[首轮步骤](replay-compat.md#首轮人工验收步骤) 使用新录像检查已有佩戴、佩戴/摘除、暂停/倍速、
前后拖动、权杖关闭、Full/Quick、非零起点与 30/60 FPS 导出，再检查取消/重启。
后续继续验证来源回退、健康实体卸载、暂停录制/marker 裁剪/拆分、Flashback 分段快照、多视角及光影。
以上实际功能尚无通过结论，不能依据构建、ABI 或启动加载结果发布正式支持声明。

测试安装包位于根 `.local/replay-testing/artifacts/`，只安装普通 JAR；版本与 hash 见同目录清单。
