# Minecraft 1.20.1 Fabric 官方回放适配交测

> 本文保留 2026-10-28 adapter.1.dev 的历史证据；请改用 [2026-10-30 adapter.2.dev UI 修复交测](replay-ui-1.20.1-verification.md) 中的新 JAR。1.20.1 仍等待独立验收。

日期：2026-10-28（Asia/Hong_Kong）。**状态：自动检查完成，等待维护者实际游戏测试；尚未验收通过。**

## 范围与来源

- 当前工作分支：`codex/replay-compat-1.20.1-official`，Halo 基线 `003dfe1c0c32da138076dc145c5462bbfada17ca`，改动尚未提交。
- 参考已验收 `1.21.1-fabric@f9238bdd4dd590a3cdf9d517766c71cf57661607`，只迁入 ReplayMod 后端及必要宿主接入，不整分支合并。
- core 保持 `2.6.0 / 03f84db58dc677f405796b422a8897fcf556bda1`，源码和 gitlink 均未修改。
- 新增 1.20.1 `CustomPayloadS2CPacket` / `PacketByteBuf` 封装；保持 `halo:replay` schema 1、API v2、共享 timeline、源时间、快照水位与 UI 写回隔离语义。
- 回放 Mixin 整份配置限定 `environment: client`，专用服务端不加载后端、探测插件或第三方回放类。
- 未提交、推送、打标签或发布；没有改动后续平台。

2026-10-28 复核的官方交集如下；分支 `26.1-*` 实际目标为 **26.1.2**。依据 [ReplayMod 官网](https://www.replaymod.com/download/)、[ReplayMod 文件元数据](https://api.modrinth.com/v2/project/replaymod/version)和 [Flashback 文件元数据](https://api.modrinth.com/v2/project/flashback/version)。

| Minecraft / 加载器 | ReplayMod | Flashback | 队列 |
| --- | --- | --- | --- |
| 1.20.1 Fabric | 1.20.1-2.6.28；兼查 2.6.23 | 无官方版 | 当前交测 |
| 1.20.1 Forge | 无 | 无 | 无官方交集 |
| 1.21.1 Fabric | 1.21-2.6.28 | 0.39.10 | 沿用既有验收 |
| 1.21.1 NeoForge | 无 | 无 | 无官方交集 |
| 26.1.2 Fabric | 26.1-2.6.28 | 0.43.3 | 等当前平台明确通过后开始 |
| 26.1.2 NeoForge | 无 | 无 | 无官方交集 |
| 26.2 Fabric | 26.2-2.6.28 | 0.43.6 | 后续串行 |
| 26.2 NeoForge | 无 | 无 | 无官方交集 |
| 26.3 Fabric | 26.3-2.6.28 | 0.43.6 | 后续串行 |
| 26.3 NeoForge | 无 | 无 | 无官方交集 |

Flashback 0.43.6 的 MC26.1 文件仅声明 26.1，26.1.2 固定选择明确声明支持的 0.43.3。非官方移植、Connector、网易环境和冻结分支不在本轮范围。

## 固定测试依赖与成品

本次客户端成品启动环境：Minecraft **1.20.1**、Java **17.0.11**、Fabric Loader **0.19.3**、Fabric API **0.92.11+1.20.1**。联合构建及开发专用服基线仍为 Loader **0.15.11**、Fabric API **0.92.0+1.20.1**。

| 官方录像模组文件 | 来源 / 文件标识 | SHA-256 |
| --- | --- | --- |
| `replaymod-1.20.1-2.6.28.jar` | [官网版本入口](https://www.replaymod.com/download/download_new.php?version=1.20.1-2.6.28)，重定向至官方 minio；`website:1.20.1-2.6.28` | `822a64a34042e6ace2c025289f6a52feba6ebc980097cf4f72ac75b174ec1ee0` |
| `replaymod-1.20.1-2.6.23.jar` | [Modrinth ReB0T2tK](https://api.modrinth.com/v2/version/ReB0T2tK)；本地已有官方文件，哈希与历史调查一致 | `a46491d73456187ac888d31f6cc1160e42304427a63da88874c84abeb4d14033` |

两个文件内嵌 `id=replaymod`、版本分别为 `1.20.1-2.6.28` / `1.20.1-2.6.23`、`environment=client`；要求 Fabric Loader >=0.7.0 及 networking/key-binding/resource-loader 模块。其 `depends` 不单独声明 Minecraft 范围，不能据此外推游戏版本。完整内嵌元数据和来源保存在本地 `F:/Halo/.local/replay-1.20.1/fixtures/manifest.json`。

测试 JAR：`F:/Halo/.local/replay-1.20.1/artifacts/halo-1.20.1-fabric-2.6.0+adapter.1.dev.jar`。

SHA-256：`834f14a9d652ba737223ed1cd7c84cec87b921c86ee53b35ab42c7b0ce252c33`。

这是包含当前未提交适配源码的开发构建，`halo-build.json` 的 `haloCommit` 为上述 Halo 基线，`development=true`，`coreCommit == coreLock == 03f84db58dc677f405796b422a8897fcf556bda1`。不是正式发布包。玩家只装这一个 Halo JAR，不另装 core；不要同时保留旧 Halo JAR。

## 自动检查

| 检查 | 结果 |
| --- | --- |
| core 使用 Java 17 独立 `build --rerun-tasks` | 364 项全部通过，无失败、错误或跳过；包括边界、旧调用方、独立回放消费者 |
| Halo 联合 `build` | 135 项：134 通过，1 跳过，无失败或错误 |
| 2.6.28 / 2.6.23 真实 JAR ABI | 两项实际运行并通过，包含回调、索引、Quick 切换、逐帧与清理、marker 边界 |
| 1.20.1 payload 字节布局 | 实际序列化并验证通道后直接跟 schema-1 JSON，没有多余长度字段 |
| 合包、来源、旧 API v2 调用方 | 通过；成品无 ReplayMod / Flashback 类 |
| 成品客户端：无录制器 / 2.6.23 / 2.6.28 | 分别启动并完成 Halo 初始化和资源加载；两个录制器环境均报告必需兼容 hook 已启用 |
| 开发专用服 | 到达 `Done` 并通过 `stop` 保存退出；类加载记录检查客户端回放隔离 |

唯一跳过项是缺少 YSM 外部 fixture 的 `YsmReleaseSignatureTest`，不声称本轮验证了 YSM 组合。

可复核命令（PowerShell，仓库根目录）：

```powershell
$env:JAVA_HOME = 'D:/Program Files/Microsoft/jdk-17.0.11.9-hotspot'
$env:HALO_REPLAYMOD_TEST_JAR = 'F:/Halo/.local/replay-1.20.1/fixtures/replaymod-1.20.1-2.6.28.jar'
$env:HALO_REPLAYMOD_LEGACY_TEST_JAR = 'F:/Halo/.local/replay-1.20.1/fixtures/replaymod-1.20.1-2.6.23.jar'
.\core\gradlew.bat -p core build --console=plain
.\gradlew.bat build --console=plain
```

本地日志：`F:/Halo/.local/replay-1.20.1/core-build.log`、`build.log`，三个 `client-*/logs/latest.log`，以及 `F:/Halo/.local/smoke-server/logs/latest.log` / `loaded-classes.log`。启动检查不等于录制、回放、导出或画面验收。

## 维护者游戏测试

先在独立 1.20.1 实例安装上述 Halo 测试 JAR、Fabric API 和 **ReplayMod 2.6.28**，固定资源包、定义、模型及配置。每次只装一份 ReplayMod；2.6.23 用另一轮测试复查。Flashback 没有本平台官方版，不安装。

1. 使用明显带旋转／启动／关闭动画的光环，例如 `halo:ring_default`。先佩戴再进入新录制，录制至少 20 秒，移动、转头、摘除再佩戴；打开权杖分别用 ESC 和按钮关闭。
2. 打开新录像，检查起点佩戴和动画相位；暂停应冻结动画，0.5× / 1× / 2× 随源时间变化。连续播放跨过多个两秒周期快照，观察是否出现周期性 snap。
3. 前后 seek 到佩戴、摘除、启动／关闭动画和 UI 开关前后；分别检查 Full / Quick 和互切，关闭后重新打开录像。确认未来状态清空，目标按 UUID 重新关联。
4. 从非零时间开始导出 30 / 60 FPS，跨越佩戴及 UI 变化。增加 2× 时间关键帧：一秒源动作输出为半秒，Halo 仍推进一秒；慢速实际渲染不能改变动画速度。取消后重新导出，确认无权杖屏幕等待或残留。
5. 分别测试单人、Halo 专用服双客户端、无 Halo 服务端的本地模式；检查 worlddata／外部来源切换与回退、空佩戴快照、录制时已处于启动／关闭中间态、健康实体卸载返回、换维度和重连。回放中权杖不能发送选择／移除／关闭等真实操作。
6. 检查 ReplayMod 的录制暂停／恢复、marker 裁剪／拆分，新片段起点应保留正确佩戴、相位和 UI，且不残留原文件时间偏移。
7. 多视角、Iris/LabPBR、EMF/YSM 等分别记录实际版本、配置和结果；没有测试的组合明确保留为未验证。

反馈请包含使用的测试 JAR 哈希、ReplayMod 版本、失败步骤、期望／实际结果、录像文件与 `latest.log`。发现问题先修复本平台，再提供新 JAR 重新交测。

## 限制与继续条件

- 本轮尚无维护者游戏验收结论；Full/Quick、marker 实际操作、视频导出和第三方组合均不能用自动检查结果代替。
- 录像保存定义标识和状态，不包含资源；旧录像未记录的初始状态、本地事件或 UI 关闭时间不能补录。
- seek 清除未来惯性并从目标锚点重建，不保存物理轨迹／检查点；不保证逐帧还原原游玩位置或不同 FPS 的物理轨迹完全一致。
- 未安装 Iris 时已有可选 Iris 类探测警告、离线测试账号的认证／Realms 错误及原版 sampler 提示需与回放后端错误区分；本轮不据此宣称光影兼容通过。
- **必须等维护者明确确认 1.20.1 Fabric 测试通过，才开始 26.1.2 Fabric。**等待期间不修改或构建后续平台，不自行提交、推送、打标签或发布。
