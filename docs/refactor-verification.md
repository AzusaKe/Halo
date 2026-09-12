# Halo 1.20.1 Fabric 重构验收记录

日期：2026-09-12。基线为 Halo `b30dad7`；未迁移其他分支。
Core：`AzusaKe/HaloCore` 的 `v1.3.0`，提交 `a12100e48e0e71d07e96c7b7dc6915570ae48861`。
运行环境：Windows 11、Java 17.0.11、Minecraft 1.20.1、Fabric Loader 0.15.11、Fabric API 0.92.0。

## 自动验证

| 检查 | 结果 |
| --- | --- |
| Core JUnit | 239 项通过，无失败或跳过 |
| Halo 适配层 JUnit | 82 项通过、1 项跳过，无失败 |
| Core 独立性 | 依赖精确为 Gson 2.10 / JOML 1.10.5；生产字节码无 Minecraft/Fabric/Mixin/LWJGL/Blaze3D 引用 |
| 独立远端克隆 | 从 HaloCore `v1.3.0` 重新克隆，执行 `gradlew build` 成功 |
| Core GitHub CI | Ubuntu / Java 17 独立构建通过：[运行记录](https://github.com/AzusaKe/HaloCore/actions/runs/34682673211) |
| 旧锚点 API v2 | 调用方先针对冻结的旧 API 编译，再仅使用最终 Halo JAR 运行，成功 |
| 格式和协议 | 旧格式世界 NBT 往返、配置/本地文件、固定字节的全量与增量佩戴包检查通过 |
| JAR 内容 | 全部 core 类平铺且仅一份；API v2 直接可见；未打入 Minecraft/Gson/JOML 类；包含提交来源元数据 |

总计 322 项 JUnit，321 项通过、1 项跳过。唯一跳过项为 `YsmReleaseSignatureTest`，需要通过
`HALO_YSM_TEST_JAR` 提供外部 YSM 2.6.5 发布包。测试数量与旧基线不同，因为移除了只检查旧单例/空 heartbeat
或私有状态的测试，并补充真实存储、协议、假适配器及完整帧流水线测试。

## 游戏中已验证

测试仅使用 `.local` 下隔离世界和配置，未操作已有 `run` 世界。

- 专用服务器启动、show/hide/inspect、实体 NBT 镜像、save-all、资源重载和重启后的佩戴恢复。
- 非玩家死亡后活动记录和持久佩戴同时移除，inspect 报告无佩戴关系。
- 两个客户端接入同一专用服务器，两个视角均可看到实体光环；其中一个客户端进一步完成隐藏、重新挂载和重载的视觉检查。
- 专用服务器类加载日志未加载 `HaloModClient`、`HaloRenderer`、`HaloClientState` 或 `ClientRuntime`。
- 单人游戏从存档恢复佩戴、指令隐藏并完成关闭动画、权杖界面选择后重新挂载。
- 单人运行配置桥接：`/halo config scale 2` 立即使客户端光环缩放生效。
- 原版专用服务器（没有 Fabric/Halo）上执行本地 `/halo show @s halo:ring_default`，第三人称正常绘制，旧格式本地佩戴 JSON 写入成功。

## 限制与待复测项

- 双客户端测试中，第二个客户端在窗口切换期间发生 `glfw.dll` 原生访问异常。崩溃栈位于 LWJGL/GLFW，
  未定位到 Halo Java 代码；尚不能据此宣称双客户端长时间运行稳定。需要在目标图形环境继续复测。
- EMF/YSM/Iris 的数学、版本门控及捕获有效期测试通过，但本次没有完成各发布包组合的实际游戏验收。
- 固定时钟回放覆盖大坐标、动画连续性、生命周期和内置定义输出；未完成所有实际帧率、视角、睡眠/隐身、
  朝向模式和光影组合的人工视觉矩阵。单元测试通过不等同于所有视觉组合已验收。
- 开发联调曾因重写正在运行的 core JAR 出现 ZIP 读取异常；每次启动使用独立 JAR 副本后，后续单人游戏和专用服测试未再出现该异常。

发布或恢复其他适配版本时，应结合 `core-refactor.md` 的接入清单补齐上述实际环境验证。
