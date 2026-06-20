# 🤖 Agent 启动指令

## 给 Claude Code CLI Agent 的完整工作流

---

## 如何使用本指令

1. **复制对应阶段的"Agent 任务"部分**
2. **发送给我（Claude Code CLI）作为单个提示**
3. **我生成代码**
4. **你执行"人类检验"部分**
5. **如果通过，进入下一阶段；如果失败，我修复**

---

## PHASE 0: Project Init

### 🤖 Agent 任务（复制这段给我）

```
你是一个 Minecraft Fabric Mod 开发的 Senior 工程师。

你的任务: 完成 task-00-project-init.md 的 所有 8 个小任务。

生成以下文件:
1. settings.gradle
2. build.gradle (Fabric Loom 1.5+, MC 1.20.1, Yarn mappings)
3. gradle/wrapper/gradle-wrapper.properties (Gradle 8.8)
4. gradle.properties
5. src/main/resources/fabric.mod.json (entrypoints: main, client)
6. src/main/resources/halo.mixins.json
7. src/main/java/com/example/halo/HaloMod.java (ModInitializer, 输出日志)
8. src/main/java/com/example/halo/HaloModClient.java (ClientModInitializer)

约束:
- 遵循 Fabric 标准项目布局
- Java 17+
- 所有 gradle 依赖完整
- 输出 "Halo mod initialized" 和 "Halo client initialized"

完成后，生成的代码应该能通过: ./gradlew build
```

### 👤 人类检验

```bash
cd f:\Halo
./gradlew build

# 检查:
✅ BUILD SUCCESSFUL
✅ build/libs/halo-0.1.0.jar 存在
✅ 无编译错误

如果通过:
echo "✅ PHASE 0 通过，进入 PHASE 1"

如果失败:
重新给我看错误信息，我修复
```

---

## PHASE 1: Data Model

### 🤖 Agent 任务（复制这段给我）

```
完成 task-01-data-model.md 的所有 7 个小任务。

生成以下 Java 类:
- src/main/java/com/example/halo/data/HaloDampingConfig.java (record)
- src/main/java/com/example/halo/data/HaloPositioning.java (record)
- src/main/java/com/example/halo/data/HaloDefinition.java (record)
- src/main/java/com/example/halo/data/HaloInstance.java (class)
- src/main/java/com/example/halo/data/HaloRenderState.java (record)

生成形状类:
- src/main/java/com/example/halo/shape/HaloShape.java (sealed interface)
- src/main/java/com/example/halo/shape/BillboardShape.java
- src/main/java/com/example/halo/shape/GlowLayer.java

生成动画类:
- src/main/java/com/example/halo/animation/HaloAnimation.java
- src/main/java/com/example/halo/animation/AnimationCurve.java (sealed interface)
- src/main/java/com/example/halo/animation/PositionCurve.java
- src/main/java/com/example/halo/animation/RotationCurve.java
- src/main/java/com/example/halo/animation/builtin/ConstantCurve.java
- src/main/java/com/example/halo/animation/builtin/LinearCurve.java
- src/main/java/com/example/halo/animation/builtin/OscillateCurve.java

生成 JSON 加载器:
- src/main/java/com/example/halo/json/HaloJsonLoader.java (Fabric resource reload listener)
- src/main/java/com/example/halo/json/HaloDefinitionDeserializer.java (Gson deserializer)

生成示例:
- src/main/resources/assets/halo/halo_definitions/ring_default.json
  包含: id, shape (billboard), animation (oscillate Y + linear yaw), positioning, damping

生成测试:
- src/test/java/com/example/halo/data/HaloDataTest.java
  包含: JSON 序列化/反序列化测试

更新 HaloMod.onInitialize():
- 调用 HaloJsonLoader.register()

约束:
- 所有 record 不需要 constructor，使用 Java 15+ records
- sealed interface 使用 permits 列出所有实现
- JSON deserializer 支持多态类型
- 错误处理: 解析错误时 log.warn 但不崩溃
- 无外部库依赖（仅 Gson，已在 build.gradle）
```

### 👤 人类检验

```bash
cd f:\Halo

# 1. 编译
./gradlew build
# 期望: ✅ BUILD SUCCESSFUL

# 2. 测试
./gradlew test
# 期望: ✅ HaloDataTest 通过

# 3. 检查 JSON 文件
cat src/main/resources/assets/halo/halo_definitions/ring_default.json
# 期望: 有效的 JSON，包含所有字段

# 4. 可选: 启动游戏检查无崩溃
./gradlew runClient
# 期望: 游戏启动，无错误 (按 ESC 退出)

如果全部通过:
echo "✅ PHASE 1 通过，进入 PHASE 2"

如果失败:
重新给我看错误，我修复
```

---

## PHASE 2: Server Core

### 🤖 Agent 任务

```
完成 task-02-server-core.md 的所有任务。

生成以下类:
- src/main/java/com/example/halo/server/HaloServerEvents.java
- src/main/java/com/example/halo/server/ServerTickHandler.java (implements ServerTickEvents.End)

更新:
- src/main/java/com/example/halo/HaloMod.java: 注册 ServerTickHandler

生成测试:
- src/test/java/com/example/halo/server/ServerTickHandlerTest.java

约束:
- ServerTickHandler 在每个 server tick 被调用
- 无实际逻辑，仅框架和日志
- 测试验证事件注册成功
```

### 👤 人类检验

```bash
./gradlew build && ./gradlew test
# 期望: ✅ BUILD SUCCESSFUL，所有测试通过

如果通过:
echo "✅ PHASE 2 通过，进入 PHASE 3"
```

---

## PHASE 3: Damping Physics ⭐⭐⭐

### 🤖 Agent 任务

```
完成 task-07-damping-physics.md。这是最关键的阶段。

生成以下类:
- src/main/java/com/example/halo/physics/HaloDampingState.java
  字段: prevRelativePosition (Vec3d), prevRelativeRotation (Quaternionf), needsSnap (boolean)

- src/main/java/com/example/halo/physics/DampingPhysics.java
  静态方法:
  1. computeDampedPosition(current, target, damping, state) → Vec3d
     逻辑:
     - if (needsSnap) { needsSnap=false; return (0,0,0); }
     - offset = prevRelPos
     - k = 1 - linearFactor
     - newPos = current + k*offset
     - if (||newPos|| > maxDist) newPos = normalized(newPos) * maxDist
     - state.prevRelPos = newPos
     - return newPos

  2. computeDampedRotation(current, target, damping, state) → Quaternionf
     类似逻辑，使用四元数 slerp 和角度钳制

- src/main/java/com/example/halo/physics/HaloTickHandler.java (Fabric tick listener)

更新:
- HaloInstance: 添加 tickDamping() 方法

生成单元测试 src/test/java/com/example/halo/physics/DampingPhysicsTest.java:
必须包含这 4 个测试，全部必须 PASS:
1. testInstantSnap(): 验证 snap 时位置为 (0,0,0)
2. testDampingDecay(): k=0.5 时，每帧衰减 50%
3. testClampDistance(): distance > maxDist 时钳制正确
4. testAngularDamping(): 四元数阻尼数值正确

约束:
- 物理逻辑必须精确，数学要点验证在单元测试中
- 不依赖其他 halo 模块（可独立测试）
- 四元数使用 Minecraft/JOML 的 Quaternionf
```

### 👤 人类检验 ⭐ 最重要

```bash
# 必须通过这个测试，否则不继续！
./gradlew test --tests "*DampingPhysicsTest"

# 期望输出:
# > Task :test
# com.example.halo.physics.DampingPhysicsTest > testInstantSnap PASSED
# com.example.halo.physics.DampingPhysicsTest > testDampingDecay PASSED
# com.example.halo.physics.DampingPhysicsTest > testClampDistance PASSED
# com.example.halo.physics.DampingPhysicsTest > testAngularDamping PASSED

如果 4 个全 PASS:
echo "✅ PHASE 3 通过，物理正确"

如果任何一个 FAIL:
❌ STOP! 让 Agent 修复那个具体的测试
```

---

## PHASE 4: CLI Commands

### 🤖 Agent 任务

```
完成 task-08-halo-config-cli.md。

生成:
- src/main/java/com/example/halo/config/HaloConfig.java
- src/main/java/com/example/halo/manager/HaloManager.java (singleton)
- src/main/java/com/example/halo/command/HaloConfigCommand.java

Brigadier 命令树:
/halo list → 列出所有定义
/halo show <entity> <definition> → 添加光环
/halo hide <entity> → 移除光环
/halo config <param> <value> → 修改配置
  - linear-damping [0-1]
  - scale [0.1-5.0]
  (其他参数可选)

更新:
- HaloMod: CommandRegistrationCallback 注册命令

生成测试:
- src/test/java/com/example/halo/command/HaloCommandTest.java

约束:
- HaloManager 是全局单例
- 配置值有边界检查
- 命令需要权限等级 2
```

### 👤 人类检验

```bash
./gradlew build && ./gradlew test
# 期望: ✅ 编译和测试通过

# 关键: 游戏内验证
./gradlew runClient

# 在游戏内执行:
/halo list
# 期望: 看到 "ring_default"

/halo show @s ring_default
# 期望: "Halo ring_default shown on [你的名字]"

如果命令都有反应:
echo "✅ PHASE 4 通过"
```

---

## PHASE 5: Lifecycle Management

### 🤖 Agent 任务

```
完成 task-09-halo-lifecycle.md。

生成:
- src/main/java/com/example/halo/data/HaloEntityData.java
- src/main/java/com/example/halo/lifecycle/EntityHaloTracker.java
- src/main/java/com/example/halo/lifecycle/HaloEntityEventHandler.java
- src/main/java/com/example/halo/lifecycle/HaloWorldSaveData.java (PersistentState)

功能:
1. NBT 持久化: 光环数据保存在实体 NBT
2. 实体跟踪: 记录活跃光环
3. 传送检测: 标记 needsSnap (距离 >20 blocks 或 tick 内移动异常)
4. 世界保存: 光环生命周期数据存储

生成测试:
- src/test/java/com/example/halo/lifecycle/EntityHaloTrackerTest.java

约束:
- 使用 NBT 而不是其他序列化
- 持久化不影响性能
```

### 👤 人类检验

```bash
./gradlew build && ./gradlew test
# 期望: 编译和测试通过

./gradlew runClient

# 游戏内:
/halo show @s ring_default
# (光环显示)

/save-all
# (保存)

# 关闭游戏 (ESC → Save and Quit)

# 重新启动:
./gradlew runClient
# 期望: 光环仍在玩家头上！

/tp @s ~ ~10 ~
# 期望: 光环快速跳到新位置（无滑动）

如果持久化和传送都正确:
echo "✅ PHASE 5 通过"
```

---

## PHASE 6: Renderer ⭐⭐⭐ 最后大阶段

### 🤖 Agent 任务

```
完成 task-10-halo-renderer.md。这是最复杂的部分，涉及 OpenGL 渲染。

生成:
- src/main/java/com/example/halo/render/HaloRenderer.java
  方法:
  1. renderHalos(matrixStack, camera, tickDelta)
     对每个可见光环调用 renderSingleHalo()

  2. renderSingleHalo(instance, matrixStack, camera, tickDelta)
     - 获取实体位置和头部锚点
     - 计算光环世界坐标
     - 应用四元数旋转
     - 应用缩放
     - 绘制广告牌（面向相机）
     - 可选: 绘制发光层

  3. renderBillboard(billboard, matrixStack, camera)
     - 绑定纹理
     - 绘制 2 个三角形组成的四边形
     - 顶点: (-w, -h, 0) → (w, -h, 0) → (w, h, 0) → (-w, h, 0)

- src/main/java/com/example/halo/render/HaloClientManager.java (客户端状态)
- src/main/java/com/example/halo/render/HaloRenderListener.java (Fabric 事件钩子)
- 更新 HaloModClient: 注册渲染监听器

生成测试:
- src/test/java/com/example/halo/render/HaloRendererTest.java
  测试: 四元数转矩阵、面向相机计算、帧插值

约束:
- 使用 RenderSystem 进行 GL 调用
- 广告牌总是面向摄像头（重要！）
- 实现帧差值: 位置用 lerp，旋转用 slerp
- 支持 50+ 实体而不卡顿
```

### 👤 人类检验 ⭐ 最后的"看得见"

```bash
./gradlew build && ./gradlew test
# 期望: 编译和测试通过

./gradlew runClient

# 关键: 能看到光环吗？
/halo show @s ring_default

# 检验清单:
✅ 光环出现在头上（不是看不见）
✅ 光环面向摄像头（旋转正确）
✅ 走动时平滑跟随（无卡顿）
✅ 60+ FPS（流畅）

走动、转身、跳跃 → 光环始终平滑跟随

如果以上全部通过:
echo "✅ PHASE 6 通过，系统可见！"

如果光环不可见或卡顿:
❌ 停止，让 Agent 修复渲染问题
```

---

## PHASE 7: Final Human Integration Check

### 👤 人类最终验收

```bash
./gradlew clean build && ./gradlew test
# 期望: 全部通过

./gradlew runClient

# 完整的游戏内测试序列:

1️⃣ 基础命令
/halo list → 看到 ring_default ✓
/halo show @s ring_default → 看到光环 ✓

2️⃣ 物理行为
走动 → 光环平滑跟随 ✓
转身 → 光环随之旋转 ✓
跳跃 → 光环继续平滑 ✓

3️⃣ 配置系统
/halo config linear-damping 0.8 → 光环变慢 ✓
/halo config linear-damping 0.1 → 光环变快 ✓
/halo config scale 2.0 → 光环变大 ✓
/halo config scale 0.5 → 光环变小 ✓

4️⃣ 传送与持久化
/save-all ✓
关闭游戏
重启: ./gradlew runClient
光环仍在 ✓
/tp @s 0 100 0 → 光环快照（无滑动） ✓

5️⃣ 清理
/halo hide @s → 光环消失 ✓

如果全部通过: 🎉 系统完整！
```

---

## 快速参考

| 阶段 | 时间 | Agent 生成           | 验收命令                             |
| ---- | ---- | -------------------- | ------------------------------------ |
| P0   | 30m  | Gradle + entrypoints | `./gradlew build`                    |
| P1   | 45m  | 数据类 + JSON        | `./gradlew test`                     |
| P2   | 30m  | 服务器核心           | `./gradlew build`                    |
| P3   | 45m  | 物理引擎 ⭐          | `./gradlew test --tests "*Damping*"` |
| P4   | 45m  | 命令系统             | `/halo list`                         |
| P5   | 40m  | 生命周期             | `/save-all` 后重启                   |
| P6   | 60m  | 渲染系统 ⭐          | 游戏内看光环                         |
| P7   | 20m  | （人类）             | 完整游戏测试                         |

**总计**: ~5.5 小时

---

## 重要提醒

✅ **一次一个阶段** - 不要跳过  
✅ **每个阶段通过才继续** - 确保质量  
✅ **游戏测试是必需的** - 不只是单元测试  
✅ **如果失败，让 Agent 修复** - 不要自己改  
✅ **记录每个阶段的时间** - 了解进度

---

**现在你可以开始了！给我发送任何一个"Agent 任务"部分，我会生成代码。**
