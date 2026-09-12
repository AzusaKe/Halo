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
| Halo 干净递归克隆 | 从本地已提交的 `1.20.1-fabric` 全新克隆，core 从 GitHub 按 gitlink 检出；`gradlew build -Prelease=true` 成功 |
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
- 完全退出客户端并重启，重新连接同一原版服务器后，本地光环自动恢复，无需重新输入佩戴命令。

## 用户复杂环境验收

用户在既有复杂模组环境中安装本次成品并实际游玩约 10 分钟，反馈如下：

- 未见崩溃；EMF、YSM、Iris 适配正常。
- 动画、隐身与睡眠行为未见异常。
- 帧率未见明显下降，光影未见异常。
- 持久化正常。

这些结果由用户实际游戏测试提供，补充了前述隔离环境验证；具体模组清单、版本和光影预设未记录。
结合自动检查和已有游戏验证，本次重构在已测试环境中验收通过。

锚点 API v2 再次核对：五个公开 API 源文件与重构前基线内容一致，包名、签名和调用方式未变；
已针对旧 API 编译的调用方再次仅使用最终 Halo JAR 运行通过。现有 API v2 接入无需修改或重新编译。

## 验证范围与历史问题

- 双客户端测试中，第二个客户端在窗口切换期间发生 `glfw.dll` 原生访问异常。崩溃栈位于 LWJGL/GLFW，
  未定位到 Halo Java 代码。用户后续复杂环境约 10 分钟未见崩溃；保留该独立开发环境事件，未将其标记为已定位修复。
- 用户已验证当前使用的 EMF/YSM/Iris 和光影组合；这不覆盖所有历史发布包及全部组合。
- 固定时钟回放覆盖大坐标、动画连续性、生命周期和内置定义输出，用户补充了动画、隐身、睡眠与帧率观察。
  未进行完整的帧率、视角、朝向模式和光影组合穷举，也未进行量化性能基准。
- 开发联调曾因重写正在运行的 core JAR 出现 ZIP 读取异常；每次启动使用独立 JAR 副本后，后续单人游戏和专用服测试未再出现该异常。

后续发布或恢复其他适配版本时，按 `core-refactor.md` 的接入清单验证其实际目标环境。
