# 🎵 Vibe Coding 执行流程

## 单机 Claude Code CLI 开发工作流

---

## 📋 快速总览

**总体耗时**: ~5-6 小时（单机）  
**执行方式**: Agent 生成代码 → 人类验收 → 循环（8 个阶段）  
**成功标志**: `./gradlew runClient` 后，游戏内执行 `/halo show @s ring_default` 光环出现

---

## 🚀 执行流程（8 个阶段）

### **PHASE 0: Agent 初始化项目**

**⏱️ 耗时**: 30 分钟  
**🤖 Agent 任务**:

```bash
# Agent 应该执行：
1. 生成 settings.gradle
2. 生成 build.gradle (Fabric Loom 1.5+, MC 1.20.1)
3. 生成 gradle.properties
4. 生成 gradle/wrapper/gradle-wrapper.properties
5. 生成 src/main/resources/fabric.mod.json
6. 生成 src/main/resources/halo.mixins.json
7. 生成 src/main/java/com/example/halo/HaloMod.java (main entrypoint)
8. 生成 src/main/java/com/example/halo/HaloModClient.java (client entrypoint)
```

**参考文件**: `task-00-project-init.md`

**👤 人类检验**:

```bash
# 1. 验证项目构建
cd f:\Halo
./gradlew build

# 预期结果: ✅ BUILD SUCCESSFUL
# 产物: build/libs/halo-0.1.0.jar

# 2. 验证输出
如果看到:
  ✅ "Halo mod initialized" (服务器日志)
  ✅ "Halo client initialized" (客户端日志)
  ✅ 无编译错误

则通过 ✓
```

**✅ 检验清单** (task-00 通过条件):

- [ ] `./gradlew build` 成功编译
- [ ] JAR 文件生成在 `build/libs/`
- [ ] 无编译错误
- [ ] 无 Gradle 下载失败

**📝 验收反馈**:

```
✅ PASS: "项目已初始化，可以构建"
❌ FAIL: 描述具体错误，Agent 修复后重试
```

---

### **PHASE 1: Agent 生成数据模型**

**⏱️ 耗时**: 45 分钟  
**🤖 Agent 任务**:

```bash
# 生成以下 Java 类:
1. src/main/java/com/example/halo/data/HaloDampingConfig.java
2. src/main/java/com/example/halo/data/HaloPositioning.java
3. src/main/java/com/example/halo/data/HaloDefinition.java
4. src/main/java/com/example/halo/data/HaloInstance.java
5. src/main/java/com/example/halo/data/HaloRenderState.java

# 生成形状类:
6. src/main/java/com/example/halo/shape/HaloShape.java (sealed interface)
7. src/main/java/com/example/halo/shape/BillboardShape.java
8. src/main/java/com/example/halo/shape/GlowLayer.java

# 生成动画类:
9. src/main/java/com/example/halo/animation/HaloAnimation.java
10. src/main/java/com/example/halo/animation/AnimationCurve.java
11. src/main/java/com/example/halo/animation/PositionCurve.java
12. src/main/java/com/example/halo/animation/RotationCurve.java
13. src/main/java/com/example/halo/animation/builtin/ConstantCurve.java
14. src/main/java/com/example/halo/animation/builtin/LinearCurve.java
15. src/main/java/com/example/halo/animation/builtin/OscillateCurve.java

# 生成 JSON 加载器:
16. src/main/java/com/example/halo/json/HaloJsonLoader.java
17. src/main/java/com/example/halo/json/HaloDefinitionDeserializer.java

# 生成示例 JSON:
18. src/main/resources/assets/halo/halo_definitions/ring_default.json

# 生成单元测试:
19. src/test/java/com/example/halo/data/HaloDataTest.java
```

**参考文件**: `task-01-data-model.md`

**👤 人类检验**:

```bash
# 1. 编译所有新类
./gradlew build

# 预期: ✅ BUILD SUCCESSFUL

# 2. 运行单元测试
./gradlew test

# 预期: ✅ x个测试通过 (HaloDataTest)

# 3. 检查 JSON 有效性
cat src/main/resources/assets/halo/halo_definitions/ring_default.json
# 应该能看到: id, shape, animation, positioning, damping 字段

# 4. 验证游戏启动
./gradlew runClient
# 游戏启动不崩溃
```

**✅ 检验清单** (task-01 通过条件):

- [ ] `./gradlew build` 成功
- [ ] `./gradlew test` 所有数据测试通过
- [ ] JSON 文件格式合法（非格式错误）
- [ ] 没有 deserialization 错误
- [ ] 游戏客户端启动正常

**📝 验收反馈**:

```
✅ PASS: "数据模型完整，JSON 加载正常"
❌ FAIL: 指出具体编译或测试错误
```

---

### **PHASE 2: Agent 生成服务器核心**

**⏱️ 耗时**: 30 分钟  
**🤖 Agent 任务**:

```bash
# 生成服务器核心类:
1. src/main/java/com/example/halo/server/HaloServerEvents.java
2. src/main/java/com/example/halo/server/ServerTickHandler.java
3. 更新 HaloMod.java: 注册服务器事件监听器

# 生成单元测试:
4. src/test/java/com/example/halo/server/ServerTickHandlerTest.java
```

**参考文件**: `task-02-server-core.md`

**👤 人类检验**:

```bash
# 1. 编译
./gradlew build
# 预期: ✅ BUILD SUCCESSFUL

# 2. 运行测试
./gradlew test
# 预期: ✅ ServerTickHandlerTest 通过

# 3. 快速服务器启动测试
./gradlew runServer
# 让服务器运行 5 秒后关闭 (Ctrl+C)
# 预期: 日志中没有关于 halo 的错误
```

**✅ 检验清单** (task-02 通过条件):

- [ ] `./gradlew build` 成功
- [ ] `./gradlew test` 服务器测试通过
- [ ] 服务器启动无 halo 相关错误
- [ ] 事件监听器注册成功

**📝 验收反馈**:

```
✅ PASS: "服务器核心就绪"
❌ FAIL: 描述启动错误或测试失败
```

---

### **PHASE 3: Agent 生成阻尼物理引擎**

**⏱️ 耗时**: 45 分钟  
**🤖 Agent 任务**:

```bash
# 生成物理引擎:
1. src/main/java/com/example/halo/physics/DampingPhysics.java
2. src/main/java/com/example/halo/physics/HaloDampingState.java
3. src/main/java/com/example/halo/physics/HaloTickHandler.java
4. 更新 HaloInstance.java: 添加 tickDamping() 方法

# 生成单元测试:
5. src/test/java/com/example/halo/physics/DampingPhysicsTest.java
   - testInstantSnap(): snap=true 时位置为 (0,0,0)
   - testDampingDecay(): k=0.5 时位置每帧衰减 50%
   - testClampDistance(): distance > maxDist 时钳制到 maxDist
   - testAngularDamping(): 四元数阻尼正确
```

**参考文件**: `task-07-damping-physics.md`

**👤 人类检验**:

```bash
# 1. 编译
./gradlew build
# 预期: ✅ BUILD SUCCESSFUL

# 2. 运行物理测试（最关键）
./gradlew test --tests "*DampingPhysicsTest"

# 预期输出类似:
# > Task :test
# com.example.halo.physics.DampingPhysicsTest > testInstantSnap PASSED
# com.example.halo.physics.DampingPhysicsTest > testDampingDecay PASSED
# com.example.halo.physics.DampingPhysicsTest > testClampDistance PASSED
# com.example.halo.physics.DampingPhysicsTest > testAngularDamping PASSED
# 4 tests PASSED

# 3. 完整测试
./gradlew test
# 所有测试应该通过
```

**✅ 检验清单** (task-03 通过条件):

- [ ] `./gradlew build` 成功
- [ ] **所有 4 个 DampingPhysicsTest 通过**
- [ ] 没有物理计算错误
- [ ] 数学验证正确（钳制、衰减、快照）

**📝 验收反馈**:

```
✅ PASS: "物理引擎验证成功，所有数学测试通过"
❌ FAIL: 列出失败的测试用例，Agent 修复
```

---

### **PHASE 4: Agent 生成命令系统**

**⏱️ 耗时**: 45 分钟  
**🤖 Agent 任务**:

```bash
# 生成配置和管理类:
1. src/main/java/com/example/halo/config/HaloConfig.java
2. src/main/java/com/example/halo/manager/HaloManager.java
3. src/main/java/com/example/halo/command/HaloConfigCommand.java
4. 更新 HaloMod.java: 注册命令

# 生成单元测试:
5. src/test/java/com/example/halo/command/HaloCommandTest.java
```

**参考文件**: `task-08-halo-config-cli.md`

**👤 人类检验**:

```bash
# 1. 编译
./gradlew build
# 预期: ✅ BUILD SUCCESSFUL

# 2. 运行测试
./gradlew test --tests "*HaloCommand*"
# 预期: ✅ 命令测试通过

# 3. 游戏内验证（最关键的可见性检验）
./gradlew runClient

# 在游戏内执行命令:
/halo list
# 预期输出: "Loaded halo definitions: ring_default"

/halo show @s ring_default
# 预期: "Halo ring_default shown on [player]"

/halo config linear-damping 0.5
# 预期: "Set linear-damping to 0.5"

/halo hide @s
# 预期: "Halo hidden"
```

**✅ 检验清单** (task-04 通过条件):

- [ ] `./gradlew build` 成功
- [ ] 命令测试通过
- [ ] `/halo list` 显示至少一个定义
- [ ] `/halo show @s ring_default` 命令识别
- [ ] `/halo config` 参数接受有效范围
- [ ] `/halo hide @s` 清理命令工作

**📝 验收反馈**:

```
✅ PASS: "命令系统完整，所有命令工作"
❌ FAIL: 说明哪些命令失败，Agent 修复
```

---

### **PHASE 5: Agent 生成生命周期管理**

**⏱️ 耗时**: 40 分钟  
**🤖 Agent 任务**:

```bash
# 生成生命周期管理:
1. src/main/java/com/example/halo/data/HaloEntityData.java
2. src/main/java/com/example/halo/lifecycle/EntityHaloTracker.java
3. src/main/java/com/example/halo/lifecycle/HaloEntityEventHandler.java
4. src/main/java/com/example/halo/lifecycle/HaloWorldSaveData.java
5. 更新 HaloInstance.java: 添加生命周期方法
6. 更新 HaloManager.java: 添加清理和跟踪

# 生成单元测试:
7. src/test/java/com/example/halo/lifecycle/EntityHaloTrackerTest.java
```

**参考文件**: `task-09-halo-lifecycle.md`

**👤 人类检验**:

```bash
# 1. 编译
./gradlew build
# 预期: ✅ BUILD SUCCESSFUL

# 2. 运行生命周期测试
./gradlew test --tests "*EntityHaloTracker*"

# 3. NBT 持久化测试（关键）
./gradlew runClient

# 在游戏内:
/halo show @s ring_default
# 看到光环显示

/save-all
# 手动保存

# 关闭游戏 (ESC → 保存并退出)

# 重新启动游戏
./gradlew runClient

# 预期: 光环仍在玩家头上（持久化成功）

# 4. 传送测试（快照行为）
/tp @s ~ ~10 ~
# 预期: 光环应该直接快照到新位置（无滑动）
```

**✅ 检验清单** (task-05 通过条件):

- [ ] `./gradlew build` 成功
- [ ] 生命周期测试通过
- [ ] 光环在游戏内显示
- [ ] `/save-all` 无错误
- [ ] 游戏重启后光环仍存在
- [ ] 传送后光环快照到新位置

**📝 验收反馈**:

```
✅ PASS: "生命周期管理完整，持久化与传送工作正常"
❌ FAIL: 说明具体问题（崩溃/不持久化/传送异常）
```

---

### **PHASE 6: Agent 生成渲染系统**

**⏱️ 耗时**: 60 分钟  
**🤖 Agent 任务**:

```bash
# 生成客户端渲染:
1. src/main/java/com/example/halo/render/HaloRenderer.java
2. src/main/java/com/example/halo/render/HaloClientManager.java
3. src/main/java/com/example/halo/render/HaloRenderListener.java
4. 更新 HaloModClient.java: 注册渲染监听器

# 生成单元测试（数学验证）:
5. src/test/java/com/example/halo/render/HaloRendererTest.java
   - 四元数转矩阵
   - 面向相机的广告牌计算
   - 帧差值插值（无跳帧）
```

**参考文件**: `task-10-halo-renderer.md`

**👤 人类检验**:

```bash
# 1. 编译（可能有新的 Fabric API 依赖）
./gradlew build
# 预期: ✅ BUILD SUCCESSFUL

# 2. 运行渲染测试（数学验证）
./gradlew test --tests "*HaloRenderer*"
# 预期: ✅ 渲染测试通过（四元数、插值等）

# 3. 视觉验证（最重要的）
./gradlew runClient

# 游戏内:
/halo show @s ring_default

# 预期视觉效果:
# ✅ 光环出现在玩家头上
# ✅ 光环面向摄像头（总是看得到贴图）
# ✅ 光环跟随玩家头部平滑移动
# ✅ 走动时没有卡顿或闪烁
# ✅ 60+ FPS 流畅

# 4. 交互测试
走动、转身、跳跃、看不同方向
# 预期: 光环始终平滑跟随，面向相机

# 5. 距离测试
走远（50+ 格）、走近
# 预期: 光环始终正确渲染，距离不影响效果

# 6. 配置实时更新
/halo config scale 2.0
# 预期: 光环立即放大（不需要重启）

/halo config linear-damping 0.1
# 预期: 光环跟随速度明显变快
```

**✅ 检验清单** (task-06 通过条件):

- [ ] `./gradlew build` 成功
- [ ] 渲染数学测试通过
- [ ] **光环可见**（出现在头上）
- [ ] **光环面向摄像头**（不是扁平的）
- [ ] **平滑移动**（无卡顿）
- [ ] **60+ FPS**（流畅）
- [ ] **配置实时生效**
- [ ] **距离不影响**（能看到很远的光环）

**📝 验收反馈**:

```
✅ PASS: "渲染系统完整，光环可见且流畅"
❌ FAIL: 描述视觉问题（不可见/卡顿/错误旋转/黑屏等）
```

---

### **PHASE 7: 人类集成验收**

**⏱️ 耗时**: 20 分钟  
**👤 人类最终验收**:

```bash
# 完整的端到端测试流程

# 1. 清理并重新构建整个项目
./gradlew clean build

# 2. 运行所有测试
./gradlew test

# 预期: ✅ 所有测试通过

# 3. 启动游戏
./gradlew runClient

# 4. 完整的游戏内测试序列

# 步骤 4.1: 基础命令
/halo list
# ✅ 看到: "ring_default"

/halo show @s ring_default
# ✅ 看到: "Halo ring_default shown on [你的角色名]"
# ✅ 看到: 光环出现在头上

# 步骤 4.2: 物理行为
走动 → 光环平滑跟随
转身 → 光环旋转跟随
跳跃 → 光环保持平滑动画

# 步骤 4.3: 配置系统
/halo config linear-damping 0.8
# ✅ 光环变得"懒"（跟随缓慢）

/halo config linear-damping 0.1
# ✅ 光环变得"灵敏"（跟随快速）

/halo config scale 0.5
# ✅ 光环缩小

/halo config scale 2.0
# ✅ 光环放大

# 步骤 4.4: 多实体测试（可选）
/halo show @e[type=armor_stand,limit=1] ring_default
# ✅ 光环出现在盔甲架上（如果有）

# 步骤 4.5: 清理
/halo hide @s
# ✅ 光环消失

# 步骤 4.6: 持久化
/save-all
# ✅ 保存无错误

关闭游戏

# 重新启动
./gradlew runClient
# ✅ 光环仍在玩家头上

# 步骤 4.7: 传送测试
/tp @s 0 100 0
# ✅ 光环直接快照到新位置（无滑动）
```

**✅ 最终检验清单**:

- [ ] `./gradlew test` 所有测试通过
- [ ] `./gradlew build` 无错误
- [ ] `/halo list` 显示定义
- [ ] `/halo show @s ring_default` 光环出现
- [ ] **光环平滑跟随**（关键指标）
- [ ] **物理阻尼工作**（配置改变效果明显）
- [ ] **配置实时生效**（无需重启）
- [ ] **60+ FPS**（流畅）
- [ ] **保存/重启持久化**（光环仍在）
- [ ] **传送快照**（不滑动）
- [ ] **无控制台错误**

**✅ 完成标志**:

```
如果所有项都通过 ✓，则系统完整可用！

🎉 VIBE CODING 完成！
    ├─ 物理系统 ✓
    ├─ 命令系统 ✓
    ├─ 持久化 ✓
    └─ 渲染系统 ✓
```

---

## 📊 执行时间线

| 阶段 | 任务       | 耗时 | 累计  | 状态      |
| ---- | ---------- | ---- | ----- | --------- |
| P0   | 项目初始化 | 30m  | 30m   | Agent     |
| P1   | 数据模型   | 45m  | 1h15m | Agent     |
| P2   | 服务器核心 | 30m  | 1h45m | Agent     |
| P3   | 物理引擎   | 45m  | 2h30m | Agent     |
| P4   | 命令系统   | 45m  | 3h15m | Agent     |
| P5   | 生命周期   | 40m  | 3h55m | Agent     |
| P6   | 渲染系统   | 60m  | 4h55m | Agent     |
| P7   | 集成验收   | 20m  | 5h15m | **Human** |

**总耗时**: ~5.25 小时

---

## 🎯 关键检验点速查表

**记住这 7 个命令**:

```bash
# 1. 编译（每个阶段）
./gradlew build

# 2. 测试（每个阶段）
./gradlew test

# 3. 游戏（可视化验证）
./gradlew runClient

# 4. 特定测试
./gradlew test --tests "*DampingPhysics*"
./gradlew test --tests "*HaloCommand*"
./gradlew test --tests "*EntityHaloTracker*"
./gradlew test --tests "*HaloRenderer*"

# 5. 清理（最终验收前）
./gradlew clean build

# 6. 游戏内关键命令
/halo list
/halo show @s ring_default
/halo config linear-damping 0.5
/halo hide @s

# 7. 最关键：看头上有没有光环
```

---

## ⚠️ 常见问题与快速修复

| 问题       | 症状                | 解决方案                                 |
| ---------- | ------------------- | ---------------------------------------- |
| 编译失败   | `gradle build` 报错 | 检查 Java 17+，`./gradlew clean` 重试    |
| 测试失败   | 单元测试不通过      | Agent 检查伪代码实现，补充测试覆盖       |
| 游戏崩溃   | 启动游戏闪退        | 检查日志，通常是 mixin 或 event 注册问题 |
| 光环不显示 | 游戏中看不到光环    | 验证 HaloRenderer 注册，检查渲染循环     |
| 光环闪烁   | 光环忽明忽暗        | 检查帧插值和渲染 Z-fighting              |
| 命令不工作 | `/halo` 命令无反应  | 验证命令注册，检查权限等级               |
| 持久化失败 | 重启游戏光环消失    | 检查 NBT 编码/解码，WorldSaveData 注册   |

---

## 🚀 快速开始命令（复制粘贴）

```bash
# === PHASE 0: Init ===
cd f:\Halo
./gradlew build

# === PHASE 1-6: Loop ===
# 对每个阶段:
./gradlew build
./gradlew test
# 然后视觉检验:
./gradlew runClient

# === PHASE 7: Final ===
./gradlew clean build
./gradlew test
# 在游戏内验收所有检验点
```

---

## 📝 记录检验结果

建议使用此模板记录每个阶段：

```
### PHASE X: [任务名称]
- ✅ 编译成功
- ✅ 测试通过
- ✅ 游戏启动
- ❓ [其他观察]
- 🔧 [需要修复的问题]
- ✅ 阶段通过 / ❌ 阶段失败

下一步: [下一个阶段]
```

---

**🎵 Ready to vibe code? Let's go!**
