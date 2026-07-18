# Halo Mod 跨版本兼容性测试方案

> 兼容范围：**1.19.4 ~ 1.20.4**（Fabric，已确认）
> - 下界 **1.19.4**（已确认）：1.19.3 缺少 `ClientPlayNetworkHandler.getConnection()` 方法
> - 上界 **1.20.4**（已确认）：1.20.5 起 Fabric API 移除 `ServerPlayNetworking$PlayChannelHandler`，启动即崩
> - **已实测通过**：1.19.4、1.20.1（构建目标）、1.20.2、1.20.4
> - **已实测通过**：1.20.1（构建目标）、1.20.2、**1.20.4**（全部 13 项通过）
> - 1.20 系列共 6 个版本：1.20 / 1.20.1 / 1.20.2 / 1.20.3 / 1.20.4 / 1.20.5 / 1.20.6
> - 1.20.5 和 1.20.6 确认不可用（Fabric API 网络层不兼容）
>
> 跨加载器：**1.20.1 NeoForge / Forge** 通过 **Sinytra Connector（信雅互联）** 已实测通过
> - 单人/多人均正常，光影兼容

---

## 测试矩阵

| 优先级 | 版本 | 测试理由 |
|--------|------|----------|
| P0 | **1.20.1** | 当前构建目标，基准对照 |
| P0 | **1.20.6** | 1.20 最终版，验证上界 |
| P0 | **1.20.5** | **大版本更新** — Data Components、网络包重构、渲染 API 变更，最可能断点 |
| P1 | **1.20.3** | Shader 重构可能在此版本引入 |
| P1 | **1.20.4** | 与 1.20.3 类似风险 |
| P1 | **1.20.2** | 用户已实测通过，抽样复验即可 |
| P1 | **1.19.4** | 1.19 系列最终版，验证下界附近 |
| P2 | **1.20** | 1.20.1 的前一个版本，变更很小 |
| P2 | **1.19.2** | 1.19.1 的修复版 |
| P2 | **1.19.1** | 理论下界 |

### 跨加载器测试（Sinytra Connector / 信雅互联）

| 优先级 | 平台 | 版本 | 测试理由 |
|--------|------|------|----------|
| P1 | **NeoForge** | 1.20.1 | 已实测通过，抽样复验 + 光影兼容性 |
| P1 | **Forge** | 1.20.1 | 已实测通过，抽样复验 |
| P2 | **NeoForge** | 1.20.2~1.20.6 | 如 Fabric 版本全部通过，可尝试扩展 |

Sinytra Connector 依赖：
- **Sinytra Connector**（Fabric→Forge/NeoForge 翻译层）
- **Forgified Fabric API**（Fabric API 的 Forge 移植版）
- 安装顺序：先装 Sinytra Connector + Forgified Fabric API，再放 Halo Mod

每个版本需要安装对应的 **Fabric Loader (>=0.15.0)** 和 **Fabric API**（Fabric 平台），或 **Sinytra Connector + Forgified Fabric API**（NeoForge/Forge 平台）。

---

## 各版本通用前置准备

1. 下载对应版本的 Fabric Loader（>=0.15.0）和 Fabric API
2. 将 halo mod jar 放入 `mods/` 文件夹
3. 将光环定义资源包放入 `resourcepacks/`（确保有至少一个可用的光环定义 JSON）
4. 准备两个测试场景：**单人世界** 和 **多人服务器**（服务器也需安装 halo mod）

---

## 功能测试项

### T1 — 启动与加载（所有版本必测）

**目标**：验证模组能在目标版本上正常加载，不崩溃

**步骤**：
1. 启动 Minecraft，进入主界面
2. 点击「模组」按钮，确认 Halo Mod 显示在模组列表中
3. 进入一个单人世界（创造模式）

**观察**：
- [ ] 游戏不崩溃
- [ ] 模组列表中 Halo Mod 版本号正确
- [ ] 进入世界时日志无 `Mixin apply failed` 错误

**风险 API**：Mixin 目标 `LivingEntity.writeCustomDataToNbt` / `readCustomDataFromNbt`、`Entity.refreshPositionAfterTeleport` / `requestTeleport`

---

### T2 — 客户端指令注册

**目标**：验证 `/halo` 客户端指令能在聊天框中被识别

**步骤**：
1. 进入单人世界（创造模式，开启作弊）
2. 在聊天框输入 `/halo ` 然后按 Tab 键
3. 确认出现自动补全列表（list, dump, show, hide, config, reload, active, inspect, save, debug）

**观察**：
- [ ] Tab 补全正常弹出
- [ ] 所有子命令都可选择
- [ ] 输入 `/halo list` 能返回结果（即使没有光环定义，应显示 "No halo definitions loaded"）

**风险 API**：
- `ClientCommandRegistrationCallback` — 1.19.1+ 可用
- `CommandRegistryAccess` 参数 — 1.19.3 可能有签名变化

---

### T3 — 服务端指令注册（多人测试）

**目标**：验证 `/halo` 服务端指令正常工作

**步骤**：
1. 启动一个安装了 Halo Mod 的 Fabric 服务器（对应版本）
2. 以 OP 身份进入服务器
3. 在聊天框输入 `/halo list`
4. 输入 `/halo active`

**观察**：
- [ ] 指令被识别，不显示 "Unknown command"
- [ ] `/halo list` 返回定义列表或 "No halo definitions loaded"
- [ ] `/halo active` 返回 "No active halos"

---

### T4 — 显示光环（核心功能，所有版本必测）

**目标**：验证光环能成功附着到实体并渲染

**步骤**：
1. 进入单人世界（创造模式，开启作弊）
2. 确保已安装光环定义资源包
3. 输入 `/halo list` 确认定义已加载
4. 找到一个生物（如牛、僵尸）或自己
5. 输入 `/halo show @e[type=cow,limit=1] halo:ring_default`（使用实际存在的定义 ID）
6. 观察该实体头顶是否出现光环

**观察**：
- [ ] `/halo show` 返回成功消息
- [ ] 光环出现在实体头部位置
- [ ] 光环随实体移动而跟随（物理阻尼效果）
- [ ] 光环在实体转头时跟随旋转
- [ ] 光环纹理/颜色正确（无粉紫缺失纹理方块）

**风险 API**（**1.20.3~1.20.6 重点**）：
- `GameRenderer::getPositionTexProgram` / `getPositionTexColorProgram` / `getPositionColorProgram` — 可能在 1.20.3~1.20.6 被替换为 `ShaderProgramKeys.*`
- `VertexFormats.POSITION_TEXTURE` / `POSITION_TEXTURE_COLOR` / `POSITION_COLOR` — 可能改名
- `Tessellator.getInstance().getBuffer().begin()` — 返回类型可能变更
- `RenderSystem.setShader(Supplier)` — 签名可能变更
- `RenderSystem.setShaderTexture(int, int)` — 可能在 1.20.3~1.20.6 移除
- JOML `Matrix4f` / `Quaternionf` — 1.19+ 稳定

---

### T5 — 隐藏光环

**目标**：验证光环能被移除

**步骤**：
1. 承接 T4，对同一个实体输入 `/halo hide @e[type=cow,limit=1]`
2. 确认光环消失

**观察**：
- [ ] `/halo hide` 返回成功消息
- [ ] 光环立即消失
- [ ] `/halo active` 不再列出该实体

---

### T6 — 光环持久化（NBT 存储）

**目标**：验证光环在世界保存/加载后能恢复

**步骤**：
1. 对一个实体使用 `/halo show` 附着光环
2. 输入 `/halo save` 保存数据
3. 退出世界
4. 重新进入同一世界
5. 找到同一实体，确认光环是否仍在

**观察**：
- [ ] `/halo save` 返回保存成功消息
- [ ] 重新进入世界后光环仍在
- [ ] `/halo inspect @e[type=cow,limit=1]` 显示 NBT 状态为 "✓ persisted"

**风险 API**：
- `PersistentStateManager.getOrCreate(Function, Supplier, String)` — 1.16+ 稳定，但 1.20.3+ 可能有签名微调
- `PersistentState.writeNbt(NbtCompound)` — 稳定

---

### T7 — 光环物理（阻尼与跟随）

**目标**：验证光环的物理阻尼效果正常

**步骤**：
1. 对自己附着光环
2. 快速移动（跑、跳、游泳），观察光环是否滞后跟随
3. 突然停下，观察光环是否平滑归位
4. 使用 `/halo config linear-damping 0.1` 降低线性阻尼
5. 重复移动测试，确认阻尼效果变化
6. 使用 `/halo config angular-damping 0.5` 调整角阻尼
7. 转头观察光环旋转跟随效果

**观察**：
- [ ] 光环有明显的物理滞后感
- [ ] 调整参数后效果变化明显
- [ ] 无异常抖动或跳变

---

### T8 — 多人同步

**目标**：验证多人游戏中光环状态能正确同步

**步骤**：
1. 启动 Fabric 服务器 + 安装 Halo Mod
2. 玩家 A 和玩家 B 分别进入服务器
3. 玩家 A（OP）对一个实体执行 `/halo show @e[type=cow,limit=1] halo:ring_default`
4. 玩家 B 观察该实体是否也显示光环

**观察**：
- [ ] 玩家 B 能看到光环
- [ ] 玩家 B 看到的光环位置/旋转与玩家 A 一致
- [ ] 新加入的玩家 C 也能看到已有光环（全量同步）
- [ ] 光环移除后，所有玩家都看不到

**风险 API**：
- `ServerPlayNetworking.send()` / `ClientPlayNetworking.registerGlobalReceiver()` — 稳定
- `CommandExecutionC2SPacket` 构造函数 — **1.20.3~1.20.6 需验证签名是否变更**

---

### T9 — 客户端到服务端指令转发（多人必测）

**目标**：验证客户端指令在多人模式下能正确转发到服务端

**步骤**：
1. 玩家 A（非 OP）进入多人服务器
2. 输入 `/halo list`（此命令在多人时应转发到服务端执行）
3. 确认返回了正确的结果

**观察**：
- [ ] 指令被正确转发，返回服务端的结果
- [ ] 不出现 "Unknown command" 或断开连接
- [ ] 不触发服务端踢出（签名校验失败等）

**风险 API**：
- `FabricHaloCommandInterceptor.sendCommandRaw()` — 直接构造 `CommandExecutionC2SPacket`
- `ArgumentSignatureDataMap.EMPTY` — 1.19.1 引入，1.20.3~1.20.6 可能有变更
- `LastSeenMessageList.Acknowledgment(int, BitSet)` — 同上

---

### T10 — 光环动画

**目标**：验证光环的视觉动画效果（脉冲、旋转等）

**步骤**：
1. 附着一个带动画的光环定义（如果有 pulse/oscillate 配置）
2. 观察光环是否播放脉冲/旋转/缩放动画
3. 等待 30 秒确认动画流畅

**观察**：
- [ ] 动画正常播放
- [ ] 帧率稳定，无卡顿

---

### T11 — 资源包热重载

**目标**：验证光环定义能通过资源包重载更新

**步骤**：
1. 进入世界，确认当前光环定义列表
2. 按 F3+T 重载资源包
3. 输入 `/halo list` 确认定义列表已更新

**观察**：
- [ ] 重载后不崩溃
- [ ] 新增/修改的定义立即生效

---

### T12 — 睡眠/游泳/潜行姿态

**目标**：验证不同实体姿态下光环行为正确

**步骤**：
1. 对玩家附着光环
2. 进入睡眠（右键床），观察光环是否隐藏（如果定义了 `hideOnSleep: true`）
3. 起床后确认光环恢复
4. 进入游泳状态，观察光环位置是否跟随
5. 潜行状态，观察光环位置是否跟随

**观察**：
- [ ] 睡眠时光环隐藏（如果配置了）
- [ ] 游泳时光环在正确位置
- [ ] 潜行时光环在正确位置

---

### T13 — 光环数量与性能

**目标**：验证同时存在多个光环时不崩溃、不严重掉帧

**步骤**：
1. 对 5~10 个不同实体分别执行 `/halo show`
2. 观察帧率变化
3. 输入 `/halo active` 确认所有光环都列出

**观察**：
- [ ] 帧率无严重下降（< 30% 掉帧可接受）
- [ ] 所有光环都正常渲染
- [ ] 无渲染伪影（z-fighting、闪烁等）

---

### T14 — Sinytra Connector 跨加载器加载（NeoForge / Forge）

**目标**：验证模组通过 Sinytra Connector 在 NeoForge/Forge 上能正常加载和运行

**步骤**：
1. 安装 NeoForge 或 Forge 1.20.1
2. 安装 **Sinytra Connector** + **Forgified Fabric API**
3. 放入 Halo Mod jar
4. 放入光环定义资源包
5. 启动游戏，进入单人世界（创造模式）

**观察**：
- [ ] 游戏不崩溃，启动日志无 Sinytra 翻译错误
- [ ] 模组列表中 Halo Mod 正常显示
- [ ] `/halo list` 指令可用
- [ ] `/halo show` 能正常附着光环并渲染
- [ ] 光环纹理/颜色/动画正常

**风险点**：
- Sinytra Connector 对 Fabric API 的翻译覆盖度——如果模组用了 Connector 未翻译的 Fabric API，会报 `NoSuchMethodError`
- Mixin 注入在 Forge/NeoForge 的 Mixin 环境下行为可能不同
- `@Environment(EnvType.CLIENT)` 注解在非 Fabric 加载器上的处理

---

### T15 — 光影兼容性（NeoForge/Forge + Shader）

**目标**：验证光环渲染与主流光影包兼容

**步骤**：
1. 承接 T14，在 NeoForge 1.20.1 上安装一个光影包（如 Iris/OptiFine Shaders 或 Complementary Shaders）
2. 启用光影
3. 对实体附着光环
4. 观察光环在光影模式下的渲染效果

**观察**：
- [ ] 光环在光影模式下可见（不被光影管线剔除）
- [ ] 光环颜色/亮度在光影下合理（不过曝、不过暗）
- [ ] 光环的半透明效果正常（无黑色方块、无穿透错误）
- [ ] 光影切换（开关）时游戏不崩溃
- [ ] 光环的发光（glow）层在光影下效果正常

**风险点**：
- `RenderSystem.setShader()` 使用的 shader program 可能被光影包替换
- 光环使用自定义 blend mode（`SRC_ALPHA / ONE` for glow），光影管线可能不支持
- `RenderSystem.disableCull()` / `depthMask` 状态在光影管线中可能被覆盖

---

## 各版本测试重点

| 版本 | 重点测试项 | 特别关注 |
|------|-----------|---------|
| **1.20.1** | T1~T13 全部 | 基准对照，全部通过才有效 |
| **1.20** | T4, T8, T9 | 与 1.20.1 差异极小 |
| **1.20.2** | T4, T9 | 已知通过，抽样复验 |
| **1.20.3** | **T1, T4, T6, T8, T9** | Shader 重构、`PersistentStateManager`、`CommandExecutionC2SPacket` |
| **1.20.4** | T1~T13 全部 | ✅ 已测试通过（见下方结果） |
| **1.19.4** | T4, T6, T8, T9 | 渲染 API 应一致；`PersistentStateManager` 签名需验证 |
| **1.19.2** | T1, T4, T8, T9 | 验证 1.19.1 API 在修复版上的稳定性 |
| **1.19.1** | T1, T4, T9 | 理论下界，重点验证 `CommandExecutionC2SPacket` 和 JOML |
| **NeoForge 1.20.1** | T1, T4, T14, T15 | Sinytra Connector 翻译 + 光影兼容（待测） |
| **Forge 1.20.1** | T1, T4, T14 | Sinytra Connector 翻译（待测） |

### 已确认不可用的版本

| 版本 | 状态 | 原因 |
|------|------|------|
| **1.20.5** | ❌ 不可用 | Fabric API 移除 `ServerPlayNetworking$PlayChannelHandler`，启动即崩 |
| **1.20.6** | ❌ 不可用 | 同上 |

---

## 已完成的测试结果

### 1.20.4 — PASS（全部通过）

```
版本: 1.20.4
结果: PASS
通过项: T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13
失败项: 无
```

**详细记录**：

| 测试项 | 结果 | 备注 |
|--------|------|------|
| T1 启动加载 | ✅ | 正常启动并进入游戏 |
| T2 客户端指令 | ✅ | 单人游戏客户端指令全部正常 |
| T3 服务端指令 | ✅ | 原版服务器正常；模组服务器需设置 OP 后大部分正常 |
| T4 显示光环 | ✅ | 渲染正常 |
| T5 隐藏光环 | ✅ | 隐藏正常 |
| T6 持久化 | ✅ | 正常 |
| T7 物理阻尼 | ✅ | 阻尼正常 |
| T8 多人同步 | ✅ | 正常 |
| T9 指令转发 | ✅ | 非 OP 玩家提示"未知命令"（预期行为，服务端要求权限等级 2） |
| T10 光环动画 | ✅ | 正常 |
| T11 资源包热重载 | ✅ | 正常 |
| T12 姿态 | ✅ | 正常 |
| T13 性能 | ✅ | 正常 |

### 1.19.4 — PASS（有条件，存在已知问题）

```
版本: 1.19.4
结果: PASS（核心功能正常，存在命令反馈问题）
通过项: T1, T4, T5, T6, T7, T8, T10, T11, T12, T13
问题项: T2, T3
```

**详细记录**：

| 测试项 | 结果 | 备注 |
|--------|------|------|
| T1 启动加载 | ✅ | 正常 |
| T2 客户端指令 | ⚠️ | Tab 补全正常；`/halo list`、`/halo active`、`/halo dump` 执行无报错但**聊天栏无输出** |
| T3 服务端指令 | ⚠️ | 模组服务器症状同上（无反馈输出）；原版服务器 `/halo show Azusa_Ke halo:hud` 正确提示需更改选择器 |
| T4 显示光环 | ✅ | 渲染正常 |
| T5 隐藏光环 | ✅ | 正常 |
| T6 持久化 | ✅ | 正常 |
| T7 物理阻尼 | ✅ | 正常 |
| T8 多人同步 | ✅ | 正常 |
| T10 光环动画 | ✅ | 正常 |
| T11 资源包热重载 | ✅ | 正常 |
| T12 姿态 | ✅ | 正常 |
| T13 性能 | ✅ | 正常 |

**已知问题**：
- `/halo config scale` 无效果（同 1.20.1，代码 bug）
- 单人 + 模组服务器：`/halo list`、`/halo active`、`/halo dump` 等查询命令无聊天输出（命令执行正常，仅无反馈）
- 原版服务器无此问题（客户端本地处理，不走服务端转发）

### 1.19.3 — FAIL（缺少 API 方法）

```
版本: 1.19.3
结果: FAIL
状态: 执行 /halo show 时崩溃
错误: NoSuchMethodError: ClientPlayNetworkHandler.getConnection()
```

**原因**：`ClientPlayNetworkHandler.getConnection()` 在 1.19.3 上不存在，1.19.4 才引入。
模组启动和渲染正常，但执行需要转发命令到服务端的指令时崩溃。

### 1.19.2 — FAIL（缺少 JOML 数学库）

```
版本: 1.19.2
结果: FAIL
状态: 启动崩溃
错误: NoClassDefFoundError: org/joml/Vector2f
```

**原因**：JOML 数学库在 1.19.3 才引入 Minecraft。`HaloDefinitionDeserializer` 使用了 `org.joml.Vector2f`，在 1.19.2 上不存在。

### 1.19.1 — FAIL（Loader 依赖检查失败）

```
版本: 1.19.1
Fabric Loader: 0.19.3
Fabric API: 0.58.5+1.19.1
结果: FAIL
状态: 未进入游戏
错误: Loader 报告 fabric-api 未安装（实际已安装），HARD_DEP_NO_CANDIDATE
```

**原因**：与 1.19.2 相同——JOML 不可用。Loader 可能在依赖解析阶段就检测到了不兼容。

### 1.20.6 — FAIL（启动崩溃）

```
版本: 1.20.6
结果: FAIL
失败项: 启动阶段
错误: NoClassDefFoundError: net/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$PlayChannelHandler
```

**原因**：Fabric API 在 1.20.5+ 中移除了 `ServerPlayNetworking.PlayChannelHandler` 内部接口。
`HaloNetwork.registerServerReceivers()` 和 `HaloNetworkClient.registerReceivers()` 均使用该接口，
导致类加载失败。1.20.5 同理。

---

## 测试结果记录模板

```
版本: 1.XX.X
Fabric Loader: X.X.X
Fabric API: X.X.X+1.XX.X
Java: 17
结果: PASS / FAIL
通过项: T1, T2, T3, ...
失败项: T4 (渲染崩溃), ...
错误日志: (粘贴 crash report 或 latest.log 关键片段)
```

---

## 如果 1.20.3~1.20.6 失败：改造清单

### 渲染层（最可能的失败点）

1. **`HaloRenderer.bindTextureSafe()`** — `RenderSystem.setShaderTexture(int, int)` 可能被移除
2. **`HaloRenderer.renderBillboard()` / `renderGlowLayer()`**
   - `GameRenderer::getPositionTexProgram` → 可能改为 `ShaderProgramKeys.POSITION_TEX`
   - `GameRenderer::getPositionTexColorProgram` → `ShaderProgramKeys.POSITION_TEX_COLOR`
   - `GameRenderer::getPositionColorProgram` → `ShaderProgramKeys.POSITION_COLOR`
   - `BufferBuilder.begin()` 返回值可能变为 `void`
3. **`GlStateManager.SrcFactor` / `DstFactor`** — 枚举值可能改名

### 网络层（T9 失败时检查）

4. **`CommandExecutionC2SPacket` 构造函数** — 签名可能变更
   - 影响：`FabricHaloCommandInterceptor.sendCommandRaw()`

### 建议改造策略

- 渲染层是最脆弱的部分，建议封装为独立的 `RenderBackend` 接口，不同版本提供不同实现
- 使用 **Gradle 多源集** 或 **运行时版本检测** 做版本隔离
