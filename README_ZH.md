<h1>
  <img src="src/main/resources/assets/halo/textures/halo/ring.png" alt="封面" height="40" style="vertical-align:middle;margin-right:12px">
  Halo Mod（光环模组）
</h1>

![许可证](https://img.shields.io/badge/license-MIT-blue.svg)
![MC版本](https://img.shields.io/badge/Minecraft-26.1--26.1.2-green.svg)
![模组加载器](https://img.shields.io/badge/Mod%20Loader-Fabric-orange.svg)

中文 | [English](README.md)

## 目录

- [目录](#目录)
- [简介](#简介)
- [特性](#特性)
- [未来将会添加的特性](#未来将会添加的特性)
- [安装](#安装)
  - [NeoForge](#neoforge)
- [使用方法](#使用方法)
  - [命令](#命令)
  - [YSM 兼容](#ysm-兼容)
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
目前支持 Minecraft 26.1 ~ 26.1.2 的 Fabric 环境（NeoForge 可通过 [Sinytra Connector](https://modrinth.com/mod/connector) 运行，渲染、持久化等核心功能已实测可用）。

> **项目仍处于早期开发阶段，功能和性能可能不稳定。欢迎提交 Issue 和 Pull Request 来帮助改进！**

> 光环采用帧率无关的方式进行离散位置跟随，可以配置跟随速度和最大距离钳制。并在传送后重置位置

<a id="特性"></a>

## 特性

- [x] **命令驱动**：完整的 `/halo` 命令树，支持 Tab 补全——从聊天栏即可完成光环的挂载、移除、配置、查看和列出。详情见下文。
- [x] **平滑衰减物理**：位置和旋转的帧率无关指数衰减。可配置线性/角度的跟随速度、最大距离钳制。
- [x] **持久化**：通过实体 NBT 和世界持久状态，光环在世界重载和服务器重启后依然保留。实体加载时自动恢复。
- [x] **传送感知**：当实体传送（或跨维度）时，光环瞬间跳到新位置——不会在地图上滑过去。
- [x] **发光效果**：`animation.glow` 动画直接驱动图元自身的自发光亮度（全亮度渲染）；将组的 `glowing` 设为 `false` 可让其图元跟随环境光照。
- [x] **动画支持**：光环定义支持位置动画曲线（振荡、线性、恒定）和旋转动画曲线（持续旋转等）。**此功能仍在规划中，预计将添加模型支持和更多动画**
- [x] **运行时配置**：衰减因子、最大距离、缩放、位置偏移和旋转偏移均可通过 `/halo config` 实时修改。**注意，目前尚无法针对特定个体和光环配置上述参数，如有需求请前往单个光环定义文件（json格式）手动调整**
- [x] **资源包友好**：光环定义为 JSON 文件，存放在 `assets/<namespace>/halo_definitions/` 目录下。通过资源包或数据包添加新光环，运行 `/reload` 即可生效。**数据包和资源包的结构尚未确定**
- [x] **多层形状**：除了单个公告板，光环还可使用 `MultiBillboardShape` 实现分层四边形效果。**即将扩增更多自定义内容，敬请期待**
- [x] **距离裁剪**：超过相机 1000 格外自动跳过渲染以优化性能。
- [x] **多人游戏支持**：光环可在多人服务器上同步——挂载、移除和配置光环，所有连接的客户端都能实时看到效果。

## 未来将会添加的特性

- [ ] **在物品栏内可见** : 目前模组的光环不会在物品栏中的玩家模型头部渲染，等待后续加入
- [x] **更多动画** : 添加 `animation.alpha` 透明度 / `animation.glow` 发光强度动画、缩放动画、以及"启动动画"
- [ ] **更多光环layer种类** : 预计将添加`mesh`（以`.obj`为载体的网格）
- [ ] **更多的layer字段** : 将加入thickness，采用精灵图挤出的方法使billboard拥有厚度，但可能在材质较大的情况下影响性能
- [ ] **更完善的自发光** : 兼容更多光影、质感更强
- [ ] **更多实体和姿态适配** : 目前光环的显示位置和动画在部分实体上存在问题，等待修复
- [x] **单人游戏 `/halo hide` 关闭动画** : 单人游戏中关闭（淡出）动画无法播放，光环会瞬间消失。此为已知问题，多人游戏下关闭动画正常工作，修复中
- [ ] 其他各项bug修复，欢迎提交issue

<a id="安装"></a>

## 安装

1. 为 Minecraft 26.1 ~ 26.1.2 安装 [Fabric Loader](https://fabricmc.net/use/) 0.19.3+。
2. 下载对应版本的 [Fabric API](https://modrinth.com/mod/fabric-api)。
3. 从 [Releases](https://github.com/AzusaKe/Halo/releases) 页面下载最新的 **Halo** 模组 JAR 文件。
4. 将两个 JAR 文件放入 Minecraft 安装目录的 `mods` 文件夹中。
5. 使用 Fabric 配置文件启动 Minecraft。

### NeoForge

本模组为 Fabric 原生模组，但可通过 [Sinytra Connector](https://modrinth.com/mod/connector) + [Forgified Fabric API](https://modrinth.com/mod/forgified-fabric-api) 在 **NeoForge**（相同 Minecraft 版本）上运行。核心功能（光环渲染、持久化）已实测可用；高版本 Minecraft 不推荐使用 Forge。

1. 安装 NeoForge（Minecraft 26.1 ~ 26.1.2）
2. 安装 [Sinytra Connector](https://modrinth.com/mod/connector)
3. 安装 [Forgified Fabric API](https://modrinth.com/mod/forgified-fabric-api)
4. 将 Halo 模组 JAR 放入 `mods` 文件夹

<a id="使用方法"></a>

## 使用方法

<a id="命令"></a>

### 命令

所有命令默认需要 2 级权限（管理员）。可在 `config/halo-azusake/halo_mod_config.json` 中修改所需权限等级（0-4，修改后需重启服务器/游戏生效）；该文件是模组级配置文件，与 `/halo config` 的运行时参数无关。使用 `/halo` 配合 Tab 补全探索可用的子命令。

| 命令                                               | 描述                                         |
| -------------------------------------------------- | -------------------------------------------- |
| `/halo list`                                       | 列出所有已加载的光环定义                     |
| `/halo dump`                                       | 详细输出光环定义，包含形状、动画和衰减信息   |
| `/halo show <实体> <定义>`                         | 将光环挂载到实体上                           |
| `/halo hide <实体>`                                | 移除实体的光环                               |
| `/halo active`                                     | 列出所有当前佩戴光环的实体                   |
| `/halo inspect <实体>`                             | 查看指定实体的光环运行时状态详情             |
| `/halo config linear-damping <0-1>`                | 设置线性跟随速度（0 = 不跟随，1 = 瞬间跟随） |
| `/halo config angular-damping <0-1>`               | 设置角度跟随速度                             |
| `/halo config max-linear-distance <n>`             | 设置硬夹断前最大距离（格）                   |
| `/halo config max-angular-degrees <n>`             | 设置最大角度偏差（度）                       |
| `/halo config allow-angular-momentum <true/false>` | 开关角动量惯性效果                           |
| `/halo config angular-momentum-factor <0-1>`       | 设置角动量阻尼系数（0 = 冻结，1 = 无惯性）   |
| `/halo config max-angular-momentum-degrees <n>`    | 设置最大角动量偏差角度（度）                 |
| `/halo config scale <0.1+>`                        | 设置统一缩放倍率                             |
| `/halo save`                                       | 同步光环数据到世界持久化存储并触发 save-all  |
| `/halo debug <true/false>`                         | 开关传送/吸附调试日志输出到聊天栏            |
| `/halo reload`                                     | 提示使用 `/reload` 来重新加载光环定义        |

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

<a id="ysm-兼容"></a>

### YSM 兼容

Halo 的 Yes Steve Model（YSM）兼容功能是可选且精确锁定版本的。本分支仅支持 **YSM `2.6.5-neoforge+mc26.1`**。未安装 YSM、关闭功能或安装其他版本时，Halo 会安全回退到标准实体锚点。只有负责渲染 YSM 模型的客户端需要安装 YSM；Halo 的服务端同步不要求安装 YSM。

开启 YSM 头部 locator 锚点的方法：

1. 安装受支持的 YSM 版本，并启动一次游戏，让 Halo 创建 `config/halo-azusake/halo_mod_config.json`。
2. 关闭游戏，保留配置文件中已有的其他字段。
3. 设置以下字段，然后重新启动游戏：

```json
{
  "commandPermissionLevel": 2,
  "experimentalYsmAnchorEnabled": true,
  "experimentalYsmHeadLocalOffset": [0.0, 0.0, 0.0]
}
```

多人游戏中，**每个负责渲染的客户端**都必须在自己的配置文件中开启该选项；此设置不会由服务器同步。

`experimentalYsmHeadLocalOffset` 用于微调捕获到的 Head locator。它采用头部局部坐标、单位为方块，数组顺序是 `[右, 上, 后]`：

- 第一个值：正数向右，负数向左。
- 第二个值：正数向上，负数向下。
- 第三个值：正数向后，负数向前。

建议从 `[0.0, 0.0, 0.0]` 开始，以 `0.01`–`0.05` 的小步长调整。例如锚点偏低时可尝试 `[0.0, 0.05, 0.0]`，锚点偏前时可尝试 `[0.0, 0.0, 0.03]`。该字段是对所有 YSM 模型生效的全局 locator 修正；单个光环的位置仍应在光环定义中调整。修改配置后必须重启游戏，执行 `/reload` 不会应用此设置。

<a id="自定义光环定义"></a>

### 自定义光环定义

光环定义是存放在 `assets/<namespace>/halo_definitions/`（资源包）下的 JSON 文件。服务器不读取任何光环定义——它只记录"哪个实体拥有哪个光环"并广播给客户端；客户端用自己的本地资源包定义来渲染光环。

> **完整的分步教程和字段参考请见文档：**
> [快速上手](docs/zh/quickstart.md) · [字段参考](docs/zh/reference.md)

**定义示例**（`ring_default.json`，简化版——[完整版](src/main/resources/assets/halo/halo_definitions/ring_default.json)）：

```json
{
  "id": "halo:ring_default",
  "orientation_mode": "locked",
  "allow_angular_momentum": true,
  "layers": [
    {
      "position": [0.0, -0.001, 0.0],
      "rotation": [0.0, 0.0, 0.0],
      "scale": 1.5,
      "animation": {
        "offset": {
          "y": [{ "function": "sin", "A": 0.01, "omega": 0.5 }]
        }
      },
      "primitive": {
        "type": "billboard",
        "texture": "halo:textures/halo/ring_03.png",
        "size": [0.5, 0.5]
      }
    },
    {
      "position": [0.0, 0.0, 0.0],
      "rotation": [0.0, 0.0, 0.0],
      "scale": 1.5,
      "animation": {
        "offset": {
          "y": [{ "function": "sin", "A": 0.01, "omega": 0.5 }]
        },
        "rotation": {
          "yaw": [{ "function": "linear", "start": 0, "speed": -0.008333 }]
        }
      },
      "primitive": {
        "type": "billboard",
        "texture": "halo:textures/halo/ring_00.png",
        "size": [0.5, 0.5]
      }
    },
    {
      "position": [0.0, 0.001, 0.0],
      "rotation": [0.0, 0.0, 0.0],
      "scale": 1.5,
      "animation": {
        "offset": {
          "y": [{ "function": "sin", "A": 0.01, "omega": 0.5 }]
        },
        "rotation": {
          "yaw": [{ "function": "linear", "start": 0, "speed": -0.1 }]
        }
      },
      "primitive": {
        "type": "billboard",
        "texture": "halo:textures/halo/ring_01.png",
        "size": [0.5, 0.5]
      }
    }
  ],
  "positioning": {
    "offset": [0.0, 0.4, 0.35],
    "scale": 1.0
  },
  "damping": {
    "linearFactor": 0.45,
    "angularFactor": 0.1,
    "maxLinearDistance": 0.5,
    "maxAngularDegrees": 180.0,
    "angularMomentumFactor": 0.3,
    "maxAngularMomentumDegrees": 45.0
  }
}
```

| 字段                                | 描述                                                              |
| ----------------------------------- | ----------------------------------------------------------------- |
| `id`                                | 唯一标识符，格式为 `命名空间:名称`                                |
| `orientation_mode`                  | `locked`、`free` 或 `sync` —— 光环相对实体头部的朝向方式          |
| `allow_angular_momentum`            | `true` 时为朝向添加角动量惯性效果（仅 `locked`/`free` 模式）      |
| `hide_on_sleep`                     | `true` 时，实体睡觉期间光环停止渲染（默认 `false`）               |
| `display_in_invisible`              | `true` 时，实体隐形期间光环继续渲染（默认 `false`）               |
| `layers`                            | 组数组；每个组可包含多个图元和嵌套子组，共享同一变换与动画        |
| `layers[].position`                 | 该组相对父组或锚点帧的 `[X, Y, Z]` 偏移（格）                     |
| `layers[].rotation`                 | 该组的 `[yaw, pitch, roll]` 欧拉旋转（度）                        |
| `layers[].scale`                    | 该组的缩放倍率                                                    |
| `layers[].animation`                | 该组的 `offset` / `rotation` 动画曲线（详见参考文档）             |
| `layers[].primitives`               | 该组内的渲染图元数组（见下方）                                    |
| `layers[].primitive`                | 向后兼容的单图元对象（等价于 `primitives: [...]`）                |
| `layers[].children`                 | 可选的嵌套子组数组，继承该组的变换                                |
| `primitive.type`                    | `billboard`（单个带纹理的四边形）或 `ring`（圆柱形圆环）          |
| `primitive.texture`                 | 纹理路径，如 `halo:textures/halo/ring_00.png`                     |
| `primitive.inner_texture`           | 仅 ring：内表面纹理（可选，省略时使用 `texture`）                 |
| `layers[].primitive.size`           | `billboard`：`[宽度, 深度]`；`ring`：`[半径, 柱面宽度]`，单位为格 |
| `layers[].primitive.segments`       | 仅 ring：多边形分段数（默认 32）                                  |
| `positioning.offset`                | `[X, Y, Z]` 相对实体头部的偏移量（格）                            |
| `positioning.scale`                 | 默认缩放倍率                                                      |
| `damping.linearFactor`              | 20 TPS 下每 tick 的线性插值速度（0 = 不跟随，1 = 瞬间跟随）       |
| `damping.angularFactor`             | 角度插值速度（范围同上）                                          |
| `damping.maxLinearDistance`         | 硬夹断最大距离（格）                                              |
| `damping.maxAngularDegrees`         | 最大角度偏差（度）                                                |
| `damping.angularMomentumFactor`     | 角动量阻尼系数（0 = 冻结，1 = 无惯性）                            |
| `damping.maxAngularMomentumDegrees` | 最大角动量偏差角度（度）                                          |

> 添加或修改光环定义后，运行 `/reload` 重新加载。

<a id="从源码构建"></a>

## 从源码构建

<a id="前置条件"></a>

### 前置条件

- **JDK 25** — Minecraft 26.1 所需
- 互联网连接（Gradle 从 Maven 仓库下载依赖）

<a id="构建"></a>

### 构建

```bash
git clone https://github.com/AzusaKe/Halo.git
cd Halo
./gradlew build
```

编译好的 JAR 文件位于 `build/libs/halo-26.1.2-fabric-1.2.1.jar`。

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
  java/network/azusake/halo/
    HaloMod.java              — 模组初始化器（服务端入口）
    HaloModClient.java         — 客户端初始化器（客户端入口）
    animation/                 — 动画曲线（Linear、Oscillate、Constant）
    client/                    — 客户端指令拦截、阶段追踪、本地管理
    command/                   — /halo Brigadier 命令树
    config/                    — 运行时 HaloConfig（衰减、缩放、偏移）
    data/                      — HaloDefinition、HaloInstance、HaloEntityData 等
    json/                      — JSON 反序列化器和资源加载器
    lifecycle/                 — EntityHaloTracker、HaloWorldSaveData、事件处理器
    manager/                   — HaloManager（单例，管理所有活跃光环）
    mixin/                     — EntityTeleportMixin、LivingEntityDataMixin
    network/                   — HaloNetwork、HaloNetworkClient（多人同步）
    physics/                   — AnchorFrameCalculator、DampingPhysics、HaloTickHandler
    render/                    — HaloRenderer、HaloClientManager、HaloRenderListener
    server/                    — HaloServerEvents、ServerTickHandler
    shape/                     — BillboardPrimitive、RingPrimitive、HaloGroup、HaloModel
  resources/
    fabric.mod.json            — 模组元数据（入口点、Mixin、依赖）
    halo.mixins.json            — Mixin 配置
    assets/halo/
      halo_definitions/         — JSON 光环定义文件（资源包，客户端渲染使用）
      textures/halo/            — 光环与发光纹理
```

<a id="贡献"></a>

## 贡献

欢迎对 Halo 进行贡献！如果你有想法、建议或想报告 Bug，请在 [GitHub 仓库](https://github.com/AzusaKe/Halo) 提交 Issue。如果你想贡献代码，请 Fork 仓库并提交 Pull Request。

- **开发环境**：Minecraft 26.1.2 + Fabric Loader 0.19.3+
- **IDE**：推荐 IntelliJ IDEA（配合 Minecraft Development 插件）或 VS Code

<a id="许可证"></a>

## 许可证

本项目采用 MIT 许可证 — 详情请参阅 [LICENSE](LICENSE) 文件。
