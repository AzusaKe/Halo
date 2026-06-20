# Vibe Coding 执行指南 - Agent 启动顺序 & 人类检验步骤

> **Vibe Coding**: 开发者按照"感觉"(vibe)顺序执行任务，而不是严格的依赖链
> 本指南将"感觉正确的顺序"转换为可执行的、有明确检验点的步骤

---

## 🚀 **执行模式**

### **推荐: 串行模式** (一个Agent接一个)

```
Agent-1 执行完 ──> 人类检验通过 ──> Agent-2 执行 ──> 人类检验 ──> ...

时间: 3-4 周
风险: 最低
质量: 最高
```

---

## 📋 **Agent 启动顺序表**

| 步骤   | Agent   | 任务        | 依赖       | 时间 | 人类检验                  |
| ------ | ------- | ----------- | ---------- | ---- | ------------------------- |
| **1**  | Agent-1 | task-00     | 无         | 1 天 | ✅ `./gradlew build` 成功 |
| **2**  | Agent-1 | task-01     | task-00    | 2 天 | ✅ JSON 加载、单测通过    |
| **3**  | Agent-1 | task-02     | task-01    | 2 天 | ✅ 服务器 tick 工作       |
| **4**  | Agent-1 | task-03     | task-02    | 1 天 | ✅ 网络同步正常           |
| **5**  | Agent-1 | task-04     | task-03    | 2 天 | ✅ 基础渲染管线           |
| **6**  | Agent-1 | task-05     | task-04    | 1 天 | ✅ 深度和发光效果         |
| **7**  | Agent-1 | task-06     | task-05    | 2 天 | ✅ 配置系统工作           |
| **8**  | Agent-2 | **task-07** | task-01/02 | 2 天 | ✅ 物理单测通过           |
| **9**  | Agent-2 | **task-08** | task-07    | 2 天 | ✅ 命令系统工作           |
| **10** | Agent-2 | **task-09** | task-08    | 2 天 | ✅ 生命周期无泄漏         |
| **11** | Agent-3 | **task-10** | task-09/04 | 3 天 | ✅ 渲染 50+halos @60FPS   |
| **12** | Agent-3 | **task-11** | task-10    | 2 天 | ✅ 动画时间同步           |
| **13** | Agent-3 | **task-12** | task-11    | 1 天 | ✅ 头部跟随平滑           |
| **14** | Agent-3 | **task-13** | task-12    | 2 天 | ✅ GUI 响应式             |
| **15** | Agent-3 | **task-14** | task-13    | 1 天 | ✅ 模型导入不崩溃         |
| **16** | Agent-3 | **task-15** | task-14    | 1 天 | ✅ 命令可靠               |
| **17** | Lead    | **verify**  | task-15    | 1 天 | ✅ 集成测试全过           |

---

## 👤 **Agent-1 启动 (基础框架)**

### Task-00: Project Init

**Agent 命令** (给Agent的指令):

```
你好！请阅读 f:\Halo\task\task-00-project-init.md

按照任务要求，完成以下工作：
1. 创建 settings.gradle
2. 创建 build.gradle (Fabric Loom 1.5+, MC 1.20.1)
3. 创建 gradle.properties 和 gradle-wrapper.properties
4. 创建 fabric.mod.json 和 halo.mixins.json
5. 创建 HaloMod.java 和 HaloModClient.java 两个入口点

完成后提交 PR，标题: "[task-00] Project structure init"
```

**人类检验步骤** (你需要做的):

```bash
# 步骤 1: 拉取 PR 代码
git checkout <agent-pr-branch>

# 步骤 2: 编译检验
./gradlew build

# 预期结果:
#   > BUILD SUCCESSFUL
#   > jar文件生成在 build/libs/halo-0.1.0.jar

# 步骤 3: 运行检验 (可选，如果需要启动游戏)
./gradlew runClient

# 预期结果:
#   游戏启动 → 加载屏幕 → 看到"Halo mod initialized"日志

# 步骤 4: 代码检查
grep -r "class HaloMod" src/
grep -r "class HaloModClient" src/

# 预期结果: 两个文件都存在且编译无误
```

**✅ 检验通过标志**:

- [ ] `./gradlew build` 成功，无 error
- [ ] 生成 `build/libs/halo-0.1.0.jar`
- [ ] 日志显示 "Halo mod initialized"
- [ ] 日志显示 "Halo client initialized"

**❌ 检验失败** → 要求 Agent 修复，修复后重新检验

**✅ 通过后** → Merge PR，Agent 继续 task-01

---

### Task-01: Data Model

**Agent 命令**:

```
阅读 f:\Halo\task\task-01-data-model.md

完成以下工作:
1. 创建 data/ 包中的所有数据类 (6 个 .java 文件)
   - HaloDampingConfig.java (record)
   - HaloPositioning.java (record)
   - HaloDefinition.java (record)
   - HaloInstance.java (class)
   - HaloRenderState.java (record)

2. 创建 shape/ 包中的形状类型 (3 个 .java 文件)
   - HaloShape.java (sealed interface)
   - BillboardShape.java
   - GlowLayer.java

3. 创建 animation/ 包中的动画类型 (4 个 .java 文件)
   - HaloAnimation.java
   - AnimationCurve.java (sealed interface)
   - PositionCurve.java
   - RotationCurve.java
   + builtin/ 中 3 个实现类

4. 创建 json/ 包中的加载器 (2 个 .java 文件)
   - HaloJsonLoader.java
   - HaloDefinitionDeserializer.java

5. 创建示例 JSON: ring_default.json

6. 编写单元测试覆盖所有数据类

提交 PR: "[task-01] Data model & JSON parsing"
```

**人类检验步骤**:

```bash
# 步骤 1: 编译检验
./gradlew build

# 预期: BUILD SUCCESSFUL

# 步骤 2: 运行单元测试
./gradlew test

# 预期结果:
#   > 所有测试通过 (绿色)
#   > Test Summary: X tests passed
#   > 代码覆盖 > 80%

# 步骤 3: JSON 加载检验
# 启动游戏 + 运行命令
./gradlew runClient

# 游戏内执行:
#   /reload
#   /halo dump
#
# 预期结果:
#   Loaded halo definitions: 1
#   - ring_default (或 halo:ring_default)

# 步骤 4: 类结构检验
grep -c "public record" src/main/java/com/example/halo/data/*.java
# 预期: >= 3

grep -c "sealed interface" src/main/java/com/example/halo/shape/*.java
# 预期: >= 1

find src/main/java/com/example/halo/animation/builtin -name "*.java"
# 预期: 3 个 curve 实现类
```

**✅ 检验通过标志**:

- [ ] `./gradlew test` 所有绿色
- [ ] 代码覆盖 >= 80%
- [ ] `/halo dump` 显示 ring_default
- [ ] 无 ClassDefNotFound 错误

**✅ 通过后** → Merge，Task-02

---

### Task-02: Server Core

**Agent 命令**:

```
阅读 f:\Halo\task\task-02-server-core.md

创建服务器核心:
1. 事件系统 (event/ 包)
   - 自定义事件类 3-5 个

2. Mixin 类 (mixin/ 包)
   - ServerWorldMixin.java
   - ServerPlayerEntityMixin.java

3. 工具类 (util/ 包)
   - 实体选择、NBT 工具等

编写单元测试，注册事件监听器。

提交 PR: "[task-02] Server core & events"
```

**人类检验**:

```bash
./gradlew build test

# 检验 Mixin 是否正确应用
grep -c "@Mixin" src/main/java/com/example/halo/mixin/*.java
# 预期: >= 2

# 启动游戏验证没有崩溃
./gradlew runClient
# 预期: 服务器无错误日志
```

**✅ 通过标志**: 编译通过 + 无崩溃

---

### Task-03 到 Task-06

重复以上流程 (编译 → 测试 → 游戏内验证)

---

## 🔧 **Agent-2 启动 (光环系统 - 后端)**

_前提: task-01 ~ task-06 已完成并合并_

### Task-07: Damping Physics

**Agent 命令**:

```
阅读 f:\Halo\task\task-07-damping-physics.md

这是光环系统的核心！

创建:
1. physics/ 包
2. DampingPhysics.java - 静态方法计算阻尼
3. HaloDampingState.java - 内部状态
4. HaloTickHandler.java - 服务器 tick 事件

关键: 实现物理公式:
  newPos = currentPos + (1 - k) * prevOffset
  如果 ||newPos|| > maxDist: 钳制到 maxDist

编写详细的单元测试:
  - testSnap() 测试快照
  - testDecay() 测试衰减
  - testClamp() 测试钳制
  - testTeleport() 测试传送

提交 PR: "[task-07] Damping physics engine"
```

**人类检验**:

```bash
# 步骤 1: 单元测试必须全过
./gradlew test --tests "*DampingPhysicsTest*"

# 预期:
#   > All tests passed
#   > testSnap PASSED
#   > testDecay PASSED
#   > testClamp PASSED
#   > testTeleport PASSED

# 步骤 2: 检查物理逻辑
# 打开 DampingPhysics.java, 检查公式是否正确:
#   k = 1 - linearFactor
#   newPos = current + k * offset
#   max clamp 逻辑存在

# 步骤 3: 性能检验 (可选)
# 单元测试中应该有性能基准
grep -A 5 "testPerformance" src/test/java/com/example/halo/physics/DampingPhysicsTest.java

# 步骤 4: 编译检验
./gradlew build

# 预期: BUILD SUCCESSFUL (不能有 warning 关于物理)
```

**✅ 通过标志**:

- [ ] `./gradlew test` 所有物理测试通过
- [ ] testClamp, testDecay, testSnap 都绿色
- [ ] 编译无错误
- [ ] 物理公式代码审查通过

**❌ 常见问题**:

- 如果 testDecay 失败: 检查 k 值计算是否正确
- 如果 testClamp 失败: 检查 maxDistance 逻辑
- 如果 testSnap 失败: 检查 needsSnap 标志

---

### Task-08: Config & CLI

**Agent 命令**:

```
阅读 f:\Halo\task\task-08-halo-config-cli.md

创建命令系统:
1. config/ 包
   - HaloConfig.java - 配置数据结构

2. command/ 包
   - HaloConfigCommand.java - Brigadier 命令树

3. manager/ 包
   - HaloManager.java - 全局管理器

实现命令:
  /halo show <entity> <definition>
  /halo hide <entity>
  /halo config <param> <value>
  /halo list

编写单元测试验证边界值。

提交 PR: "[task-08] Config & CLI commands"
```

**人类检验**:

```bash
# 步骤 1: 编译 + 测试
./gradlew build test

# 步骤 2: 启动游戏，手动测试命令
./gradlew runClient

# 游戏内:
/halo list
# 预期: "Loaded halo definitions: 1" + "- halo:ring_default"

/halo show @s ring_default
# 预期: "Halo halo:ring_default shown on [player name]"

/halo config linear-damping 0.5
# 预期: "Set linear-damping to 0.5"

/halo hide @s
# 预期: "Halo hidden"

# 步骤 3: 检查无崩溃
# 从 latest.log 中检查是否有异常
type logs\latest.log | findstr "Exception\|Error"
# 预期: 无输出

# 步骤 4: 边界检验
/halo config linear-damping 1.5
# 预期: 被钳制到 [0, 1] 范围，显示"Set linear-damping to 1.0"

/halo config linear-damping -0.5
# 预期: 被钳制到 [0, 1] 范围，显示"Set linear-damping to 0.0"
```

**✅ 通过标志**:

- [ ] `/halo list` 工作
- [ ] `/halo show` 工作
- [ ] `/halo config` 工作
- [ ] `/halo hide` 工作
- [ ] 边界值被正确钳制
- [ ] 无崩溃错误

---

### Task-09: Lifecycle

**Agent 命令**:

```
阅读 f:\Halo\task\task-09-halo-lifecycle.md

实现生命周期管理:
1. lifecycle/ 包
   - EntityHaloTracker.java
   - HaloEntityEventHandler.java

2. data/ 包扩展
   - HaloEntityData.java (NBT 工具)

3. world/ 包
   - HaloWorldSaveData.java (持久化)

关键需求:
- 传送时标记 snap
- 实体死亡时清理
- NBT 数据持久化
- 无内存泄漏

编写内存泄漏测试。

提交 PR: "[task-09] Halo lifecycle & persistence"
```

**人类检验**:

```bash
# 步骤 1: 编译 + 测试
./gradlew build test

# 步骤 2: 内存泄漏检验 (重要!)
# 启动游戏
./gradlew runClient

# 游戏内:
/halo show @s ring_default

# 让游戏运行 30 分钟，每 5 分钟执行一次:
/kill @s
/halo show @s ring_default

# 观察 Java 内存占用:
# 启动 jconsole 或 JProfiler
# 如果内存持续增长 > 500MB，则有泄漏

# 步骤 3: 传送测试
/halo show @s ring_default

# 观察光环在头上

/tp @s ~ ~10 ~

# 预期: 光环 snap 到新位置 (无滑动)

# 步骤 4: 持久化测试
/halo show @s ring_default

/save-all

# 重启游戏，不加载之前的存档，然后加载同一个世界
# 预期: 光环仍然在玩家头上

# 步骤 5: 实体死亡测试
/halo show @s ring_default
/kill @s

# 预期: 光环消失，无错误日志
```

**✅ 通过标志**:

- [ ] 内存占用稳定 (1h+ 不增长)
- [ ] 传送 snap 工作
- [ ] NBT 持久化工作
- [ ] 实体死亡清理正常
- [ ] 无"out of memory"错误

---

## 👨‍🎨 **Agent-3 启动 (光环系统 - 渲染)**

_前提: task-07, 08, 09 完成_

### Task-10: Renderer

**Agent 命令**:

```
阅读 f:\Halo\task\task-10-halo-renderer.md

实现渲染管线 (最复杂的任务!):

1. render/ 包
   - HaloRenderer.java (核心渲染)
   - HaloClientManager.java (客户端状态)
   - HaloRenderListener.java (事件钩子)

关键:
- 广告牌面向相机
- 四元数旋转
- 帧差值插值 (tickDelta)
- 性能检验: 50+halos @60FPS

编写性能测试。

提交 PR: "[task-10] Halo renderer core"
```

**人类检验** (最严格!):

```bash
# 步骤 1: 编译 + 测试
./gradlew build test

# 步骤 2: 性能测试 - 创建 50 个光环
./gradlew runClient

# 游戏内: 创建 50 个盔甲架，每个上都放一个光环
/summon armor_stand ~ ~1 ~ {CustomName:'"{\"text\":\"stand_0\"}"}
# 重复 50 次...

# 或者用这个脚本 (如果支持):
for /L %i in (1,1,50) do (
  /summon armor_stand ~ ~1 ~ {NoGravity:1b}
  /halo show @e[type=armor_stand,limit=1] ring_default
)

# 步骤 3: FPS 检验 (重要!)
# F3 打开调试屏幕，看 FPS 数值
# 预期: >= 60 FPS (稳定，不低于 50)

# 如果低于 50 FPS，则测试失败，要求 Agent 优化

# 步骤 4: 视觉检验
# 靠近光环 → 应该是正方形纹理，面向你
# 远离光环 → 应该被剔除
# 转身看 → 光环始终面向你 (不转向)
# 走动 → 光环平滑跟随 (无卡顿)

# 步骤 5: 发光检验
# 光环应该有半透明的绿色发光
# 发光应该有脉冲效果 (如果定义了)

# 步骤 6: 清理
# 删除所有盔甲架
/kill @e[type=armor_stand]

# 再验证 FPS 恢复到 60+ (无内存泄漏)
```

**✅ 通过标志**:

- [ ] 50 个 halos: >= 60 FPS (稳定)
- [ ] 光环总是面向相机
- [ ] 光环平滑跟随头部
- [ ] 发光效果正确
- [ ] 无渲染错误或撕裂

**❌ 常见问题**:

- FPS 低于 50: 需要优化 (批处理、LOD 等)
- 光环不面向相机: 检查四元数旋转
- 发光不工作: 检查 blend mode

---

### Task-11: Animation Evaluator

**Agent 命令**:

```
阅读 f:\Halo\task\task-11-animation-evaluator.md

实现动画评估:

创建:
1. animation/ 包
   - HaloAnimationEvaluator.java (曲线求值)

关键:
- Sine 振荡曲线
- Linear 线性曲线
- Constant 常数曲线
- 时间同步 (milliseconds)

编写曲线测试。

提交 PR: "[task-11] Animation evaluator"
```

**人类检验**:

```bash
# 步骤 1: 编译 + 测试
./gradlew build test

# 步骤 2: 时间同步检验
./gradlew runClient

# 游戏内:
/halo show @s ring_default

# 观察 1 分钟，光环应该:
# - 上下摇晃 (如果有 Y 振荡)
# - 旋转 (如果有 yaw 线性)
# - 周期应该匹配 JSON 中的定义 (frequency)

# 步骤 3: 性能检验 (50 个 halos 的动画评估)
# 同样的 50 halos 测试
# 预期: 仍然 >= 60 FPS (动画评估不应该降速)
```

**✅ 通过标志**:

- [ ] 动画在 1 分钟内重复周期正确
- [ ] 50+halos 动画: 仍 >= 60 FPS
- [ ] 无时间漂移

---

### Task-12 到 Task-15

重复以上流程，难度逐渐降低

---

## 🏁 **最后: Verification (Lead)**

### Task-Verify: Integration Test

**Lead 检验**:

```bash
# 完整场景测试

# 步骤 1: 启动游戏
./gradlew runClient

# 步骤 2: 完整流程
/reload
/halo list                           # 列出所有
/halo show @s ring_default           # 显示光环
/tp @s ~ ~10 ~                       # 传送，光环快照
/halo config linear-damping 0.8      # 调整阻尼
# 走动观察光环跟随

/halo hide @s                        # 隐藏
/halo show @s ring_default           # 重新显示

/save-all                            # 保存
# 重启游戏 + 加载世界
# 验证光环仍在

# 步骤 3: 性能最终检验
/summon armor_stand ~ ~2 ~ {NoGravity:1b}
# 重复 10 次...
# 创建 10 个 halos
# F3 观察 FPS >= 55

# 步骤 4: 日志检查
type logs\latest.log | findstr /v "INFO" | findstr "ERROR\|Exception"
# 预期: 无输出 (无错误)

# 步骤 5: 音频/视觉检验
# 光环应该闪闪发光
# 没有明显的卡顿或撕裂
# 没有奇怪的颜色变化

# 步骤 6: 清理存档
# 备份世界
# 测试新存档
```

**✅ 最终通过**:

- [ ] 所有命令工作
- [ ] FPS >= 55 (10 halos 场景)
- [ ] 0 个崩溃
- [ ] 0 个错误日志
- [ ] 视觉效果满足预期

---

## 📊 **检验清单汇总**

打印出来，每完成一个 task 就打勾:

```
Base Framework (Agent-1):
├─ [   ] Task-00: build successful, ./gradlew build ✓
├─ [   ] Task-01: JSON loads, unit tests ✓
├─ [   ] Task-02: Server events, no crashes ✓
├─ [   ] Task-03: Networking sync ✓
├─ [   ] Task-04: Rendering pipeline ✓
├─ [   ] Task-05: Depth glow ✓
├─ [   ] Task-06: Config system ✓

Halo Backend (Agent-2):
├─ [   ] Task-07: Physics tests all green ✓
├─ [   ] Task-08: Commands work, no crashes ✓
├─ [   ] Task-09: 1h+ no memory leak, persist ✓

Halo Rendering (Agent-3):
├─ [   ] Task-10: 50 halos @60FPS ✓
├─ [   ] Task-11: Animation syncs @60FPS ✓
├─ [   ] Task-12: Head-track smooth ✓
├─ [   ] Task-13: GUI responsive ✓
├─ [   ] Task-14: Model import stable ✓
├─ [   ] Task-15: Commands reliable ✓

Final Integration:
└─ [   ] Verify: E2E test complete ✓
```

---

## ⏱️ **时间估计**

| 阶段     | 任务         | 时间       | 人类检验        |
| -------- | ------------ | ---------- | --------------- |
| 基础     | task-00~06   | 12-14 天   | 7 个检验点      |
| 后端     | task-07~09   | 6-8 天     | 3 个检验点      |
| 渲染     | task-10~15   | 9-12 天    | 6 个检验点      |
| 最终     | verify       | 1-2 天     | 6 步完整测试    |
| **总计** | **17 tasks** | **3-4 周** | **22 个检验点** |

---

## 🎯 **关键原则**

### 对 Agent 的要求

1. ✅ 严格按 task 文件实现
2. ✅ 每个 task 完成后提交 PR (不要一次提交多个)
3. ✅ PR 标题格式: `[task-XX] <description>`
4. ✅ PR 描述包含单元测试通过截图
5. ✅ 等待人类检验通过再继续下一个

### 对人类的要求

1. ✅ 每个 PR 收到后 48 小时内检验
2. ✅ 检验失败立即反馈具体问题
3. ✅ 通过后 merge 并标记 checkpoint
4. ✅ 记录所有性能数据 (FPS, 内存等)
5. ✅ 保留所有测试日志

---

## 🚨 **失败重启**

如果某个 task 检验失败:

```
1. 反馈给 Agent 具体失败原因
2. Agent 修复并重新提交 PR (同一个 PR 更新，或新 PR)
3. 人类重新检验
4. 通过后再继续下一个

不要跳过失败的 task!
```

---

## 🎬 **立即开始**

```bash
# 1. 打印本指南
# 2. 准备好 Agent-1
# 3. 给 Agent-1 指令:
#    "请读 f:\Halo\task\task-00-project-init.md,
#     然后完成其中的所有工作，
#     完成后提交 PR 到 develop 分支，
#     标题: [task-00] Project structure init"

# 4. 等待 Agent-1 的 PR
# 5. 按上面的检验步骤检验

# 6. 如果通过，merge 并准备好 Agent-2 的 task-01
```

---

**现在就可以开始！** 🚀

预计 3-4 周完成整个光环系统。祝你好运！
