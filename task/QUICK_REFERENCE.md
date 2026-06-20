# 快速参考卡片 - Agent 启动顺序

## 📋 就这个顺序

```
AGENT-1 (基础, 12-14天)
  ├─ task-00: ./gradlew build ✓
  ├─ task-01: /halo dump ✓
  ├─ task-02: server tick ✓
  ├─ task-03: sync ✓
  ├─ task-04: render ✓
  ├─ task-05: glow ✓
  └─ task-06: config ✓
         ↓
AGENT-2 (后端, 6-8天)
  ├─ task-07: 单测全绿 ✓
  ├─ task-08: /halo show ✓
  └─ task-09: 无泄漏 ✓
         ↓
AGENT-3 (渲染, 9-12天)
  ├─ task-10: 50halos @60FPS ✓
  ├─ task-11: 动画 @60FPS ✓
  ├─ task-12: 平滑 ✓
  ├─ task-13: GUI ✓
  ├─ task-14: 模型 ✓
  └─ task-15: 命令 ✓
         ↓
LEAD (验收, 1-2天)
  └─ verify-05: E2E test ✓
```

---

## 🧪 人类必做的检验 (每个 task)

### 最少检验清单

```bash
# 每个 task 完成后，你做这个:

# 1. 编译
./gradlew build
# ✅ BUILD SUCCESSFUL

# 2. 单测
./gradlew test
# ✅ All tests passed

# 3. 游戏
./gradlew runClient
# ✅ 游戏启动，无崩溃

# 4. 功能测试 (task 相关)
# 例如 task-07: 检查物理单测
# 例如 task-08: /halo show 工作
# 例如 task-10: FPS >= 60

# 5. 日志检查
type logs\latest.log | findstr "ERROR"
# ✅ 无输出
```

---

## 🚀 给 Agent 的命令模板

```
你好！

请完成任务: task-07-damping-physics.md

按照文档中的要求:
1. 创建所有必要的 Java 文件
2. 实现所有方法
3. 编写单元测试，覆盖所有场景
4. 确保 ./gradlew build 成功
5. 确保 ./gradlew test 全过

完成后提交 PR 到 develop 分支
PR 标题: [task-07] Damping physics engine
PR 描述中附带:
- 单元测试通过的截图
- 关键代码片段
- 性能基准 (如有)

等待我的检验。
```

---

## ✅ / ❌ 通过/失败 标准

### Task-00 ~ Task-06

| 检验项   | ✅ 通过            | ❌ 失败  |
| -------- | ------------------ | -------- |
| 编译     | `BUILD SUCCESSFUL` | ERROR    |
| 单测     | 所有绿             | 任何红   |
| 游戏启动 | 无崩溃             | 有崩溃   |
| 日志     | 无 ERROR           | 有 ERROR |
| 功能     | 按预期             | 不按预期 |

### Task-07 (关键!)

| 检验项    | ✅ 通过 | ❌ 失败     |
| --------- | ------- | ----------- |
| testSnap  | 绿      | 红          |
| testDecay | 绿      | 红          |
| testClamp | 绿      | 红          |
| 编译      | 无警告  | 有警告/错误 |

### Task-10 (关键!)

| 检验项       | ✅ 通过    | ❌ 失败     |
| ------------ | ---------- | ----------- |
| 50 halos FPS | >= 60 稳定 | < 50 或抖动 |
| 面向相机     | 总是面向   | 不面向      |
| 光滑跟随     | 无卡顿     | 有卡顿/延迟 |
| 发光         | 可见       | 不可见      |

---

## ⏰ 时间轴

```
Week 1: Agent-1 (task-00~06)
Week 2: Agent-2 (task-07~09)
Week 3: Agent-3 (task-10~15)
Week 4: Verify + 修复

总计: 3-4 周
```

---

## 📞 Issue 反馈模板 (检验失败时)

```
Task-[XX] 检验失败

检验步骤: ./gradlew build

错误信息:
[粘贴错误日志]

预期: [什么应该发生]

实际: [什么没发生]

截图: [如果有]
```

---

## 🎯 关键检验点 (非常重要)

这 5 个 task 检验最严格:

### ✓ Task-00

```bash
./gradlew build
# 必须: BUILD SUCCESSFUL
# 必须: jar 文件生成
```

### ✓ Task-01

```bash
/halo dump
# 必须: Loaded halo definitions: 1
# 必须: halo:ring_default 显示
```

### ✓ Task-07 (物理)

```bash
./gradlew test --tests "*DampingPhysicsTest*"
# 必须: 所有物理单测绿色
# 必须: testSnap, testDecay, testClamp 都过
```

### ✓ Task-10 (渲染)

```
F3 看 FPS
# 必须: 50 halos >= 60 FPS
# 必须: 平滑无卡顿
# 必须: 光环可见且面向相机
```

### ✓ Verify-05 (最终)

```bash
# 完整场景:
/reload
/halo list
/halo show @s ring_default
/tp @s ~ ~10 ~
/halo config linear-damping 0.8
/halo hide @s
/save-all
[重启游戏+加载]
# 必须: 光环仍在，0 错误
```

---

## 🛑 如果失败怎么办

1. 反馈给 Agent
2. Agent 修复
3. 重新提交 PR (更新同一个 or 新 PR)
4. 人类重新检验
5. **不要跳过失败的 task！**

---

## 💾 需要保存的东西

```
每个检验通过后，保存:

✓ latest.log (性能数据)
✓ 单元测试结果截图
✓ FPS 数据 (task-10+)
✓ 内存占用数据 (task-09+)
✓ PR 链接
✓ Commit hash

用于最后的总结报告。
```

---

## 🚀 立即开始

```bash
# 1. 发给 Agent-1:

你好！请执行 task-00

阅读: f:\Halo\task\task-00-project-init.md

完成后提交 PR:
标题: [task-00] Project structure init
描述: 包含 ./gradlew build 的成功截图

# 2. 等待 Agent-1 的 PR

# 3. 执行上面的"最少检验清单"

# 4. 通过 → merge → 准备 Agent-1 的 task-01

# 5. 重复
```

---

**最后: 祝你好运!** 🎉

问题？看完整指南: `VIBE_CODING_EXECUTION_GUIDE.md`
