# Halo 1.20.1 Forge Flash 兼容性测试方案

> 当前分支：`1.20.1-forge-flash`
>
> 目标环境：Minecraft **1.20.1**、Java **17**、原生 Forge。最低编译与运行基线为 Forge **47.4.0**，声明兼容范围为 **[47.4.0, 48)**，并使用 Forge **47.4.10** 推荐版做额外回归。
>
> 该 JAR 不依赖 Fabric Loader、Fabric API、Sinytra Connector 或 Forgified Fabric API，也不测试 Fabric 客户端与 Forge 服务端的跨加载器互通。

Flash 分支仅验证原生 Forge 运行所必需的功能与回归。架构优化、新功能、OptiFine、光影和其他模组兼容不是本次硬性门槛。

## 运行模式与测试矩阵

| 模式 | 客户端 | 服务端 | 预期行为 |
|------|----------|----------|----------|
| 单人 | Halo | 集成服务端 | 命令、渲染、动画和持久化完整工作 |
| 多人同步 | Halo | Halo | attach/remove 实时同步，late join 获得全量状态，重连与重启后持久化 |
| LOCAL | Halo | 未安装 Halo 的原版或 Forge 服务端 | 不拒绝连接，`/halo` 仅在客户端本地生效并可持久化 |
| 异构客户端 | 未安装 Halo | Halo | 不拒绝连接，服务端不发送 Halo 消息，双方不崩溃 |

| 优先级 | Minecraft | Forge | 用途 |
|----------|-----------|-------|------|
| P0 | 1.20.1 | **47.4.0** | 最低支持基线，通过全部硬性检查 |
| P0 | 1.20.1 | **47.4.10** | 官方推荐版回归，通过构建、启动与核心功能 |
| P2 | 1.20.1 | 其他 47.4.x | 声明范围内的可选抽样 |

## 自动检查

以 JDK 17 按顺序执行：

```powershell
./gradlew compileJava
./gradlew test
./gradlew build
./gradlew reobfJar
```

- [ ] 四个任务全部成功，不通过删除或禁用测试换取通过。
- [ ] 现有测试语义覆盖保留；事件断言使用 Forge END phase、注册幂等性与回调行为。
- [ ] 产物为 `halo-1.20.1-forge-1.2.1.jar`。
- [ ] JAR 包含 `META-INF/mods.toml`、`pack.mcmeta`、`halo.mixins.json`、`halo.refmap.json`、`assets/`、`data/` 和 `LICENSE`。
- [ ] 仓库无 Fabric import，JAR 中无 `fabric.mod.json`。
- [ ] `mods.toml` 声明 Minecraft `[1.20.1,1.20.2)`、Forge `[47.4.0,48)`、JavaFML `[47,48)`。
- [ ] Mixin 保留 `required=true`、`defaultRequire=1`；任何 target 或注入失败均是启动阻塞。

### Dedicated server 启动

分别在 Forge 47.4.0 和 47.4.10 执行 `runServer`，接受 EULA 后启动至日志出现 `Done`。

ForgeGradle 的 `runClient` 使用 `Dev1`/`Dev2` 开发账号；`runServer` 会在启动前将 `runForgeServer/server.properties` 配置为 `online-mode=false`、`enforce-secure-profile=false`，仅用于本机多人联调。正式部署的服务器仍应启用正版验证。

- [ ] 无客户端类被 dedicated server 加载。
- [ ] 无 `Mixin apply failed`、`ClassNotFoundException`、`NoSuchMethodError` 或网络通道注册错误。
- [ ] `halo_world_data` 能从 `DataStorage` 读写，旧 Fabric 世界的原有 NBT 键保持可读。

## 用户联调第一轮：客户端与单人

前置：Minecraft 1.20.1 + Forge 47.4.0 或 47.4.10，`mods/` 中仅安装当前 Halo Forge JAR，不安装 Fabric API 或 Connector。

1. 启动客户端并创建开启作弊的单人世界。
2. 输入 `/halo ` 并使用 Tab，验证 `list/show/hide/config/reload/save/active/inspect/dump/debug`。
3. 分别检查 ring、billboard、face_camera、发光、透明、启动/关闭动画和阻尼跟随。
4. 按 F3+T 重载资源，再次执行 `/halo list` 并验证定义已更新。

验收：无 Mixin/渲染/Shader/资源重载异常；命令树完整；光环无缺失纹理、黑块、错误透视或明显抖动。失败时回传 `logs/latest.log`、截图/录像、Forge 版本、命令与复现步骤。

当前结果（2026-08-21）：客户端及单人世界启动正常，`show`/`hide`、光环渲染和存档持久化已由用户验证通过；其余命令、资源重载与姿态组合继续按上述清单回归。

## 用户联调第二轮：状态与持久化

1. 给玩家和非玩家实体附加光环，执行 `/halo save`，退出并重进存档。
2. 验证实体卸载/重载、玩家死亡重生、普通传送和跨维度 snap。
3. 检查潜行、游泳、鞩翅、睡眠、隐形、第一/第三人称与相机旋转稳定性。

验收：存档重进和实体重载后状态不丢失；死亡、重生和传送不产生长距离滑行或旧位置残留；姿态与相机变化下头部锚点稳定。

## 用户联调第三轮：多人与异构环境

### A. 双方安装 Halo

1. 两个 Halo Forge 客户端连入 Halo dedicated server，验证实时 attach/remove、权限与渲染结果。
2. 第三个客户端 late join，验证全量同步；断开重连并重启服务端，验证持久化。
3. 执行需转发的 `/halo` 命令，确认无客户端命令递归、误拦截或签名校验踢出。

### B. Halo 客户端连入无 Halo 服务端

1. 分别连入未安装 Halo 的原版服务端和 Forge 服务端，验证不被 mod list 或网络通道拒绝。
2. 执行 `/halo list/show/hide/config/save`，验证 LOCAL 状态仅本地可见、可持久化，且不向服务端发送 Halo 自定义包。

### C. 无 Halo 客户端连入 Halo 服务端

1. 使用纯 Forge 1.20.1 客户端连入 Halo 服务端，验证进入、移动和重连正常。
2. 在服务端执行 Halo 命令，确认不向该客户端发送未知消息，且服务端无 channel absent 或断言崩溃。

当前结果（2026-08-21）：多人联调全部通过，包括双方安装 Halo、Halo 客户端连接未安装 Halo 的服务端，以及无 Halo 客户端连接 Halo 服务端；连接、命令、同步和缺失通道兼容均未发现异常。

## 测试结果记录模板

```text
Minecraft: 1.20.1
Forge: 47.4.0 / 47.4.10
Java: 17
Halo JAR: halo-1.20.1-forge-1.2.1.jar
模式: 单人 / 双方安装 / LOCAL / 无 Halo 客户端
结果: PASS / FAIL
通过项:
失败项:
复现步骤:
日志: logs/latest.log / crash-reports/...
截图或录像:
其他 mods 与资源包:
```

两个 Forge 基线均通过自动检查、dedicated server 启动、三轮用户联调，且无 Mixin target 失败后，可认定当前 Flash 端口达到“在 Forge 原版环境正常运行”的目标。
