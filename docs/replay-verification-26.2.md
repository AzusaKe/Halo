# Minecraft 26.2 Fabric 官方回放兼容交测

日期：2026-10-30（Asia/Hong_Kong）。状态：维护者于 2026-11-07 明确确认本平台验收通过，授权继续最后的 26.3 Fabric。

## 范围与来源

维护者已确认 1.21.1、1.20.1 和 26.1.2 Fabric 测试通过，本轮只开始 26.2 Fabric。没有修改或构建 26.3，没有整分支合并。

- Halo 分支 `codex/replay-compat-26.2-fabric`，基线 `26.2-fabric@e695e99d041b61ab3a4466ab2df67058c4b0c815`，改动未提交。
- core 固定 **2.6.0 / 03f84db58dc677f405796b422a8897fcf556bda1**；本平台工作树由旧 2.5.1 指针升级，core 源码不改，也不跟随移动分支。
- 版本 **2.6.0+adapter.1.dev**，包含已验收的默认隐藏／可手动关闭 UI 策略。
- 未提交阶段，成品 `halo-build.json` 的 `coreLock` 如实记录基线 HEAD 的旧 gitlink `59e3bb2e3139b1c0982c0d992525bc3032b4c69b`；实际编译和成品 `coreCommit` 为固定 `03f84db...`，`development=true`，不伪装正式发布来源。
- 构建和启动：Minecraft **26.2**、Java **25.0.4+7**、Fabric Loader **0.19.3**、Fabric API **0.157.0+26.2**、wrapper **9.5.1**、Loom **1.17.19**。core 保持 Java 17 字节码并用 Java 17 独立构建。

固定两份官方文件，每轮只装一个录制器：

| 后端／文件 | 官方来源／文件 ID | MC／加载器 | 内嵌依赖 |
| --- | --- | --- | --- |
| `replaymod-26.2-2.6.28.jar` | [ReplayMod 官网](https://www.replaymod.com/download/)；[Modrinth eHbYuA7P](https://api.modrinth.com/v2/version/eHbYuA7P)，文件 `s70ceaPj` | 26.2 / Fabric | Loader >=0.18.5；networking、key-mapping、resource-loader 模块；JAR 本身未单列 MC 范围，以官方逐文件元数据核实 |
| `Flashback-0.43.6-for-MC26.2.jar` | [Modrinth Um7qd3Na](https://api.modrinth.com/v2/version/Um7qd3Na)，文件 `ocN2v4Lv` | 26.2 / Fabric | Loader >=0.15.10、MC >26.1 <26.3、Java >=21、Fabric API >=0.107.0；与 Iris <1.10.9、voicechat_api <2.6.24 冲突 |

本 Halo 包及 MC26.2 要求 Java 25，Flashback 自身的最低 Java 声明不降低此要求。完整内嵌依赖／冲突、官方来源、文件 ID 和 SHA-512 校验记录在 `F:/Halo/.local/replay-26.2/fixtures/manifest.json`，本轮不自动换版。

- ReplayMod SHA-256：`ec260e2452b7413b5b04f5fac20eee20b3c8101b3b40af5704abe6a04ee6506a`。
- Flashback SHA-256：`37c34da5c0a09801d0e99e62d1c7233e00dbecd7f4827d3cb004c2ffbff755c7`。

## 实现与 UI

复用已验收的 core API v2 宿主、快照／事件、时间线、动画及物理，保持 `halo:replay` schema 1 和纳秒时间单位。连续播放跨越已被事件水位覆盖的周期快照不重置惯性；seek 恢复目标相位并清除未来运动状态。普通回放和导出均由录像源时间推进，暂停、预热、等待与同帧多视角不重复推进。

26.2 的屏幕入口使用 `Minecraft.gui.screen()`／`setScreen()`；断开连接的宿主清理保留在客户端线程。第三方反射、网络 payload 和可选 Mixin 留在适配层；两个兼容 Mixin 配置整体限定 client。只在既有渲染入口前准备回放时钟，保留本分支的 FrameSubmission、帧提取／提交、材质及第三方锚点实现，不用旧版本绘制代码覆盖。

`config/halo-azusake/halo_mod_config.json` 的 `replayScepterUiEnabled` 默认 `false`。缺少字段补齐；只有 JSON 布尔值 `true` 启用，其他类型修复为 `false`，未知字段保留。修改后重启客户端生效。

默认隐藏回放权杖 UI，仍记录、恢复和消费 UI 轨道，普通游玩不受影响。显式启用后可在播放和暂停时 ESC／按钮关闭，当前录制会话不立即重开，恢复播放也不重开。下一次录制的打开事件可显示新会话；seek 的新 epoch 按目标位置恢复，同 epoch 快照补齐不撤销手动关闭。导出准备／导出始终隐藏，不覆盖录制器进度／错误屏幕。UI 关闭只修改本地显示抑制，不写回服务器或录像。

## 测试包与自动检查

- 测试 JAR：`F:/Halo/.local/replay-26.2/artifacts/halo-26.2-fabric-2.6.0+adapter.1.dev.jar`。
- SHA-256：`762161c280094a28f153070253da55839f52fae6900b32b5b71729ce6539cac7`。
- Halo 联合构建 **155 项全部通过，0 失败／错误／跳过**；两个完整官方 JAR ABI 均实际运行，包含反射状态、暂停判断和导出注入边界；UI 7 项、4 项新增配置回归及原始 payload 字节检查均通过。
- core Java 17 独立构建 **364 项全部通过**，本工作树实际执行；联合构建另通过边界、独立消费者、旧 API v2 调用方、合包及来源检查。
- 最终同一 JAR 在无录制器、仅 ReplayMod、仅 Flashback 三种隔离客户端均完成初始化和资源加载；两个后端必需 hook 验证通过。无录制器／ReplayMod 自动补齐 UI 配置为 `false`，Flashback 的显式 `true` 和未知 `futureOption:42` 均保留。
- 开发专用服到达 `Done`，`stop` 保存全部维度并正常退出；加载日志无客户端回放兼容类，日志副本已保存。

完整构建来源、文件哈希、依赖与启动证据见 `F:/Halo/.local/replay-26.2/artifacts/manifest.json`。只安装一个 Halo JAR，不另装 core，不保留旧版 Halo。

自动检查包括：core 独立构建与边界／旧调用方、Halo 联合构建、协议和真实 payload 原始字节、codec、UI、配置、两个真实官方 JAR 的字段／方法／注入边界 ABI、平铺合包和来源。成品分别启动无录制器、ReplayMod、Flashback 环境并核对配置与必需 hook；开发专用服检查客户端兼容类隔离。

日志在 `F:/Halo/.local/replay-26.2/`。构建、ABI 和启动通过不代替实际录像、画面及导出验收。

## 维护者游戏步骤

先复制独立 26.2 实例，使用固定资源包、定义、模型及配置，替换旧 Halo。**同一个 Halo JAR 分两轮测试**：先装 ReplayMod 26.2-2.6.28，再卸下它改装 Flashback 0.43.6。每轮都重新启动客户端，不能同时装两个录制器。

1. 保持 UI 配置默认关闭：先佩戴明显带动画的光环，开始新录制；记录起点已有佩戴、摘除／再佩戴、启动／关闭中间态、转头移动，以及权杖 ESC 和按钮关闭。普通游戏 UI 应正常；回放经过 UI 区间不弹权杖屏幕，录制器按钮和快捷键可用。
2. 暂停、恢复、0.5×／1×／2×，前后 seek 至佩戴／摘除及动画中间态，连续经过多个周期快照，再重复打开录像。检查动画按源时间推进、暂停冻结、连续播放无周期 snap、seek 恢复相位并清除未来惯性。ReplayMod 分别测试 Full／Quick 及互切；Flashback 测试分段和时间轴跳转。
3. 退出客户端，把 UI 配置改为 `true` 后重启：录制的打开区间显示只读 UI。分别在播放／暂停时 ESC／按钮关闭，确认不立即重开、恢复播放也不重开。下一次录制的打开／关闭事件按时显示／关闭；向前／向后 seek 后按目标状态重新显示／隐藏。录制器控件和快捷键仍正常。
4. 从非零起点导出 30／60 FPS，分别覆盖默认 UI 关闭和显式开启、2× 时间关键帧。检查一秒源动作始终推进一秒 Halo 表现，慢机器渲染不改变速度；权杖不阻塞导出。取消、重新导出和返回回放均能操作，没有 C2S 写回或递归录制。同一源帧多视角不得多推进。
5. 补测本地模式、Halo 专用服双客户端、资源来源切换／回退、空佩戴快照、健康实体卸载返回、换维度／重连。检查录制暂停／恢复后仍可恢复正确状态；普通游戏的权杖与指令没有被回放只读策略误伤。
6. ReplayMod marker 裁剪／拆分：新片段起点应保留佩戴、动画相位和 UI 状态，不能残留原文件时间偏移；Full／Quick 都需检查。Flashback 用新录制验证周期 snapshot 与事件边界。
7. 多视角、Iris/LabPBR、EMF 及其他模型组合逐项记录真实版本、光影和结果；未测项为未验证，不从旧平台推定通过。当前平台原有模型支持范围不因统一 API 扩大。

反馈请提供 JAR SHA-256、录制器版本、配置值、失败步骤、录像和 `latest.log`。有问题先修复当前平台并重新交测。

## 已知限制与继续条件

录像不内嵌资源；旧录像缺失的初始状态、事件与 UI 关闭时刻不能补录。seek 从目标锚点重建，不保存原游玩物理轨迹，不保证跨 FPS 逐帧一致。Flashback 若配置 ignoredCustomPayloads 排除 `halo:replay`，将无法完整恢复并给出警告。

Full／Quick、marker 裁剪／拆分、实际视频导出、多视角、Iris/LabPBR 和模型组合仍需维护者游戏测试；没有逐项反馈的组合不推定通过。本轮不扩大原平台的模型支持声明。

启动检查使用隔离离线账号，认证／Realms 错误、Windows OSHI/Perflib 性能计数器探测失败、未安装 Iris 的可选探测及录制器自身提示应与 Halo 兼容失败区分，不构成第三方组合已验收。

未提交、推送、打标签或发布。**必须等待维护者明确确认本次 26.2 Fabric 两个后端通过，才开始 26.3 Fabric。**构建成功、沉默和部分反馈不能替代此确认。
