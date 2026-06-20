# ✅ Vibe Coding 检验清单

## Phase-by-Phase 验收表

复制此表格，在你验收代码时填写。

---

## PHASE 0: Project Init

```
日期: ________
开始时间: ____:____
Agent 生成完成: YES / NO

检验步骤:
□ 文件检查
  □ settings.gradle 存在
  □ build.gradle 存在
  □ fabric.mod.json 存在
  □ HaloMod.java 存在
  □ HaloModClient.java 存在

□ 编译检查
  命令: ./gradlew build
  结果: ✅ SUCCESS / ❌ FAIL
  错误信息 (如有):
  _________________________________________

□ 验收
  ✅ 通过 → 进入 PHASE 1
  ❌ 失败 → 让 Agent 修复

完成时间: ____:____
实际耗时: ____ 分钟
```

---

## PHASE 1: Data Model

```
日期: ________
开始时间: ____:____

检验步骤:
□ 编译检查
  ./gradlew build
  结果: ✅ / ❌

□ 单元测试
  ./gradlew test
  结果: ✅ / ❌
  失败测试 (如有):
  _________________________________________

□ JSON 有效性
  cat src/main/resources/assets/halo/halo_definitions/ring_default.json
  内容检查:
  □ 包含 "id" 字段
  □ 包含 "shape" 字段
  □ 包含 "animation" 字段
  □ 包含 "positioning" 字段
  □ 包含 "damping" 字段

□ 游戏启动 (可选)
  ./gradlew runClient
  游戏启动: ✅ / ❌
  (按 ESC 退出)

□ 验收
  ✅ 通过 → 进入 PHASE 2
  ❌ 失败 → 让 Agent 修复

完成时间: ____:____
实际耗时: ____ 分钟
```

---

## PHASE 2: Server Core

```
日期: ________

检验步骤:
□ ./gradlew build
  结果: ✅ / ❌

□ ./gradlew test
  结果: ✅ / ❌

□ 验收
  ✅ 通过 → 进入 PHASE 3
  ❌ 失败 → 让 Agent 修复

完成时间: ____:____
实际耗时: ____ 分钟
```

---

## PHASE 3: Damping Physics ⭐⭐⭐ 最关键

```
日期: ________

检验步骤:
□ 编译
  ./gradlew build
  结果: ✅ / ❌

□ 物理测试（必须全过）
  ./gradlew test --tests "*DampingPhysicsTest"

  结果:
  □ testInstantSnap               ✅ PASSED / ❌ FAILED
  □ testDampingDecay              ✅ PASSED / ❌ FAILED
  □ testClampDistance             ✅ PASSED / ❌ FAILED
  □ testAngularDamping            ✅ PASSED / ❌ FAILED

  失败详情 (如有):
  _________________________________________

□ 完整测试
  ./gradlew test
  总体结果: ✅ / ❌

□ 验收
  ✅ 4 个物理测试全过 → 进入 PHASE 4
  ❌ 任何测试失败 → 停止，让 Agent 修复

完成时间: ____:____
实际耗时: ____ 分钟
```

---

## PHASE 4: CLI Commands

```
日期: ________

检验步骤:
□ 编译 & 测试
  ./gradlew build && ./gradlew test
  结果: ✅ / ❌

□ 游戏内命令验证
  ./gradlew runClient 后:

  /halo list
  □ 看到 "ring_default" 或其他定义

  /halo show @s ring_default
  □ 命令执行无错误
  □ 输出: "Halo ring_default shown on [你的名字]"

  /halo config linear-damping 0.5
  □ 命令执行无错误
  □ 输出: "Set linear-damping to 0.5"

  /halo hide @s
  □ 命令执行无错误

□ 验收
  ✅ 所有命令工作 → 进入 PHASE 5
  ❌ 某个命令失败 → 让 Agent 修复

完成时间: ____:____
实际耗时: ____ 分钟
```

---

## PHASE 5: Lifecycle

```
日期: ________

检验步骤:
□ 编译 & 测试
  ./gradlew build && ./gradlew test
  结果: ✅ / ❌

□ 持久化测试
  ./gradlew runClient

  /halo show @s ring_default
  □ 光环出现

  /save-all
  □ 保存成功

  关闭游戏 (ESC → Save and Quit)

  重新启动:
  ./gradlew runClient

  □ 光环仍在玩家头上
  (持久化成功!)

□ 传送测试
  /tp @s 0 100 0
  □ 光环快速跳到新位置
  □ 无滑动效果

□ 验收
  ✅ 持久化和传送都工作 → 进入 PHASE 6
  ❌ 任何失败 → 让 Agent 修复

完成时间: ____:____
实际耗时: ____ 分钟
```

---

## PHASE 6: Renderer ⭐⭐⭐ 最后的可见性

```
日期: ________

检验步骤:
□ 编译 & 测试
  ./gradlew build && ./gradlew test
  结果: ✅ / ❌

□ 游戏启动
  ./gradlew runClient
  启动: ✅ / ❌

□ 光环可见性 (关键!)
  /halo show @s ring_default

  能看到光环吗?
  □ ✅ 是 (继续)
  □ ❌ 否 (停止，让 Agent 修复)

□ 光环外观检查
  □ 面向摄像头 (不是扁平的)
  □ 是正确的贴图
  □ 大小合理
  □ 颜色正确 (如果有发光)

□ 平滑性测试
  走动、转身、跳跃:
  □ 光环平滑跟随
  □ 无卡顿
  □ 无闪烁
  □ 60+ FPS (看任务栏帧率)

□ 配置实时更新测试
  /halo config linear-damping 0.1
  □ 光环立即变灵敏

  /halo config linear-damping 0.8
  □ 光环立即变懒

  /halo config scale 2.0
  □ 光环立即变大

  /halo config scale 0.5
  □ 光环立即变小

□ 距离测试
  走远 (50+ 格):
  □ 光环仍可见

  走近 (1 格):
  □ 光环仍可见（可能穿过头）

□ 验收
  ✅ 光环可见、平滑、所有功能工作 → 进入 PHASE 7
  ❌ 光环不可见或卡顿 → 停止，让 Agent 修复渲染

完成时间: ____:____
实际耗时: ____ 分钟
```

---

## PHASE 7: Final Integration ✨

```
日期: ________

检验步骤:
□ 完整构建
  ./gradlew clean build
  结果: ✅ / ❌

□ 完整测试
  ./gradlew test
  所有测试: ✅ PASSED / ❌ FAILED
  失败数: ____

□ 游戏完整演示
  ./gradlew runClient

  执行此完整序列:

  1️⃣ 基础命令
     /halo list
     □ 看到定义

     /halo show @s ring_default
     □ 光环出现在头上

  2️⃣ 物理行为
     走动 → 光环跟随
     □ 平滑无卡

     转身 → 光环旋转
     □ 正确跟随

     跳跃 → 光环继续
     □ 无中断

  3️⃣ 配置系统
     /halo config linear-damping 0.8
     □ 光环变慢

     /halo config linear-damping 0.1
     □ 光环变快

     /halo config scale 2.0
     □ 光环变大

     /halo config scale 0.5
     □ 光环变小

  4️⃣ 传送与持久化
     /save-all
     □ 保存成功

     关闭游戏

     重启游戏
     □ 光环仍在

     /tp @s 0 100 0
     □ 光环快照到新位置

  5️⃣ 清理
     /halo hide @s
     □ 光环消失

□ 最终验收
  所有项都通过?

  ✅ 是 → 🎉 系统完整！
  ❌ 否 → 说明哪项失败: _____________

完成时间: ____:____
实际耗时: ____ 分钟
```

---

## 🎯 总体统计

```
项目名称: Halo 光环系统 Minecraft Mod
开始日期: __________
完成日期: __________

各阶段耗时:
┌─────────────────────────────────┐
│ Phase 0: ____ 分钟              │
│ Phase 1: ____ 分钟              │
│ Phase 2: ____ 分钟              │
│ Phase 3: ____ 分钟 ⭐⭐⭐      │
│ Phase 4: ____ 分钟              │
│ Phase 5: ____ 分钟              │
│ Phase 6: ____ 分钟 ⭐⭐⭐      │
│ Phase 7: ____ 分钟 ✨           │
├─────────────────────────────────┤
│ 总耗时:    ____ 分钟 (计划 330m) │
└─────────────────────────────────┘

成功指标:
□ 编译成功率: ____ %
□ 单元测试通过率: ____ %
□ 游戏内功能完整: ✅ / ❌
□ 性能指标 (FPS): ____ +

Agent 性能评估:
□ 代码质量: ⭐⭐⭐⭐⭐ / ⭐⭐⭐⭐ / ⭐⭐⭐
□ 文档完整度: ⭐⭐⭐⭐⭐ / ⭐⭐⭐⭐ / ⭐⭐⭐
□ 调试周期: ____ 次迭代
□ 建议改进: _____________________________________

最终评价:
_________________________________________________
```

---

## 📝 每个阶段的问题记录

```
PHASE __: ____________

问题描述:
_________________________________________________

错误信息:
_________________________________________________

原因分析:
_________________________________________________

解决方案:
_________________________________________________

是否影响后续阶段:
□ 是 - 需要回溯
□ 否 - 可继续

---
```

---

## 🎉 完成标志

```
[ ] 所有 7 个 Phase 验收通过
[ ] 无阻塞性 Bug
[ ] 系统功能完整
[ ] 性能达标 (60+ FPS)
[ ] 文档齐全

最终确认:

日期: __________
验收人: __________
签名: __________

🎵 Vibe Coding 完成！
```

---

**使用说明**:

- 打印此表格或在文本编辑器中填写
- 每个 Phase 前复制对应的模板
- 完成后保存，用作项目的验收文档
- 如有问题，参考 VIBE_CODING_WORKFLOW.md 的对应章节
