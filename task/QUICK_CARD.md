# ⚡ Vibe Coding Quick Card

## 执行流程 (7 个阶段，5-6 小时)

```
Agent P0 (30m)      Agent P1 (45m)      Agent P2 (30m)      Agent P3 (45m)
├─ Project Init     ├─ Data Model       ├─ Server Core      ├─ Physics
└─ ✅ gradlew build └─ ✅ gradlew test  └─ ✅ gradlew build └─ ✅ DampingTest
        ↓                   ↓                   ↓                   ↓
    Human Check        Human Check         Human Check         Human Check
    "编译成功"          "JSON 正常"        "无错误启动"        "所有物理测试 PASS"
        ↓                   ↓                   ↓                   ↓

Agent P4 (45m)      Agent P5 (40m)      Agent P6 (60m)      Human P7 (20m)
├─ Commands         ├─ Lifecycle        ├─ Renderer          └─ FINAL CHECK
└─ ✅ /halo works   └─ ✅ 持久化        └─ ✅ 看到光环        ✅ 游戏内完整测试
        ↓                   ↓                   ↓                   ↓
    Human Check        Human Check         Human Check         所有项通过?
    "命令响应"          "重启保留"          "平滑渲染"
```

---

## 7 个魔法命令

```bash
# 1️⃣ 编译
./gradlew build                    # 总是先编译

# 2️⃣ 测试
./gradlew test                     # 单元测试

# 3️⃣ 特定测试
./gradlew test --tests "*Damping*"
./gradlew test --tests "*Command*"
./gradlew test --tests "*Tracker*"
./gradlew test --tests "*Render*"

# 4️⃣ 游戏启动（可视化）
./gradlew runClient                # 按 ESC 退出

# 5️⃣ 清理重建
./gradlew clean build              # 最终验收前

# 6️⃣ 游戏内命令
/halo list                         # 看定义
/halo show @s ring_default         # 显示光环 ⭐⭐⭐
/halo config linear-damping 0.5    # 配置
/halo hide @s                      # 隐藏

# 7️⃣ 关键验收
看头上有没有光环？有 = ✅ PASS
```

---

## 每个阶段的人类检验清单

### ✅ P0: 项目初始化 (30m)

```
编译结果:
  [ ] gradlew build 成功
  [ ] JAR 在 build/libs/
  [ ] 无错误

即可进入 P1 ✓
```

### ✅ P1: 数据模型 (45m)

```
验收:
  [ ] gradlew build 成功
  [ ] gradlew test 全过
  [ ] JSON 文件存在
  [ ] 游戏启动正常

即可进入 P2 ✓
```

### ✅ P2: 服务器核心 (30m)

```
验收:
  [ ] gradlew build 成功
  [ ] gradlew test 全过
  [ ] 服务器启动无错

即可进入 P3 ✓
```

### ✅ P3: 物理引擎 (45m) ⭐⭐⭐ 关键

```
验收 (最重要):
  [ ] gradlew build 成功
  [ ] gradlew test --tests "*Damping*" ✅ 4/4 PASS
      - testInstantSnap ✅
      - testDampingDecay ✅
      - testClampDistance ✅
      - testAngularDamping ✅

如果任何测试失败 ❌ 停止，让 Agent 修复

即可进入 P4 ✓
```

### ✅ P4: 命令系统 (45m)

```
验收:
  [ ] gradlew build 成功
  [ ] gradlew test 全过
  [ ] runClient 后:
      /halo list ➜ ring_default 出现
      /halo show @s ring_default ➜ "Halo shown"
      /halo config linear-damping 0.5 ➜ "Set to 0.5"
      /halo hide @s ➜ "hidden"

即可进入 P5 ✓
```

### ✅ P5: 生命周期 (40m)

```
验收:
  [ ] gradlew build 成功
  [ ] gradlew test 全过
  [ ] runClient 后:
      /halo show @s ring_default (光环出现)
      /save-all (无错误)
      关闭游戏
      重启: ./gradlew runClient
      光环仍在 ✓
      /tp @s ~ ~10 ~ (光环快照到新位置)

即可进入 P6 ✓
```

### ✅ P6: 渲染系统 (60m) ⭐⭐⭐ 最终可见

```
验收:
  [ ] gradlew build 成功
  [ ] gradlew test 全过
  [ ] runClient 后，最重要的视觉检验:
      ✅ 光环出现在头上
      ✅ 面向摄像头（能看到贴图）
      ✅ 走动时平滑跟随（无卡顿）
      ✅ 60+ FPS（流畅）
      ✅ /halo config scale 2.0 立即变大
      ✅ /halo config linear-damping 改变跟随速度

如果光环不可见或卡顿 ❌ 停止，让 Agent 修复

即可进入 P7 ✓
```

### ✅ P7: 最终集成 (20m)

```
完整端到端测试:
  [ ] gradlew clean build 成功
  [ ] gradlew test 100% 通过
  [ ] runClient 完整测试:
      /halo list ✓
      /halo show @s ring_default ✓ (光环出现)
      走动、转身、跳跃 ✓ (平滑)
      /halo config linear-damping 0.1 ✓ (灵敏)
      /halo config linear-damping 0.8 ✓ (懒)
      /halo config scale 0.5 ✓ (缩小)
      /halo config scale 2.0 ✓ (放大)
      /halo hide @s ✓ (消失)
      /save-all ✓ (保存)
      关闭→重启 ✓ (光环仍在)
      /tp @s 0 100 0 ✓ (快照)

所有项通过 = 🎉 系统完整！
```

---

## 如果某个阶段失败

```
❌ P3 失败 (物理测试不过)
  → Agent 检查 DampingPhysics.java 的数学实现
  → 修复后重新 gradlew test --tests "*Damping*"
  → 所有 4 个测试过了才继续

❌ P6 失败 (光环不可见)
  → Agent 检查:
     - HaloRenderer 是否注册
     - 渲染循环是否被调用
     - 纹理是否加载
  → 修复后重新 gradlew runClient
  → 看到光环才继续

❌ P7 失败 (某个命令无法工作)
  → Agent 检查命令注册
  → 修复后 gradlew build && gradlew test
  → 游戏内验证命令
  → 所有命令工作才完成
```

---

## 关键决策

### Q: 需要完成所有 task-00 到 task-06 吗？

**A: 是的！** 先完成 P0-P2（project init + data model + server core），然后才能做光环系统（P3+）

### Q: 可以跳过某个阶段吗？

**A: 不能！** 依赖链是严格的。P7 需要 P6，P6 需要 P5，etc.

### Q: 一次可以做多个阶段吗？

**A: 可以（但不推荐）** 如果 Agent 很快，Agent 可以连续生成多个阶段的代码。但人类验收仍需逐个检查。

### Q: 如何处理 Agent 无法修复的错误？

**A: 回滚并调整需求** 如果某个阶段反复失败，可能是需求设计有问题。回到该任务的 `.md` 文件，调整需求，重新让 Agent 生成。

---

## 性能目标

| 指标       | 目标   | 检验方法             |
| ---------- | ------ | -------------------- |
| 编译时间   | <30秒  | gradlew build        |
| 测试时间   | <10秒  | gradlew test         |
| 游戏启动   | <10秒  | gradlew runClient    |
| FPS        | 60+    | 在游戏中看帧率计数器 |
| 光环流畅度 | 无卡顿 | 走动时视觉检验       |

---

## 文件位置速查

```
f:\Halo\
├── task/
│   ├── VIBE_CODING_WORKFLOW.md ← 你在这里
│   ├── task-00-project-init.md
│   ├── task-01-data-model.md
│   ├── task-02-server-core.md
│   ├── task-07-damping-physics.md
│   ├── task-08-halo-config-cli.md
│   ├── task-09-halo-lifecycle.md
│   └── task-10-halo-renderer.md
│
└── src/
    ├── main/java/com/example/halo/   ← Agent 生成代码这里
    ├── test/java/com/example/halo/   ← 单元测试这里
    └── main/resources/               ← JSON 配置这里
```

---

## 立即开始！

```bash
# 1. 进入项目目录
cd f:\Halo

# 2. 让 Agent 开始 P0
# (告诉 Agent: "实现 task-00-project-init.md 的所有内容")

# 3. Agent 完成后，你运行:
./gradlew build

# 4. 检查是否通过 (预期: BUILD SUCCESSFUL)

# 5. 如果通过，进入 P1
# 如果失败，让 Agent 修复

# 6. 重复步骤 2-5 直到 P7

🎵 Vibe coding 开始！
```

---

**总耗时**: 5-6 小时  
**成功标志**: 游戏内执行 `/halo show @s ring_default` 后看到头上有光环  
**推荐**: 一次一个阶段，中间不间断
