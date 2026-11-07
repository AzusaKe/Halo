# Minecraft 26.3 Fabric 官方回放兼容交测

最新状态（2026-11-07）：维护者已于 2026-11-07 明确确认本开发包测试通过，随后授权提交和 GitHub 发布。原始交付说明和未验证组合保持为历史证据；正式版本 `2.6.0+adapter.1`、目标标签 `v2.6.0-fabric-26.3-adapter.1` 及提交来源规则见 [发布登记](replay-release-2.6.0.md)。


日期：2026-11-07（Asia/Hong_Kong）。状态：维护者于 2026-11-07 明确回复“测试通过”，本平台验收完成；未逐项报告的环境组合继续为未验证。

验收对应 JAR SHA-256：`f2e68c81a87adbd77b3cfa319b0de3c5c7058bff26a9faabdb881a4bda07cc6e`；独立回执为 `F:/Halo/.local/replay-26.3/artifacts/acceptance.json`。交付时的 `manifest.json`、`TESTING.md` 与 JAR 原样保留，其待验收文字表示交付时状态。

## 范围与锁定来源

维护者已明确确认 1.21.1、1.20.1、26.1.2 和 26.2 Fabric 验收通过。本轮只适配既定队列最后的 26.3 Fabric，不因上游新文件自动扩展范围或替换依赖。

- Halo：`codex/replay-compat-26.3-fabric`，基线 `26.3-fabric@e3b2c4e68ef730f539cd8e1675e2cff8d7cc73c3`，改动未提交。
- core：固定 **2.6.0 / 03f84db58dc677f405796b422a8897fcf556bda1**；本平台从原 2.5.1 指针升级，core 源码不改，不跟踪移动分支。
- 版本：**2.6.0+adapter.1.dev**，包含已验收的回放 UI 策略；core 升级后适配修订重置为 1。
- 当前未提交，成品 `halo-build.json` 的 `coreLock` 如实保留基线 HEAD 的旧 gitlink `59e3bb2e3139b1c0982c0d992525bc3032b4c69b`；实际编译／成品 `coreCommit` 为固定 `03f84db...`，`development=true`，不伪装正式发布来源。
- 构建和启动固定 Minecraft **26.3**、Java **25.0.4+7**、Fabric Loader **0.19.5**、Fabric API **0.160.6+26.3**、wrapper **9.6.0**、Loom **1.17.21**。core 保持 Java 17 字节码，并执行 Java 17 独立构建。

两个录制器分开安装，交付同一个 Halo JAR：

| 后端／文件 | 官方来源／文件 ID | MC／加载器 | 内嵌依赖和冲突范围 |
| --- | --- | --- | --- |
| `replaymod-26.3-2.6.28.jar` | [官网下载页](https://www.replaymod.com/download/)；[Modrinth 7c5mnPyr](https://api.modrinth.com/v2/version/7c5mnPyr)，文件 `cyGGUSBc` | 26.3 / Fabric | Loader >=0.18.5；networking、key-mapping、resource-loader 模块；JAR 本身未单列 MC 范围，以官方逐文件元数据核实 |
| `Flashback-0.43.6-for-MC26.3.jar` | [Modrinth cV1pa0YV](https://api.modrinth.com/v2/version/cV1pa0YV)，文件 `IDZkfqKJ` | 26.3 / Fabric | Loader >=0.15.10、MC >26.2 <26.4、Java >=21、Fabric API >=0.107.0；与 Iris <1.10.9、voicechat_api <2.6.24 冲突 |

MC26.3 及本 Halo 包要求 Java 25，不能因录制器自己的最低 Java 声明降低要求。本轮固定来源、逐文件游戏范围、依赖、SHA-256 和官方 SHA-512 校验保存在 `F:/Halo/.local/replay-26.3/fixtures/manifest.json`。

- ReplayMod SHA-256：`13af4ea72fb63c07859f00b716307dc74328e884fe95220580451dad4c52d9c6`。
- Flashback SHA-256：`2d2ff6b397831472f63dedf6b2053c384bf9233540811b4895a803719ae4370b`。

## 实现与 UI

复用 core API v2 的统一宿主、快照／事件、时间线、动画和物理；保持 `halo:replay` schema 1、纳秒单位及生命周期。连续播放跨过已覆盖的周期快照不重置惯性；seek 恢复目标相位并清除未来运动。普通回放和导出按录像源时间推进，暂停、预热、等待和同源帧多视角不重复推进。

共享 Halo 层只负责宿主、codec、UI 和写回隔离。第三方反射、时间转换、FriendlyByteBuf／StreamCodec 和可选 Mixin 留在各适配包，两个配置整体限定 client，专用服务端不加载客户端探测插件／兼容类。只在既有世界帧入口前准备回放时钟，保留 26.3 的绘制准备／提交分离、FrameIndexSlots、材质及锚点实现；保留本版鼠标按钮常量和客户端线程断开清理。

`config/halo-azusake/halo_mod_config.json` 的 `replayScepterUiEnabled` 默认 `false`，缺少字段自动补齐，只有 JSON 布尔值 `true` 启用，非法类型修复为 `false`，未知字段保留；修改后重启客户端生效。

默认隐藏回放权杖 UI，仍记录、恢复和推进 UI 轨道；普通游玩 UI 正常。显式启用后可在播放／暂停时 ESC 或按钮关闭，当前录制会话不立即重开，恢复播放也不重开。下一次录制的打开事件可显示新会话，seek 的新 epoch 按目标位置恢复；同 epoch 快照补齐不撤销手动关闭。导出准备／导出始终隐藏，不覆盖录制器进度或错误屏幕。屏幕关闭只影响本地显示，不修改录像或发送 C2S。

## 测试包与自动检查

- 测试 JAR：`F:/Halo/.local/replay-26.3/artifacts/halo-26.3-fabric-2.6.0+adapter.1.dev.jar`（862005 字节）。
- SHA-256：`f2e68c81a87adbd77b3cfa319b0de3c5c7058bff26a9faabdb881a4bda07cc6e`。
- 完整来源、改动文件哈希、依赖和检查证据：`F:/Halo/.local/replay-26.3/artifacts/manifest.json`；同目录 `TESTING.md` 为本说明副本。
- core Java 17 独立 `build`：22 项任务实际执行，364 项测试全部通过；边界及外部 replay／preview／旧调用方检查通过。
- Halo Java 25 `build`：159 项测试全部通过，无失败、错误或跳过。首次完整构建实际执行全部测试，包括两个真实官方 JAR 的 ABI 用例（2/2）；最终构建复用未变输入的 Halo 测试结果，并重新执行 core 测试、合包和旧 API 校验。
- 最终包的协议／codec／UI／配置、平铺合包、来源和旧 API 检查通过；core 字节码为 Java 17，平台字节码为 Java 25，不捆绑录制器类。
- 同一成品完成无录制器、ReplayMod、Flashback 隔离客户端初始化和资源加载（12 定义、1 锚点）。ReplayMod 6 个、Flashback 5 个目标类均含 Halo 注入。默认配置两组为 `false`；Flashback 组显式 `true` 和未知字段 `futureOption:42` 均保留。
- 专用开发服监听 `127.0.0.1:25583`，达到 `Done`，`stop` 保存三个维度后正常退出；类加载记录不含客户端回放层、两个兼容包或第三方录制器类。

只安装一份 Halo，不额外安装 core，不保留旧 Halo JAR。

检查范围：core Java 17 独立构建及边界／旧调用方；Halo Java 25 联合构建、协议／真实 payload 字节、codec、UI／配置、两个真实官方 JAR 的反射字段／方法和导出注入边界 ABI、平铺合包与来源。最终成品分别启动无录制器、ReplayMod、Flashback 环境，检查配置落盘、必需 hook 与诊断；开发专用服达到 Done、stop 保存并检查类加载隔离。

构建与启动日志保存在 `F:/Halo/.local/replay-26.3/`；自动检查不能替代下列实际回放、导出和画面验收。

## 维护者游戏步骤

先复制独立 26.3 实例，使用固定资源包、定义、模型及配置，替换旧 Halo。**同一个 Halo JAR 分两轮测试**：先装 ReplayMod 26.3-2.6.28，再卸下它改装 Flashback 0.43.6。每轮都重新启动客户端，不能同时装两个录制器。

1. 保持 UI 配置默认关闭：先佩戴明显带动画的光环，开始新录制；记录起点已有佩戴、摘除／再佩戴、启动／关闭中间态、转头移动，以及权杖 ESC 和按钮关闭。普通游戏 UI 应正常；回放经过 UI 区间不弹权杖屏幕，录制器按钮和快捷键可用。
2. 暂停、恢复、0.5×／1×／2×，前后 seek 至佩戴／摘除及动画中间态，连续经过多个周期快照，再重复打开录像。检查动画按源时间推进、暂停冻结、连续播放无周期 snap、seek 恢复相位并清除未来惯性。ReplayMod 分别测试 Full／Quick 及互切；Flashback 测试分段和时间轴跳转。
3. 退出客户端，把 UI 配置改为 `true` 后重启：录制的打开区间显示只读 UI。分别在播放／暂停时 ESC／按钮关闭，确认不立即重开、恢复播放也不重开。下一次录制的打开／关闭事件按时显示／关闭；向前／向后 seek 后按目标状态重新显示／隐藏。录制器控件和快捷键仍正常。
4. 从非零起点导出 30／60 FPS，分别覆盖默认 UI 关闭和显式开启、2× 时间关键帧。检查一秒源动作始终推进一秒 Halo 表现，慢机器渲染不改变速度；权杖不阻塞导出。取消、重新导出和返回回放均能操作，没有 C2S 写回或递归录制。同一源帧多视角不得多推进。
5. 补测本地模式、Halo 专用服双客户端、资源来源切换／回退、空佩戴快照、健康实体卸载返回、换维度／重连。检查录制暂停／恢复后仍可恢复正确状态；普通游戏的权杖与指令没有被回放只读策略误伤。
6. ReplayMod marker 裁剪／拆分：新片段起点应保留佩戴、动画相位和 UI 状态，不能残留原文件时间偏移；Full／Quick 都需检查。Flashback 用新录制验证周期 snapshot 与事件边界。
7. 多视角、Iris/LabPBR、EMF 及其他模型组合逐项记录真实版本、光影和结果；未测项为未验证，不从旧平台推定通过。当前平台原有模型支持范围不因统一 API 扩大。

反馈请提供 JAR SHA-256、录制器版本、配置值、失败步骤、录像和 `latest.log`。有问题先修复当前平台并重新交测。

## 已知限制与最终验收

录像不内嵌资源；旧录像缺失的初始状态、事件及 UI 关闭时刻不能补录。seek 从目标锚点重建，不保存原游玩物理轨迹／检查点，不保证跨 FPS 逐帧一致。Flashback 若配置 ignoredCustomPayloads 排除 `halo:replay`，完整恢复将不可用并给出警告。

Full／Quick、marker 实际操作、视频导出、多视角、Iris/LabPBR 和模型组合仍需本平台独立游戏测试，不能沿用旧平台结论。本轮不扩大原平台的模型支持范围；未测项继续标为未验证。

本地隔离启动使用离线账号，认证／Realms、未安装 Iris 的可选探测及录制器 refmap 提示须与 Halo 兼容错误区分。

首轮三个客户端同时启动时，ReplayMod 进程在资源加载期间发生 Windows 原生崩溃：`jvm.dll / 0xc0000005`，PID 57744，事件报告 `6b0df2cd-3628-4207-9bb7-e39f1682e13a`；没有 Java 层 Halo hook／Mixin 异常。关闭另外两个测试客户端后，用相同 Halo、录制器和依赖单独复跑，正常完成初始化、资源加载与必需注入检查。原因未定位，不能把复跑成功记作原生崩溃已修复；首轮日志与过滤后的 Windows 事件保存在 `F:/Halo/.local/replay-26.3/replaymod-first-attempt/`。若实际游戏再次出现同类退出，需附带 JVM 崩溃／Windows 事件和运行环境继续定位。

未提交、推送、打标签或发布。**26.3 是本轮最后一个平台，维护者已明确回复“测试通过”；至此固定官方交集的 5 个平台全部完成验收。**本次确认不扩大未逐项报告的组合，也不将首次启动的原生崩溃记作已定位／修复，不自动触发提交、推送或发布。
