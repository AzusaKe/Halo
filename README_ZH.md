<h1>
  <img src="src/main/resources/assets/halo/textures/halo/ring.png" alt="封面" height="40" style="vertical-align:middle;margin-right:12px">
  Halo Mod（光环模组）
</h1>

![许可证](https://img.shields.io/badge/license-MIT-blue.svg)
![MC版本](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)
![模组加载器](https://img.shields.io/badge/Mod%20Loader-Fabric-orange.svg)

中文 | [English](README.md)

## 目录

- [Halo Mod（光环模组）](#halo-mod光环模组)
  - [目录](#目录)
  - [简介](#简介)
  - [特性](#特性)
  - [安装](#安装)
  - [使用方法](#使用方法)
    - [命令](#命令)
    - [自定义光环定义](#自定义光环定义)
  - [从源码构建](#从源码构建)
    - [前置条件](#前置条件)
    - [构建](#构建)
    - [运行测试](#运行测试)
    - [运行客户端/服务端](#运行客户端服务端)
  - [项目结构](#项目结构)
  - [贡献](#贡献)
  - [许可证](#许可证)

<a id="简介"></a>

## 简介

**Halo** 是一个装饰性模组，为原版MC实体添加了“光环”这一外观。光环能够平滑地跟随实体头部运动，且支持完全通过命令配置——无需 GUI。
目前仅能在1.20.1 的 Fabric 环境中使用。

> **项目仍处于早期开发阶段，功能和性能可能不稳定。欢迎提交 Issue 和 Pull Request 来帮助改进！**

> 光环采用帧率无关的方式进行离散位置跟随，可以配置跟随速度和最大距离钳制。并在传送后重置位置

<a id="特性"></a>

## 特性

- [x] **命令驱动**：完整的 `/halo` 命令树，支持 Tab 补全——从聊天栏即可完成光环的挂载、移除、配置、查看和列出。详情见下文。
- [x] **平滑衰减物理**：位置和旋转的帧率无关指数衰减。可配置线性/角度的跟随速度、最大距离钳制。
- [x] **持久化**：通过实体 NBT 和世界持久状态，光环在世界重载和服务器重启后依然保留。实体加载时自动恢复。
- [x] **传送感知**：当实体传送（或跨维度）时，光环瞬间跳到新位置——不会在地图上滑过去。
- [x] **发光效果**：每个光环可带有可选的可加性混合发光层，带有脉冲式 alpha 动画。
- [x] **动画支持**：光环定义支持位置动画曲线（振荡、线性、恒定）和旋转动画曲线（持续旋转等）。**此功能仍在规划中，预计将添加模型支持和更多动画**
- [x] **运行时配置**：衰减因子、最大距离、缩放、位置偏移和旋转偏移均可通过 `/halo config` 实时修改。**注意，目前尚无法针对特定个体和光环配置上述参数，如有需求请前往单个光环定义文件（json格式）手动调整**
- [x] **资源包友好**：光环定义为 JSON 文件，存放在 `assets/<namespace>/halo_definitions/` 目录下。通过资源包或数据包添加新光环，运行 `/reload` 即可生效。**数据包和资源包的结构尚未确定**
- [x] **多层形状**：除了单个公告板，光环还可使用 `MultiBillboardShape` 实现分层四边形效果。**即将扩增更多自定义内容，敬请期待**
- [x] **距离裁剪**：超过相机 1000 格外自动跳过渲染以优化性能。

<a id="安装"></a>

## 安装

1. 为 Minecraft 1.20.1 安装 [Fabric Loader](https://fabricmc.net/use/)。
2. 下载 [Fabric API](https://modrinth.com/mod/fabric-api)（1.20.1 用 0.92.0+ 版本）。
3. 从 [Releases](https://github.com/AzusaKe/Halo/releases) 页面下载最新的 **Halo** 模组 JAR 文件。
4. 将两个 JAR 文件放入 Minecraft 安装目录的 `mods` 文件夹中。
5. 使用 Fabric 配置文件启动 Minecraft。

> **多人游戏**：在服务端和所有需要看到光环的客户端都安装此模组。光环状态（哪个实体有哪个光环）由服务端权威管理；渲染在客户端进行。

<a id="使用方法"></a>

## 使用方法

<a id="命令"></a>

### 命令

所有命令需要 2 级权限（管理员）。使用 `/halo` 配合 Tab 补全探索可用的子命令。

| 命令                                   | 描述                                         |
| -------------------------------------- | -------------------------------------------- |
| `/halo list`                           | 列出所有已加载的光环定义                     |
| `/halo dump`                           | 详细输出光环定义，包含形状、动画和衰减信息   |
| `/halo show <实体> <定义>`             | 将光环挂载到实体上                           |
| `/halo hide <实体>`                    | 移除实体的光环                               |
| `/halo active`                         | 列出所有当前佩戴光环的实体                   |
| `/halo inspect <实体>`                 | 查看指定实体的光环运行时状态详情             |
| `/halo config linear-damping <0-1>`    | 设置线性跟随速度（0 = 不跟随，1 = 瞬间跟随） |
| `/halo config angular-damping <0-1>`   | 设置角度跟随速度                             |
| `/halo config max-linear-distance <n>` | 设置硬夹断前最大距离（格）                   |
| `/halo config max-angular-degrees <n>` | 设置最大角度偏差（度）                       |
| `/halo config scale <0.1-5.0>`         | 设置统一缩放倍率                             |
| `/halo save`                           | 同步光环数据到世界持久化存储并触发 save-all  |
| `/halo debug <true/false>`             | 开关传送/吸附调试日志输出到聊天栏            |
| `/halo reload`                         | 提示使用 `/reload` 来重新加载光环定义        |
| `/halo config reload`                  | 从资源包/数据包重新加载光环定义              |

**示例：**

```mcfunction
# 将默认环形光环挂载到自己身上
/halo show @s ring_default

# 将光环挂载到另一个玩家
/halo show Azusa_Ke ring_default

# 移除自己的光环
/halo hide @s

# 让光环响应更快
/halo config linear-damping 0.3
/halo config angular-damping 0.2

# 让光环渲染得更大
/halo config scale 1.5
```

<a id="自定义光环定义"></a>

### 自定义光环定义

> **该条目即将修改**

光环定义是存放在 `assets/<namespace>/halo_definitions/`（资源包）或 `data/<namespace>/halo_definitions/`（数据包）下的 JSON 文件。

**定义示例**（`ring_default.json`）：

```json
{
  "id": "halo:ring_default",
  "shape": {
    "type": "billboard",
    "texture": "halo:textures/halo/ring.png",
    "size": [0.5, 0.5]
  },
  "animation": {
    "positionCurves": [
      { "type": "oscillate", "axis": "Y", "amplitude": 0.05, "frequency": 1.0 }
    ],
    "rotationCurves": [{ "type": "spin", "axis": "Z", "speed": 30.0 }]
  },
  "positioning": {
    "offset": [0.0, 0.2, 0.5],
    "scale": 1.0
  },
  "damping": {
    "linearFactor": 0.15,
    "angularFactor": 0.1,
    "maxLinearDistance": 1.0,
    "maxAngularDegrees": 180.0
  }
}
```

| 字段                        | 描述                                                        |
| --------------------------- | ----------------------------------------------------------- |
| `id`                        | 唯一标识符，格式为 `命名空间:名称`                          |
| `shape.type`                | `billboard`（单个四边形）或 `multi_billboard`（多层四边形） |
| `shape.texture`             | 纹理路径，相对于 `assets/`                                  |
| `shape.size`                | `[宽度, 高度]`，单位为格                                    |
| `animation.positionCurves`  | 位置动画数组（`oscillate`、`linear`、`constant`）           |
| `animation.rotationCurves`  | 旋转动画数组（`spin` 等）                                   |
| `positioning.offset`        | `[X, Y, Z]` 相对实体头部的偏移量（格）                      |
| `positioning.scale`         | 默认缩放倍率                                                |
| `damping.linearFactor`      | 20 TPS 下每 tick 的线性插值速度（0 = 不跟随，1 = 瞬间跟随） |
| `damping.angularFactor`     | 角度插值速度（范围同上）                                    |
| `damping.maxLinearDistance` | 硬夹断最大距离（格）                                        |
| `damping.maxAngularDegrees` | 最大角度偏差（度）                                          |

> 添加或修改光环定义后，运行 `/reload`（或 `/halo config reload`）重新加载。

<a id="从源码构建"></a>

## 从源码构建

<a id="前置条件"></a>

### 前置条件

- **JDK 17** — Minecraft 1.20.1 所需
- 互联网连接（Gradle 从 Maven 仓库下载依赖）

<a id="构建"></a>

### 构建

```bash
git clone https://github.com/AzusaKe/Halo.git
cd Halo
./gradlew build
```

编译好的 JAR 文件位于 `build/libs/halo-0.1.0.jar`。

<a id="运行测试"></a>

### 运行测试

```bash
./gradlew test
```

<a id="运行客户端服务端"></a>

### 运行客户端/服务端

```bash
# 启动带模组的 Minecraft 客户端
./gradlew runClient

# 启动带模组的专用服务端
./gradlew runServer
```

<a id="项目结构"></a>

## 项目结构

```
src/main/
  java/com/example/halo/
    HaloMod.java              — 模组初始化器（服务端入口）
    HaloModClient.java         — 客户端初始化器（客户端入口）
    animation/                 — 动画曲线（Linear、Oscillate、Constant）
    command/                   — /halo Brigadier 命令树
    config/                    — 运行时 HaloConfig（衰减、缩放、偏移）
    data/                      — HaloDefinition、HaloInstance、HaloEntityData 等
    json/                      — JSON 反序列化器和资源加载器
    lifecycle/                 — EntityHaloTracker、HaloWorldSaveData、事件处理器
    manager/                   — HaloManager（单例，管理所有活跃光环）
    mixin/                     — EntityTeleportMixin、LivingEntityDataMixin
    physics/                   — DampingPhysics、HaloDampingState、HaloTickHandler
    render/                    — HaloRenderer、HaloClientManager、HaloRenderListener
    server/                    — HaloServerEvents、ServerTickHandler
    shape/                     — BillboardShape、MultiBillboardShape、GlowLayer
  resources/
    fabric.mod.json            — 模组元数据（入口点、Mixin、依赖）
    halo.mixins.json            — Mixin 配置
    assets/halo/
      halo_definitions/        — JSON 光环定义文件
      textures/halo/           — 光环与发光纹理
```

<a id="贡献"></a>

## 贡献

欢迎对 Halo 进行贡献！如果你有想法、建议或想报告 Bug，请在 [GitHub 仓库](https://github.com/AzusaKe/Halo) 提交 Issue。如果你想贡献代码，请 Fork 仓库并提交 Pull Request。

- **开发环境**：Minecraft 1.20.1 + Fabric Loader 0.15+
- **IDE**：推荐 IntelliJ IDEA（配合 Minecraft Development 插件）或 VS Code

<a id="许可证"></a>

## 许可证

本项目采用 MIT 许可证 — 详情请参阅 [LICENSE](LICENSE) 文件。
