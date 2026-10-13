# Halo 工程审计：编号发现清单

审计日期：2026-10-13（Asia/Shanghai）。来源分支：`1.20.1-fabric`；审计文档分支：`review/2026-10-13`。

Halo 基线：`49b822c037606faaf0bb8ba2adba7b63ce0d62bb`；锁定 HaloCore：`a22160a842e15fffb1bc03b5b189183557f6d510`（2.5.0）。所有源码行号指向这两个提交，不指向后续修复版本。

本清单是发现的完整事实来源。[审计报告](audit-report.md)提供系统层面的解释，[修复路线](remediation-plan.md)给出依赖顺序，[验证基线](verification-baseline.md)包含执行结果与可重放探针。

共15项：12项已确认、2项疑似风险、1项改进建议；优先级为 P1×2、P2×12、P3×1。未确认 P0。确认的是各项描述中的具体源码/行为事实；需要 Minecraft、GL 或故障注入才能验证的影响另行注明。

工作量按1名熟悉仓库的工程师、1日约8小时估算，包含定向回归，不包含所有平台分支迁移、正式发布或全量第三方矩阵。置信度不是发生概率。

| 编号 | 优先级 | 状态 | 发现 |
| --- | --- | --- | --- |
| [HALO-001](#halo-001) | P1 | 已确认 | 客户端定义上报数量未经校验即可驱动巨额集合分配 |
| [HALO-002](#halo-002) | P1 | 已确认 | 空 path identifier 在持久化之后被来源 API 拒绝 |
| [HALO-003](#halo-003) | P2 | 已确认 | 缓存 billboard 的受光顶点上传循环越界 |
| [HALO-004](#halo-004) | P2 | 已确认 | 本地存储中的单条 null 值中断整个加载并丢失后续有效记录 |
| [HALO-005](#halo-005) | P2 | 已确认 | 本地佩戴和模组配置直接覆盖文件，缺少原子替换 |
| [HALO-006](#halo-006) | P2 | 已确认 | 优先级保存失败仍返回成功，运行状态与磁盘状态分叉 |
| [HALO-007](#halo-007) | P2 | 已确认 | 层级定义没有深度限制，栈溢出绕过资源错误隔离 |
| [HALO-008](#halo-008) | P2 | 已确认 | ring segments 没有上限，加载阶段可生成超大几何 |
| [HALO-009](#halo-009) | P2 | 已确认 | 动画求值边界直接返回共享数组，可污染全局缓存 |
| [HALO-010](#halo-010) | P2 | 已确认 | 世界佩戴存储使用线性列表，实体加载形成乘积扫描 |
| [HALO-011](#halo-011) | P2 | 已确认 | 同代际视觉资产缓存不驱逐已移除的模型 |
| [HALO-012](#halo-012) | P2 | 疑似风险 | 世界实体捕获的 begin/end 对非 LivingEntity 不对称 |
| [HALO-013](#halo-013) | P2 | 已确认 | 关键输入与真实上传路径未进入可阻断的测试门禁 |
| [HALO-014](#halo-014) | P3 | 改进建议（配置缺口已确认） | 构建依赖缺少校验清单，CI actions 使用可变标签 |
| [HALO-015](#halo-015) | P2 | 疑似风险 | 自制命令包固定空聊天确认状态，在线聊天场景兼容性未验证 |

## HALO-001

**P1 · 客户端定义上报数量未经校验即可驱动巨额集合分配**

- **状态 / 分类**：已确认；安全 / 输入边界 / 稳定性。
- **位置**：[`src/main/java/network/azusake/halo/network/HaloNetwork.java:80–93`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/src/main/java/network/azusake/halo/network/HaloNetwork.java#L80-L93)。
- **复现条件**：任一已连接的客户端发送 `halo:defs_report`，声明 `count=2147483647`，再提供一个合法 identifier；该接收器没有管理员权限要求。负数和截断包也未在此处校验。
- **影响**：首个集合插入可触发 OOM；在可分配超大数组的 JVM 上会先申请数 GiB 的桶数组。该请求不需要同等规模的网络负载。重复合法大报告也会放大解析、复制和服务器任务队列负担。不能由隔离结果断言服务器进程一定退出。
- **证据**：源码先 `readInt()`，随后 `new LinkedHashSet<>(count)`，在逐项读取时直接 `ids.add(...)`。集合是延迟分配：巨大数组申请发生在第一次 add。64 MiB 隔离 JVM 复现 `OutOfMemoryError: Java heap space`；负数复现 `IllegalArgumentException`。未向真实服务器发包。对比 `HaloPacketCodec.decodeSnapshot` 的数量/剩余字节校验，C2S 路径缺少相同边界。
- **置信度**：高：危险分配与输入可达路径已确认；端到端服务器故障形式未验证。
- **推荐修复**：先按剩余字节、合理的 ID 数量和单 ID 长度限制校验，再创建集合；解析失败仅拒绝该报告，保留原报告。为该频道添加每连接节流、合并待处理报告和断线后的任务有效性检查。不要依赖捕获 OOM 来建立边界。
- **验证方法**：覆盖负数、零、上限、上限+1、极大声明但短负载、非法/截断 identifier、重复 ID 和报告洪泛；断言无大分配、无主线程未捕获异常、原状态不被部分覆盖。之后在隔离双客户端服务器验证拒绝行为。
- **工作量**：1–2 工程师日。
- **回归风险**：中：需要兼顾大量资源包的真实 ID 数量与现有字节协议；建议保持 wire layout 不变。

> 开发者置评：暂不予理会，属于过度防御性编程，且需恶意修改客户端
## HALO-002

**P1 · 空 path identifier 在持久化之后被来源 API 拒绝**

- **状态 / 分类**：已确认；功能正确性 / API 契约 / 数据一致性。
- **位置**：[`core/src/main/java/network/azusake/halo/core/Identifier.java:9–17`](https://github.com/AzusaKe/HaloCore/blob/a22160a842e15fffb1bc03b5b189183557f6d510/src/main/java/network/azusake/halo/core/Identifier.java#L9-L17)；[`core/src/main/java/network/azusake/halo/core/runtime/HaloSourceCoordinator.java:9`](https://github.com/AzusaKe/HaloCore/blob/a22160a842e15fffb1bc03b5b189183557f6d510/src/main/java/network/azusake/halo/core/runtime/HaloSourceCoordinator.java#L9)；[`src/main/java/network/azusake/halo/manager/HaloManager.java:74–94`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/src/main/java/network/azusake/halo/manager/HaloManager.java#L74-L94)；[`src/main/java/network/azusake/halo/lifecycle/HaloWorldSaveData.java:79–83`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/src/main/java/network/azusake/halo/lifecycle/HaloWorldSaveData.java#L79-L83)。
- **复现条件**：通过适配器的公开 `showHaloOn(entity, new Identifier("halo:"))` 调用，或存档中出现 `Definition="halo:"`。`/halo show` 也使用 Minecraft identifier 参数，但空 path 的命令解析端到端未运行，不能把命令可达性当作本次已复现结果。
- **影响**：世界数据先被写入一个来源 API 不接受的 ID，然后来源设置抛异常；持久状态与运行状态不一致。`fromNbt` 接受此 ID，而每次实体恢复再次调用 `worldDataSource.set`，可重复失败。可能阻断受影响实体的恢复；未验证整服启动是否被阻断。
- **证据**：Core Identifier 的 path 正则使用 `*`，接受空串；HaloSourceCoordinator 使用 `+`，拒绝空 path。隔离调用实际 core 类输出 `Identifier accepted halo:`，随后 `HaloSource rejected`。HaloManager 行77先写世界数据，行78后校验来源输入；restore 行92没有异常隔离。NBT 工厂只按较宽松的 Identifier 构造器校验。
- **置信度**：高：契约冲突、写入顺序和恢复路径已确认；Minecraft 指令及存档故障的整服影响未实测。
- **推荐修复**：定义一处统一的佩戴 definition ID 校验，在所有写入前完成；保持来源 ID 与资源 ID 的语义明确。对旧 NBT 中非法记录做可诊断的隔离/迁移，保留可恢复数据，避免重新持久化毒化记录。
- **验证方法**：断言空 path 和非法 ID 在存储调用前被拒绝、旧有效佩戴不变；用合成 NBT 加载混合有效/无效记录，验证实体加载和重启不会再次异常；正常命令、权杖和外部来源回归。
- **工作量**：1–2 工程师日。
- **回归风险**：中：涉及 core 与适配器及已有存档；不要擅自改变所有资源 Identifier 的历史空 path 语义。

> 开发者置评：不予接受，不属于常规情况下会发生的行为，且影响可逆，数据的维护由实际玩家或管理员负责
## HALO-003

**P2 · 缓存 billboard 的受光顶点上传循环越界**

- **状态 / 分类**：已确认；功能正确性 / 渲染 / 性能。
- **位置**：[`src/main/java/network/azusake/halo/render/HaloMeshBufferCache.java:195–220`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/src/main/java/network/azusake/halo/render/HaloMeshBufferCache.java#L195-L220)；[`core/src/main/java/network/azusake/halo/render/PrimitiveGeometries.java:25–32`](https://github.com/AzusaKe/HaloCore/blob/a22160a842e15fffb1bc03b5b189183557f6d510/src/main/java/network/azusake/halo/render/PrimitiveGeometries.java#L25-L32)；[`src/main/java/network/azusake/halo/render/GenerationMeshCache.java:43–58`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/src/main/java/network/azusake/halo/render/GenerationMeshCache.java#L43-L58)。
- **复现条件**：切换 `/halo renderer cached`，加载 billboard 或 face_camera 共享 quad。缓存构造会同时上传 flat 和 lit 两种布局，因此不只 `glowing:false` 的 billboard 受影响。
- **影响**：quad 只有4个顶点，上传循环却访问0到5，访问第4个顶点时越界。工厂异常被 GenerationMeshCache 捕获并记为失败；因此主要表现为缓存上传失败和每帧 CPU fallback，而非已证明的客户端崩溃。该模式在 billboard 上无法兑现静态缓存路径。
- **证据**：`corners = mesh.vertexCount() * 3 / 2`，但 QUADS 分支用 `vertex=corner`。实际 PrimitiveGeometries 创建4顶点、6索引。隔离使用实际 core geometry 和相同访问循环输出 `vertices=4 corners=6`，随后 `ArrayIndexOutOfBoundsException: Index 12 out of bounds for length 12`。这里混淆了索引个数和顶点个数。
- **置信度**：高：数组访问已复现；GPU 上下文中的日志、fallback 成本和画面未实测。
- **推荐修复**：QUADS 上传保留4角顶点流，以实际写入顶点数决定容量，仍用6个三角形索引提交；TRIANGLES 保留逐角展开。检查索引写入与 sourceQuad 分支的一致性，并加入实际生产上传循环的测试接口。
- **验证方法**：用真实 quad geometry 断言4次顶点写入、6个合法索引，且 factory 不失败；游戏中验证普通/受光/face_camera billboard、两种模式、GUI、Iris 开关和重载，观察缓存失败计数及稳态上传量。
- **工作量**：0.5–1.5 工程师日。
- **回归风险**：中：需保护 Iris 从四角计算切线/中点 UV 的契约，不能简单把循环改为六角展开。

> 开发者置评：确认属实后需修复
## HALO-004

**P2 · 本地存储中的单条 null 值中断整个加载并丢失后续有效记录**

- **状态 / 分类**：已确认；异常处理 / 数据完整性。
- **位置**：[`core/src/main/java/network/azusake/halo/core/runtime/LocalOwnership.java:47–89`](https://github.com/AzusaKe/HaloCore/blob/a22160a842e15fffb1bc03b5b189183557f6d510/src/main/java/network/azusake/halo/core/runtime/LocalOwnership.java#L47-L89)；[`core/src/main/java/network/azusake/halo/core/Identifier.java:9–11`](https://github.com/AzusaKe/HaloCore/blob/a22160a842e15fffb1bc03b5b189183557f6d510/src/main/java/network/azusake/halo/core/Identifier.java#L9-L11)。
- **复现条件**：`halo_local_halos.json` 为合法 JSON，但某一 UUID 的 definition 值为 null，例如手动编辑或不完整迁移。它后面还有其他服务器的有效记录。
- **影响**：null 导致 Identifier 构造器 NPE，绕过单记录的 IllegalArgumentException 捕获，退出整个 load；ensureLoaded 仍设为 true。后续 show/hide 将残缺的内存状态写回，删除未加载的有效记录。
- **证据**：合成 TextStore 的第一个服务器包含 null，第二个服务器包含合法 `halo:kept`。调用实际 LocalOwnership 后第二条返回 `Optional.empty`；添加第三个服务器后输出已不含第二个服务器。证据不是语法损坏的整个 JSON，而是一个语义无效的叶子值。
- **置信度**：高：完整加载和后续写回行为在实际 core 类上已复现。
- **推荐修复**：先在临时结构中解析，逐条显式检查 null、UUID 和 ID；隔离坏记录后提交有效结果。区分不存在、读取失败与部分损坏，加载失败时禁止覆盖原文件，并提供备份/诊断。
- **验证方法**：覆盖首/中/末位置的 null、坏 UUID、坏 ID、合法后续服务器、无法读取文件及再次保存；断言有效记录全部保留，失败时旧文件未被覆盖，重试加载具有明确策略。
- **工作量**：0.5–1 工程师日。
- **回归风险**：低至中：只应改变错误路径，保持 JSON 结构和正常恢复行为。

> 开发者置评：不予理会，通常情况下不会发生，属于过度防御性编程
## HALO-005

**P2 · 本地佩戴和模组配置直接覆盖文件，缺少原子替换**

- **状态 / 分类**：已确认；文件 I/O / 故障恢复 / 数据完整性。
- **位置**：[`src/main/java/network/azusake/halo/client/HaloLocalManager.java:12–17`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/src/main/java/network/azusake/halo/client/HaloLocalManager.java#L12-L17)；[`src/main/java/network/azusake/halo/config/HaloModConfigStore.java:100–129`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/src/main/java/network/azusake/halo/config/HaloModConfigStore.java#L100-L129)。
- **复现条件**：写 `halo_local_halos.json` 或保存/补齐 `halo_mod_config.json` 时发生进程中断、磁盘写失败等故障。配置切换和本地佩戴变更都会走这些路径。
- **影响**：直接 TRUNCATE_EXISTING 后写入会让原有效文件在写完成前失去保护；失败可能留下空文件或不完整 JSON。读取恢复又会使用默认/空状态，可能放大为本地佩戴或配置丢失。
- **证据**：三处 `Files.writeString(file, ...)` 均没有临时文件和 rename，也没有保留最后有效备份。对比同仓库 HaloSourcePriorityStore.write 已使用 sibling `.tmp` + ATOMIC_MOVE/fallback，这是可复用的已有实现方向。本次确认了写入设计缺口，没有模拟真实断电或磁盘故障；实际丢失属于条件性后果。
- **置信度**：高：非原子覆盖路径明确；故障下文件损坏未做 OS 级实测。
- **推荐修复**：统一文件写入器：同目录临时文件、完成写入后原子替换、明确不支持原子移动时的策略及失败结果。为本地佩戴保留最后有效副本；原子 rename 与断电持久化并非同一保证，是否 fsync 应按需求决定。
- **验证方法**：注入打开、写入中途、移动各阶段的失败，断言旧完整文件仍可读且 UI 不报告已持久化；验证未知配置字段保留、首次创建、缺字段迁移和原子移动不可用的文件系统。
- **工作量**：1–2 工程师日。
- **回归风险**：中：避免迁移时丢未知字段，避免把未完成的临时文件当作有效配置。

> 开发者置评：模组不是操作系统，数据维护由玩家负责
## HALO-006

**P2 · 优先级保存失败仍返回成功，运行状态与磁盘状态分叉**

- **状态 / 分类**：已确认；API 契约 / 持久化 / 操作反馈。
- **位置**：[`src/main/java/network/azusake/halo/config/HaloSourcePriorityStore.java:55–68`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/src/main/java/network/azusake/halo/config/HaloSourcePriorityStore.java#L55-L68)；[`src/main/java/network/azusake/halo/config/HaloSourcePriorityStore.java:88–91`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/src/main/java/network/azusake/halo/config/HaloSourcePriorityStore.java#L88-L91)；[`src/main/java/network/azusake/halo/command/HaloConfigCommand.java:238–251`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/src/main/java/network/azusake/halo/command/HaloConfigCommand.java#L238-L251)；[`src/main/java/network/azusake/halo/manager/HaloManager.java:167–172`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/src/main/java/network/azusake/halo/manager/HaloManager.java#L167-L172)。
- **复现条件**：优先级文件无法替换，例如只读目录、目标路径被变成目录或磁盘问题；执行 `/halo priority set`。
- **影响**：current 先更新，save 只记录 warning；命令返回 SINGLE_SUCCESS，未注册来源还明确显示 Saved。本次运行使用新值，重启却可能恢复旧值，管理员无法从命令结果区分持久保存与仅运行时变更。
- **证据**：在桩平台下编译未修改的实际 HaloSourcePriorityStore；先正常写入，再将目标替换为合成非空目录，调用 set。实际结果为 warning、正常返回、内存99、目标仍是目录。桩只代替 Fabric 路径和日志，不代替生产文件写入代码。命令层成功反馈由源码确认。
- **置信度**：高：实际 writer 的失败与吞异常已复现；游戏聊天反馈未运行。
- **推荐修复**：存储 API 返回结构化写入结果；决定失败时回滚内存还是显式保留临时生效值，命令必须清楚反馈。merge 的 boolean 不应混同“内存变更”与“持久化成功”。
- **验证方法**：对 IOException/移动失败验证错误返回、命令状态及重启值；正常保存应立即生效并能重启恢复，来源冲突自动降级的持久化同样回归。
- **工作量**：0.5–1 工程师日。
- **回归风险**：中：需明确 runtime-only 失败策略，避免来源重配置与回滚产生额外广播。

> 开发者置评：同上，这不是操作系统
## HALO-007

**P2 · 层级定义没有深度限制，栈溢出绕过资源错误隔离**

- **状态 / 分类**：已确认；异常处理 / 输入边界 / 资源加载。
- **位置**：[`core/src/main/java/network/azusake/halo/json/HaloDefinitionDeserializer.java:166–228`](https://github.com/AzusaKe/HaloCore/blob/a22160a842e15fffb1bc03b5b189183557f6d510/src/main/java/network/azusake/halo/json/HaloDefinitionDeserializer.java#L166-L228)；[`core/src/main/java/network/azusake/halo/core/runtime/DefinitionResources.java:16–30`](https://github.com/AzusaKe/HaloCore/blob/a22160a842e15fffb1bc03b5b189183557f6d510/src/main/java/network/azusake/halo/core/runtime/DefinitionResources.java#L16-L30)；[`src/main/java/network/azusake/halo/json/HaloJsonLoader.java:165–183`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/src/main/java/network/azusake/halo/json/HaloJsonLoader.java#L165-L183)。
- **复现条件**：选中的资源包/数据包提供深层 `children` 树。没有 JSON 字节上限、层级深度或总节点数上限；其他收集/准备/渲染函数同样递归。
- **影响**：有效 JSON 可在定义转换时抛 StackOverflowError，资源 loader 只捕获 RuntimeException，不能把该资源作为普通坏定义隔离；资源重载中止或受宿主错误处理影响。该输入边界来自已选择的资源包，不是已证明的远程免交互攻击。
- **证据**：实际 DefinitionResources.reload 在 `-Xss256k` 下，1000层、15033字节的合成合法 JSON 抛出未捕获 StackOverflowError。较小栈用于可重复隔离；另在本次JVM默认栈下，4000层/60033字节也抛同样错误。游戏JVM的具体阈值未测。JSON 初始解析完成后的 parseGroup 递归缺少预算。
- **置信度**：高：资源入口与错误类型已复现；游戏重载后的恢复行为未实测。
- **推荐修复**：在解析阶段限制深度和节点数，并限制适配层读取字节。优先用显式预算/迭代遍历，保证收集资产、准备 geometry 和渲染也受同一结构边界约束；不要用笼统 catch Error 替代输入验证。
- **验证方法**：上限与上限+1 深度、宽树、多个资源的总预算、重复重载；无效资源应有问题报告，其他有效资源仍发布，之前可用的资源状态按明确策略处理。
- **工作量**：1–2 工程师日。
- **回归风险**：中：应选择覆盖真实作者需求的上限，并统一所有递归入口。

> 开发者置评：资源包深度有限属于开发前的假设，目前仍然有效（截止至当前审阅，未来可能变更，必要时重新询问用户），所以暂不修复
## HALO-008

**P2 · ring segments 没有上限，加载阶段可生成超大几何**

- **状态 / 分类**：已确认；性能 / 内存 / 输入边界。
- **位置**：[`core/src/main/java/network/azusake/halo/json/HaloDefinitionDeserializer.java:404–423`](https://github.com/AzusaKe/HaloCore/blob/a22160a842e15fffb1bc03b5b189183557f6d510/src/main/java/network/azusake/halo/json/HaloDefinitionDeserializer.java#L404-L423)；[`core/src/main/java/network/azusake/halo/render/PrimitiveGeometries.java:39–47`](https://github.com/AzusaKe/HaloCore/blob/a22160a842e15fffb1bc03b5b189183557f6d510/src/main/java/network/azusake/halo/render/PrimitiveGeometries.java#L39-L47)；[`core/src/main/java/network/azusake/halo/render/PrimitiveGeometries.java:72–100`](https://github.com/AzusaKe/HaloCore/blob/a22160a842e15fffb1bc03b5b189183557f6d510/src/main/java/network/azusake/halo/render/PrimitiveGeometries.java#L72-L100)。
- **复现条件**：小型 JSON 定义设置极大的 `segments`。构造器仅复制 size，准备时只做 `Math.max(3, segments)`，没有上限。
- **影响**：百万 segments 的单 ring 内外两面，仅第一轮位置/法线/UV/索引数组就约168 MiB；还未计防御复制、工作区、GPU 双布局和其他对象。OOM 捕获发生在申请之后，不能防止 GC/加载卡顿。
- **证据**：实际 parser 接受 `segments=1000000`。依据生产公式，两面初始数组至少176000128字节。探针只验证接受和计算预算，没有实际申请这些大数组；ObjMeshLoader 相比已设16 MiB、元素和三角形上限。乘法溢出检查存在，但不是合理资源预算。
- **置信度**：高：输入接受及分配公式明确；实际峰值/RSS/GPU行为未测。
- **推荐修复**：对 segments 做严格整数与合理上下限验证，限制单定义/整个加载代际的总顶点及字节预算；在准备前估算成本并拒绝超预算输入。与旧定义兼容策略一起设计，不应静默把巨额输入继续传递给分配器。
- **验证方法**：正常默认32、最小值、最大值、上限+1、整数边界、非整数和多 ring 总预算；断言在分配前返回诊断，其他定义可加载，预算统计符合两面及缓存布局。
- **工作量**：0.5–1.5 工程师日。
- **回归风险**：低至中：主要影响异常大定义，须记录上限和迁移提示。

> 开发者置评：极少数情况，暂不处理
## HALO-009

**P2 · 动画求值边界直接返回共享数组，可污染全局缓存**

- **状态 / 分类**：已确认；数据一致性 / 可维护性 / 对象所有权。
- **位置**：[`core/src/main/java/network/azusake/halo/animation/TransitionQueue.java:29–67`](https://github.com/AzusaKe/HaloCore/blob/a22160a842e15fffb1bc03b5b189183557f6d510/src/main/java/network/azusake/halo/animation/TransitionQueue.java#L29-L67)；[`core/src/main/java/network/azusake/halo/animation/TransitionAnimation.java:45–64`](https://github.com/AzusaKe/HaloCore/blob/a22160a842e15fffb1bc03b5b189183557f6d510/src/main/java/network/azusake/halo/animation/TransitionAnimation.java#L45-L64)；[`core/src/main/java/network/azusake/halo/animation/StartupAnimationConfig.java:40–43`](https://github.com/AzusaKe/HaloCore/blob/a22160a842e15fffb1bc03b5b189183557f6d510/src/main/java/network/azusake/halo/animation/StartupAnimationConfig.java#L40-L43)；[`core/src/main/java/network/azusake/halo/animation/StartupAnimationConfig.java:69–71`](https://github.com/AzusaKe/HaloCore/blob/a22160a842e15fffb1bc03b5b189183557f6d510/src/main/java/network/azusake/halo/animation/StartupAnimationConfig.java#L69-L71)。
- **复现条件**：调用方获得共享定义的 transition result，在开始/结束时间修改 `result.scale()` 或其他返回数组；也可通过公开 segment/queue accessor 修改数组。
- **影响**：修改一个求值结果会改变后续求值以及共享同一配置的其他实体。外层 List.copyOf/不可修改集合不能保护内部数组。可能造成新旧动画或并发检查看到不一致结果。当前自带渲染器是否主动写这些数组没有发现证据，不能声称已发生正常玩家画面故障。
- **证据**：实际 StartupAnimationConfig 与 TransitionAnimationResult：第一次 evaluate(0) 的 scale[0]=1；修改输出为99；再次 evaluate(0) 得99，且缓存实例身份相同。TransitionQueue 在空队列、起点和终点直接返回内部数组；中间 lerp 通常创建新数组，因此风险集中在容易被认为纯输出的边界。
- **置信度**：高：共享状态污染已复现；当前内置调用方的故障可达性有限，主要是契约/扩展风险。
- **推荐修复**：明确内部与外部所有权，构造及输出边界防御复制，或改为不可变标量值对象；内部热路径可使用受控 writer 以避免无谓复制。保持公开方法描述符，别破坏冻结调用方兼容性。
- **验证方法**：修改构造输入、accessor 输出及 t<=0/t>=duration 输出后，另一实体和后续求值不变；覆盖反向动画、withHead/withTail 与共享 default 数组；评估新增分配成本。
- **工作量**：1–2 工程师日。
- **回归风险**：中：动画回归面较大，必须保护数值、端点及二进制兼容。
> 开发者置评：未见明显动画故障，暂不修复
## HALO-010

**P2 · 世界佩戴存储使用线性列表，实体加载形成乘积扫描**

- **状态 / 分类**：已确认；性能 / 架构 / 可维护性。
- **位置**：[`src/main/java/network/azusake/halo/lifecycle/HaloWorldSaveData.java:127–169`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/src/main/java/network/azusake/halo/lifecycle/HaloWorldSaveData.java#L127-L169)；[`src/main/java/network/azusake/halo/lifecycle/EntityHaloTracker.java:185–190`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/src/main/java/network/azusake/halo/lifecycle/EntityHaloTracker.java#L185-L190)；[`src/main/java/network/azusake/halo/manager/HaloManager.java:88–94`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/src/main/java/network/azusake/halo/manager/HaloManager.java#L88-L94)。
- **复现条件**：世界有 N 条持久佩戴记录，加载 E 个 living entity；每个 ENTITY_LOAD 都查一次世界记录，包括没有光环的实体。大量新佩戴 upsert 也扫描已有列表。
- **影响**：查询成本 O(E×N)，连续新增 upsert 成本 O(N²)。玩家离线与未加载实体的所有权有意保留，因此 N 可超过当前活跃数量；加载区块时容易集中付出成本。没有游戏 TPS 基准，不能给出毫秒或卡顿阈值。
- **证据**：用未修改 HaloWorldSaveData 加轻量平台桩编译，将 entries 替换为只计数的 ArrayList 子类。10000次不同 upsert 观察49995000个被扫描条目；2000次无主查找对10000条记录观察20000000次遍历。它验证操作量，不是完整 Minecraft 性能测试。
- **置信度**：高：生产循环及精确操作次数已验证；可见延迟仍取决于真实世界规模。
- **推荐修复**：内存用 UUID→Identifier 的 Map 建索引，保留现有 NBT Halos list 字节/字段契约；明确重复 UUID 的迁移决策，dirty 标记和有序输出策略。合并实体 restore 的重复查找，避免每 tick 构造仅用于遍历的完整诊断对象。
- **验证方法**：对固定规模比较查询/upsert 操作复杂度，旧 NBT 往返、重复项、死亡/卸载/跨维度/重启回归；之后在隔离大世界测加载耗时和分配率，不用微基准替代 TPS 证据。
- **工作量**：1–2 工程师日。
- **回归风险**：中：旧存档、重复 UUID 语义与持久化顺序需保护。
> 开发者置评：实体自己带来的影响比光环大的多，暂不予修复
## HALO-011

**P2 · 同代际视觉资产缓存不驱逐已移除的模型**

- **状态 / 分类**：已确认；资源释放 / 内存 / 长会话。
- **位置**：[`core/src/main/java/network/azusake/halo/core/runtime/VisualAssetLoader.java:24–44`](https://github.com/AzusaKe/HaloCore/blob/a22160a842e15fffb1bc03b5b189183557f6d510/src/main/java/network/azusake/halo/core/runtime/VisualAssetLoader.java#L24-L44)；[`src/main/java/network/azusake/halo/render/HaloMeshResources.java:75–87`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/src/main/java/network/azusake/halo/render/HaloMeshResources.java#L75-L87)。
- **复现条件**：整合单人服务端定义变更，在客户端 tick 中刷新依赖，客户端资源代际不变；多轮切换到不同 model/texture ID，再撤销这些定义。
- **影响**：新 VisualResources 只发布当前依赖，但 loader 的成功/失败缓存不删除旧 key，持续持有历史 TriangleMesh 数组及失败记录，直到完整客户端资源重载替换 loader。GPU cache 对移除资源有释放，不能因此认为 CPU 资产已释放。
- **证据**：实际 VisualAssetLoader 同代际依次加载40个不同 OBJ，最后加载空依赖；发布 meshes=0，反射检查生产 models cache 仍有40个值。反射仅用于观察，未改变缓存行为。适配器 refreshDefinitions 复用 loader；完整 reload 才新建 loader。
- **置信度**：高：历史引用保留已复现；真实大资源会话的内存增长幅度未测。
- **推荐修复**：为 active dependencies 提供驱逐/限额策略，或在 source 定义代际变动时创建受控缓存代际。保留共享资产重用与失败去重，但为历史成功/失败记录设置清晰生命周期。
- **验证方法**：重复 A→B→空依赖，断言移除的模型可回收、缓存受限；共享资源保留、失败资产诊断次数、资源重载及 GPU generation 对齐回归。
- **工作量**：1–1.5 工程师日。
- **回归风险**：中：防止因过早驱逐而每帧/每 tick 重读资源，不能把 I/O 放回 draw。

> 开发者置评：暂不修复，影响不大
## HALO-012

**P2 · 世界实体捕获的 begin/end 对非 LivingEntity 不对称**

- **状态 / 分类**：疑似风险；嵌套渲染 / 生命周期 / 上下文。
- **位置**：[`src/main/java/network/azusake/halo/physics/RenderHeadCapture.java:98–130`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/src/main/java/network/azusake/halo/physics/RenderHeadCapture.java#L98-L130)；[`src/main/java/network/azusake/halo/mixin/EntityRenderContextMixin.java:17–37`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/src/main/java/network/azusake/halo/mixin/EntityRenderContextMixin.java#L17-L37)。
- **复现条件**：一个 living entity 的 renderer/feature 内嵌套调用 dispatcher 渲染非 living entity，随后还需继续提交外层模型锚点。具体可触发的模组/游戏场景未找到实测证据。
- **影响**：非 living 的 begin 不入 world scope，RETURN 却无条件 pop 和清除外层 ThreadLocal；外层后续 anchor 提交可能被拒绝，单值 CURRENT_ENTITY/CURRENT_MODEL 也不支持栈式恢复。预览路径另有独立的成对实体栈，不能将其正确性推到 world 路径。
- **证据**：静态分支确认 begin 仅对 LivingEntity push，end 对所有 dispatcher render 均 pop。实际 coordinator 模拟对应调用顺序，外层 submit 从true变false；未调用 Minecraft renderer，因此只证实状态机条件，未证实实际画面故障。RETURN 注入也不能保证异常路径执行。
- **置信度**：中：失衡条件明确，真实嵌套场景及异常恢复未验证。
- **推荐修复**：先确认真实嵌套调用链；使用每次 render 的 token/栈式作用域，确保只关闭自己打开的作用域并恢复上一层上下文。设计异常路径的 finally 等价保障，避免仅依赖 RETURN。
- **验证方法**：在受控测试 renderer 中覆盖 living→nonliving、living→living、preview 嵌套、异常抛出和返回；观察外层提交仍有效，辅助 draw 不污染外层，下一帧恢复。
- **工作量**：1–2 工程师日（含验证）。
- **回归风险**：中至高：Mixin 调用点、第三方兼容和作用域恢复互相影响，确认前不要大范围重写。

> 开发者置评：无法从静态分析得出结论，需要在客户端尝试复现
## HALO-013

**P2 · 关键输入与真实上传路径未进入可阻断的测试门禁**

- **状态 / 分类**：已确认；测试 / CI / 构建可靠性。
- **位置**：[`src/test/java/network/azusake/halo/render/MeshUploadBufferSizingTest.java:38–60`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/src/test/java/network/azusake/halo/render/MeshUploadBufferSizingTest.java#L38-L60)；[`src/test/java/network/azusake/halo/network/HaloPacketCodecTest.java:17–40`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/src/test/java/network/azusake/halo/network/HaloPacketCodecTest.java#L17-L40)；[`src/test/java/network/azusake/halo/compat/ysm/YsmReleaseSignatureTest.java:26–30`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/src/test/java/network/azusake/halo/compat/ysm/YsmReleaseSignatureTest.java#L26-L30)；[`.github/workflows/gradle.yml:42–49`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/.github/workflows/gradle.yml#L42-L49)。
- **复现条件**：开发/CI 运行现有单元测试和 build；没有实际 GL 上下文、C2S defs_report 错误输入或必需的外部兼容包。
- **影响**：绿色构建不能挡住 HALO-001/003 等已复现错误。上传 sizing 测试复制错误公式并断言6次“写入”，没有读取第4个顶点；Math.max(256, ...) 让容量断言通过。YSM 签名测试在缺少环境变量时按设计跳过。
- **证据**：主线 CI 对本次基线是成功；core 的336项本地测试也全通过；随后隔离探针仍找出上述问题。现有网络测试集中在合法旧字节布局。CI 源码未设置 HALO_YSM_TEST_JAR；因此缺少该变量的运行不能宣称验证了官方包签名。本次未读取 CI 测试明细，不虚报远端跳过数。
- **置信度**：高：测试实现和工作流缺口明确；测试覆盖百分比未知，未配置覆盖率工具。
- **推荐修复**：抽出可测试的生产输入解析和上传计划，使测试消费实际生成顶点/索引；错误边界先加回归用例。建立轻量的隔离游戏/GL smoke 检查及可选择但报告明确的第三方 ABI gate；发布验收需要记录跳过原因和版本矩阵。
- **验证方法**：以 HALO-001/003/004/007 的失败输入证明新测试先红再绿（修复阶段）；正常/失败上传都核查资源释放；CI 明确报告测试、兼容、游戏 smoke 和跳过项目。
- **工作量**：2–4 工程师日（不含全组合游戏矩阵）。
- **回归风险**：低至中：测试须覆盖行为，避免继续镜像实现或要求不可合法分发的第三方包。

> 开发者置评：我已列入代办，暂不执行
## HALO-014

**P3 · 构建依赖缺少校验清单，CI actions 使用可变标签**

- **状态 / 分类**：改进建议（配置缺口已确认）；供应链 / 可重复构建。
- **位置**：[`gradle/wrapper/gradle-wrapper.properties:1–7`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/gradle/wrapper/gradle-wrapper.properties#L1-L7)；[`core/build.gradle:7–12`](https://github.com/AzusaKe/HaloCore/blob/a22160a842e15fffb1bc03b5b189183557f6d510/build.gradle#L7-L12)；[`.github/workflows/gradle.yml:27–40`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/.github/workflows/gradle.yml#L27-L40)。
- **复现条件**：新环境重新获取 Gradle、Maven 依赖和 CI actions；依赖镜像或 action 标签发生变化。
- **影响**：已锁定 core gitlink 和直接依赖版本，但没有 wrapper distributionSha256Sum、Gradle dependency verification/锁文件、actions commit SHA。构建不具备完整的内容级依赖校验。没有证据表明依赖已被篡改或存在具体 CVE。
- **证据**：两个 wrapper properties 均只固定8.8 URL；已跟踪文件与构建配置未见 dependency verification、dependency locking 配置。GitHub workflow 使用 checkout@v4.1.7、setup-gradle@v3、upload-artifact@v4 等标签。归档时间戳/顺序、core 远端可取性及分发内容检查已存在，不应忽视这些已有保护。
- **置信度**：高（缺口）；实际供应链攻击概率/漏洞状态未知。
- **推荐修复**：逐步建立 wrapper checksum、依赖内容校验和合适的锁定策略；高权限 action 固定经审阅的 commit SHA。普通构建用最小权限，Release 写权限按任务隔离。维护更新流程，避免无更新策略的永久冻结。
- **验证方法**：全新缓存构建、校验内容不匹配时拒绝、更新依赖后的可审阅差异；tag/release 核查不得改变 core 指针与成品类可见性。
- **工作量**：1–2 工程师日。
- **回归风险**：低至中：动态 Loom/Minecraft 下载链需要先证明校验兼容，避免首次解析即大量无意义失败。

> 开发者置评：暂不予理会，防御性过强
## HALO-015

**P2 · 自制命令包固定空聊天确认状态，在线聊天场景兼容性未验证**

- **状态 / 分类**：疑似风险；网络协议 / API 契约 / 多人稳定性。
- **位置**：[`src/main/java/network/azusake/halo/client/FabricHaloCommandInterceptor.java:236–251`](https://github.com/AzusaKe/Halo/blob/49b822c037606faaf0bb8ba2adba7b63ce0d62bb/src/main/java/network/azusake/halo/client/FabricHaloCommandInterceptor.java#L236-L251)。
- **复现条件**：在已收到/确认签名聊天的多人连接中，Fabric 客户端命令被转发到服务器；随后交替执行普通聊天、普通命令和 /halo 命令。
- **影响**：自制 CommandExecutionC2SPacket 始终使用 offset=0、空 BitSet、空参数签名，未消费当前 last-seen collector。可能造成服务器聊天确认校验与客户端状态分叉、命令拒绝或断线；这些影响本次均未复现。Halo 当前参数通常不是需签名聊天参数，不能据此直接判定 emptySigs 是错误。
- **证据**：上述固定值由源码确认。对应 Yarn 1.20.1+build.10 的 LastSeenMessagesCollector 文档说明存在客户端的 received-message acknowledgment 状态；文档只是状态契约背景，不是空 ack 必然被拒绝的证据。没有获得服务器 validator 的执行结果。参考：https://maven.fabricmc.net/docs/yarn-1.20.1%2Bbuild.10/net/minecraft/network/message/LastSeenMessagesCollector.html 。
- **置信度**：中低：协议状态绕过已确认，故障判断仅为待验证推断。
- **推荐修复**：先做聊天/命令交替场景测试；若失败，用保持原版确认/签名生成的发送路径，采用防重入标记绕过客户端拦截，而不是重新发明协议状态。
- **验证方法**：隔离在线模式服务端，发送多轮真实签名聊天及 /halo/普通命令，覆盖连接初期、窗口移动、重连、无 Halo 服务端；收集确认校验/断线日志。无需访问生产账号凭据或服务器。
- **工作量**：0.5–1 工程师日验证；若确认，1–2日修复。
- **回归风险**：中：网络发送与 Fabric 命令拦截互相依赖；未确认前不要直接替换现有发送逻辑。

> 开发者置评：需要构造条件确认，但暂未复现