# Minecraft 26.1.2 Fabric 官方回放兼容交测

日期：2026-10-30（Asia/Hong_Kong）。状态：维护者明确确认本平台测试通过，授权继续 26.2 Fabric。

## 范围与锁定来源

维护者已确认 1.21.1 UI 修复及 1.20.1 Fabric 测试通过，本轮只开始队列中的 26.1.2 Fabric。没有修改、构建 26.2 或 26.3，也没有整分支合并基准。

- Halo：`codex/replay-compat-26.1.2-fabric`，从维护分支 `26.1-fabric` / `f391192f4871483037e158ab3f1eb621f7270222` 创建独立工作树；完整基线 SHA 与改动文件哈希见交付清单。
- HaloCore：固定 **2.6.0 / 03f84db58dc677f405796b422a8897fcf556bda1**，源码保持干净，Java 17；将本平台工作树从原 2.5.1 指针升级到此确定提交，没有追踪 core 分支最新版本。
- 版本：**2.6.0+adapter.1.dev**。core 功能升级后，适配修订重置为 1；包含已验收的 UI 修复。
- 本轮未提交，故 `halo-build.json` 的 `coreLock` 仍如实记录基线 HEAD 的旧 gitlink `59e3bb2e3139b1c0982c0d992525bc3032b4c69b`；实际编译和成品 `coreCommit` 均为固定 `03f84db...`，`development=true`。这不是正式发布包；提交状态不会伪装为已提交的新锁定。
- 平台：Minecraft **26.1.2**，Java **25.0.4**，Fabric Loader **0.19.3**，Fabric API **0.155.3+26.1.2**，wrapper **9.5.1**，Loom **1.17.19**。保留此分支原有提取／绘制架构、材质和第三方锚点适配。

固定官方后端，两个录制器分开安装：

| 后端 | 来源／文件 ID | 官方游戏版本／加载器 | 内嵌依赖范围 |
| --- | --- | --- | --- |
| ReplayMod `26.1-2.6.28` | [官网](https://www.replaymod.com/download/download_new.php?version=26.1-2.6.28)，官方 minio；[Modrinth 5LxshONI](https://api.modrinth.com/v2/version/5LxshONI) | 26.1 / 26.1.1 / 26.1.2，Fabric | Loader >=0.18.5；networking、key-mapping、resource-loader 模块；JAR 本身未单列 MC 范围，以逐文件官方元数据核实 |
| Flashback `0.43.3` for MC26.1 | [Modrinth 2obIELyD](https://api.modrinth.com/v2/version/2obIELyD)，文件 ID `pyKxag8s` | 26.1 / 26.1.1 / 26.1.2，Fabric | Loader >=0.19.0，MC ~26.1，Java >=21，Fabric API >=0.107.0；与 Iris <1.10.9 冲突 |

MC 26.1.2 及本 Halo 包要求 Java 25，不能因 Flashback 的最低 Java 声明改用 Java 21。完整内嵌依赖、文件 SHA-256 和下载来源保存在 `F:/Halo/.local/replay-26.1.2/fixtures/manifest.json`；本轮不自动换用新文件，也不扩大 Flashback 0.43.6 的 26.1 声明。

## 实现与行为

继续使用 core API v2 的宿主、快照、事件、排序及状态恢复，`halo:replay` schema 1 不变。周期快照不重置已覆盖的连续惯性；seek 重建目标状态并清除未来运动。普通回放和导出都使用录像源时间；暂停、预热、等待及同帧多视角不重复推进。

共享 Halo 层只负责 UI、codec、宿主接线和写回隔离；Mojang 类名、FriendlyByteBuf／StreamCodec、第三方反射和可选 Mixin 均留在适配层。两个 Mixin 配置整体限制客户端环境，专用服务端不加载探测插件或回放兼容类。成品不打包两个录制器的类。

`config/halo-azusake/halo_mod_config.json` 中 `replayScepterUiEnabled` 默认 `false`。缺少字段自动补齐；只有 JSON 布尔值 `true` 开启，非法类型修复为 `false`，未知字段保留。修改后重启客户端生效。

默认隐藏回放权杖 UI，仍完整记录／消费 UI 轨道；普通游玩 UI 正常。显式开启后可在播放或暂停时 ESC／按钮关闭，当前录制会话不会立即重开，恢复播放也不重开。下一次录制的打开事件或新 epoch 的 seek 按录像状态重新判定；同 epoch 快照补齐不会撤销手动关闭。导出准备／导出始终隐藏，不覆盖录制器进度或错误屏幕。手动关闭不发送 C2S 或修改录像数据。

## 测试包与自动检查

- 测试 JAR：`F:/Halo/.local/replay-26.1.2/artifacts/halo-26.1.2-fabric-2.6.0+adapter.1.dev.jar`
- SHA-256：`9610c37fdb1bc928138cb7281ce9d2d65658139354628f94189b8deea6006619`
- ReplayMod SHA-256：`70e074ea1325e01737dccc33d8bb7637cfdc35e377ecb48d0940259db1be607c`
- Flashback SHA-256：`df06c9f20e7c5bb234753810decf90c8e747fd9dc60dc527729e6c46996e7b13`
- Halo 联合构建 **150 项全部通过，0 失败／错误／跳过**，包含两份真实官方 JAR ABI、7 项 UI 策略、4 项新增配置回归和网络原始字节布局检查。
- core Java 17 独立构建：**364 项全部通过**，本工作树实际执行，无跳过；联合构建另通过边界、独立回放消费者和旧调用方检查。
- 最终同一 JAR 在无录制器、ReplayMod、Flashback 三种隔离客户端均完成初始化和资源加载；两个后端必需 hook 验证通过。默认配置 `false` 与显式 `true`（同时保留未知字段）均检查。
- 开发专用服到达 `Done`，`stop` 保存退出；加载日志无客户端回放兼容类。相关日志副本保存在本轮交付目录。

构建来源、工作树文件哈希、依赖和成品启动证据见 `F:/Halo/.local/replay-26.1.2/artifacts/manifest.json`。开发包只安装一份 Halo，不另装 core。

自动检查项目：core Java 17 独立构建、364 项回归及边界／旧调用方；Halo Java 25 联合构建、协议／payload 原始字节、codec、UI 策略、配置迁移、旧 API v2 调用方、平铺合包和来源。ABI 用例使用两份完整官方 JAR，覆盖必需字段、反射方法和实际导出注入边界，不将跳过当作通过。成品分别启动无录制器、仅 ReplayMod、仅 Flashback，并检查兼容 hook、配置落盘与日志；开发专用服到达 Done 后 stop 保存退出并检查类加载隔离。

构建与启动日志：`F:/Halo/.local/replay-26.1.2/`。自动检查不替代下列真实回放与画面验收。

## 维护者游戏步骤

先复制独立 26.1.2 实例，使用固定资源包、定义、模型及配置，替换旧 Halo。**同一个 Halo JAR 分两轮测试**：先装 ReplayMod 26.1-2.6.28，再卸下它改装 Flashback 0.43.3。每轮都重新启动客户端，不能同时装两个录制器。

1. 保持 UI 配置默认关闭：先佩戴明显带动画的光环，开始新录制；记录起点已有佩戴、摘除／再佩戴、启动／关闭中间态、转头移动，以及权杖 ESC 和按钮关闭。普通游戏 UI 应正常；回放经过 UI 区间不弹权杖屏幕，录制器按钮和快捷键可用。
2. 暂停、恢复、0.5×／1×／2×，前后 seek 至佩戴／摘除及动画中间态，连续经过多个周期快照，再重复打开录像。检查动画按源时间推进、暂停冻结、连续播放无周期 snap、seek 恢复相位并清除未来惯性。ReplayMod 分别测试 Full／Quick 及互切；Flashback 测试分段和时间轴跳转。
3. 退出客户端，把 UI 配置改为 `true` 后重启：录制的打开区间显示只读 UI。分别在播放／暂停时 ESC／按钮关闭，确认不立即重开、恢复播放也不重开。下一次录制的打开／关闭事件按时显示／关闭；向前／向后 seek 后按目标状态重新显示／隐藏。录制器控件和快捷键仍正常。
4. 从非零起点导出 30／60 FPS，分别覆盖默认 UI 关闭和显式开启、2× 时间关键帧。检查一秒源动作始终推进一秒 Halo 表现，慢机器渲染不改变速度；权杖不阻塞导出。取消、重新导出和返回回放均能操作，没有 C2S 写回或递归录制。同一源帧多视角不得多推进。
5. 补测本地模式、Halo 专用服双客户端、资源来源切换／回退、空佩戴快照、健康实体卸载返回、换维度／重连。检查录制暂停／恢复后仍可恢复正确状态；普通游戏的权杖与指令没有被回放只读策略误伤。
6. ReplayMod marker 裁剪／拆分：新片段起点应保留佩戴、动画相位和 UI 状态，不能残留原文件时间偏移；Full／Quick 都需检查。Flashback 用新录制验证周期 snapshot 与事件边界。
7. 多视角、Iris/LabPBR、EMF 及其他模型组合逐项记录真实版本、光影和结果；未测项为未验证，不从旧平台推定通过。当前平台原有模型支持范围不因统一 API 扩大。

反馈请提供 JAR SHA-256、录制器版本、配置值、失败步骤、录像和 `latest.log`。有问题先修复当前平台并重新交测。

## 已知限制与串行门槛

录像不内嵌资源；旧录像未记录的初始状态、事件和 UI 关闭时间不能补录。seek 从目标锚点重建，不保存原物理轨迹或检查点，不保证原游玩物理路径或跨 FPS 逐帧一致。Flashback 若配置 ignoredCustomPayloads 排除 `halo:replay`，无法完整恢复，会给出警告。

本轮启动检查使用离线测试账号，认证／Realms 错误、未安装 Iris 的可选类探测及 ReplayMod 自身 accessor 提示需与 Halo 兼容异常区分，不构成第三方组合验收。Full／Quick、marker、视频导出及视觉表现仍须游戏测试。

没有提交、推送、打标签或发布。**必须等待维护者明确确认 26.1.2 Fabric 的两个后端测试通过，才开始 26.2 Fabric。**构建通过、沉默或部分反馈不能替代这一确认。
