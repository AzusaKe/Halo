# Halo Mod — 公开 API

## `HaloCommandInterceptor`

**包：** `network.azusake.halo.client`

`HaloCommandInterceptor` 是拦截 `/halo` 命令（在到达服务器之前）的抽象层。它将核心的阶段追踪/本地执行逻辑与依赖加载器的钩子机制隔离开。

### 接口

```java
public interface HaloCommandInterceptor {
    void register();        // 注册客户端 /halo 命令树，幂等
    boolean isRegistered(); // 是否已注册
}
```

### 行为约定

每个实现在命令 executor 中**必须**遵循以下流程：

1. 调用 `HaloPhaseTracker.getInstance().shouldIntercept()`。
2. 若为 `true`（LOCAL 阶段——服务器无 mod）：
   - 调用 `HaloLocalCommandHandler.handle(commandString)`。
   - 在本地聊天栏显示返回的反馈文本。
   - **不要**将命令转发到服务器。
3. 若为 `false`（单人游戏或 MULTIPLAYER 阶段——服务器有 mod）：
   - 通过加载器原生的发送命令机制将原始命令字符串转发到服务器
     （例如 `client.getNetworkHandler().sendCommand(cmd)`）。

### 为什么需要此层

- 核心类（`HaloPhaseTracker`、`HaloLocalCommandHandler`、`HaloLocalManager`）**零**加载器依赖。
- 基于 Mixin 的拦截方案在不同 Yarn 映射版本间脆弱且难以移植。
- 此接口让每个加载器（Fabric、NeoForge 等）可以接入自己的命令注册 API，核心逻辑完全不变。

### Fabric 实现

`FabricHaloCommandInterceptor`（同一包下）通过 `ClientCommandRegistrationCallback.EVENT` 注册 `/halo`。

### 移植到其他加载器

1. 使用目标加载器的客户端命令 API 实现 `HaloCommandInterceptor`。
2. 在客户端初始化阶段的等价位置调用 `register()`。
3. 无需修改任何核心逻辑文件。

---

## 生命周期钩子

### `HaloPhaseTracker`

| 方法 | 说明 |
|------|------|
| `getPhase()` | 返回 `LOCAL` 或 `MULTIPLAYER` |
| `transitionToMultiplayer()` | 收到 `halo:hello` 包时调用 |
| `resetToLocal()` | 断连时调用 |
| `shouldIntercept()` | 单人安全网——集成服务器运行时永远返回 `false` |

### `HaloLocalManager`

每次变更时持久化到 `config/halo-azusake/halo_local_halos.json`。ServerKey 为稳定的 `hostString:port` 格式。

| 方法 | 说明 |
|------|------|
| `showHalo(serverKey, uuid, defId)` | 记录光环 |
| `hideHalo(serverKey, uuid)` | 移除光环 |
| `getHalo(serverKey, uuid)` | 查询 |
| `getHalosForServer(serverKey)` | 获取所有 UUID 供渲染使用 |
| `clearServer(serverKey)` | 断连清理 |
| `serverKeyFromAddress(address)` | 从 `InetSocketAddress` 构造稳定 key |

### `HaloLocalCommandHandler`

```java
static String handle(String command)
```

支持：`list`、`dump`、`show @s <定义ID>`、`hide @s`、`config <参数> <值>`、`reload`、`active`。
`show`/`hide` 严格要求 `@s` 选择器。

### `HaloModConfig` / `HaloModConfigStore`

模组级配置文件 `config/halo-azusake/halo_mod_config.json`，承载命令系统的底层配置
（当前为 `/halo` 所需权限等级），与 `/halo config` 运行时调参（`HaloConfig`）完全
分离。文件缺失或空白时启动自动写入默认值；越界值钳制到 0-4；损坏 JSON 回退默认并
警告；未知键忽略（向后兼容）。

| 方法 | 说明 |
|------|------|
| `HaloModConfigStore.load()` | 启动时加载（mod 初始化调用一次） |
| `HaloModConfigStore.getPermissionLevel()` | `/halo` 命令树所需权限等级（默认 2，范围 0-4） |
| `HaloModConfig.getCommandPermissionLevel()` | 当前权限等级 |
| `HaloModConfig.setCommandPermissionLevel(int)` | 设置权限等级（钳制到 0-4） |

---

## 自定义头部锚点（Custom Head Anchor）

Halo 提供一个按实体类型/UUID查找头部锚点的注册表。下文描述的是当前分支的真实接口。

> 重要：8个分支的语义和注册流程保持一致，但它们不是跨 Minecraft 版本的同一个二进制 API。外部模组必须依赖对应分支的 Halo，并使用该分支的 Minecraft 映射类型与加载器注册方式。

### 数据流与调用契约

`EntityAnchorProvider.resolve(entity, tickDelta)` 在客户端实体渲染线程上按帧调用。实现应返回非空、有限值的 `HeadAnchor`；若本帧数据尚未准备好，应保留上一帧有效值或返回合适的 Vanilla/fallback 锚点。

本分支实体参数类型：`net.minecraft.world.entity.LivingEntity`。

本分支 `HeadAnchor.headCenter` 类型：`net.minecraft.world.phys.Vec3`；yaw、pitch、roll 使用角度（度，Minecraft 约定）。

注册表的解析顺序为：UUID 精确匹配 → 实体类精确匹配 → 父类链匹配 → fallback。相同键重复注册时，后注册的 provider 覆盖先注册的 provider。

`getProvider(Class<?>)` 在没有专用 provider 时也会返回可用的 fallback provider，不应把返回值当作 nullable API。

### 注册方式

NeoForge 分支在客户端初始化阶段使用 Halo 提供的事件持有者注册：

```java
import net.minecraft.world.entity.LivingEntity;
import network.azusake.halo.api.AnchorProviderSetupEvent;

public final class MyModClient {
    public static void registerHaloProviders() {
        AnchorProviderSetupEvent.EVENT.register(registry ->
            registry.register(LivingEntity.class, new MyHeadProvider()));
    }
}
```

26.x NeoForge 的稳定外部入口就是 Halo 的 `EVENT.register(...)`；不要依赖不存在于本分支的原生事件对象或 `getRegistry()` 方法。

### 默认优先级与兼容行为

- 外部 provider 只负责提供锚点；注册表的 UUID、精确类、父类链和 fallback 解析规则不因加载器改变。
- 玩家优先使用 Halo 的玩家锚点 provider；EMF 捕获到的玩家头部数据可覆盖该帧锚点，捕获不可用时回退到玩家 provider。
- 当前 EMF 兼容代码只接管玩家。非玩家实体不使用 EMF 捕获，继续走本分支已有的 YSM、Vanilla 或 fallback 路径；这是有意保留的保守策略。
- YSM 兼容是否存在及其配置方式取决于具体分支，和 EMF 兼容相互独立；请以当前分支的 YSM 章节和配置为准。

### EMF/ETF 兼容

EMF 兼容默认开启，不增加 Halo 配置开关；支持版本下界为 EMF 3.1.1，不人为设置上界，并在运行时检测实际 ABI。ETF 不增加独立捕获代码，只进行与 EMF/ETF 共存验证。

若检测到当前 EMF ABI 不兼容，会在日志及聊天栏提示：

> 当前Halo模组的EMF兼容代码无法再适用于加载版本的emf模组，请前往源码库汇报

### 注册建议

外部模组通常应注册自己的具体实体类，而不是无条件覆盖 `LivingEntity.class`；如需包装已有 provider，应先保存已有 provider，再在自定义 provider 中委托并只调整头部锚点。不要在渲染回调之外缓存跨实体的临时状态，也不要假设所有实体都有相同的模型部件结构。

## 网络通道

| 通道 | 方向 | 用途 |
|------|------|------|
| `halo:sync` | S2C | 玩家加入时全量状态快照 |
| `halo:update` | S2C | 增量 附加/移除 广播 |
| `halo:defs_report` | C2S | 客户端报告本地可用的定义 ID |
| `halo:hello` | S2C | 握手——宣告服务器已安装 mod |
