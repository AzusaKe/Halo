# Minecraft 1.20.1 Forge 迁移验收记录

记录日期：2026-09-18。此文档区分自动门禁、启动冒烟和未执行的目视/多人验收；未执行项不视为通过。

## 锁定版本

- Halo 基线：`51f684b0d8facb2ad70f64d9d6f0711235ae4158`
- HaloCore：`224551ef3f76c36b60fd5c84caaf4fe8a71b5e3a`（2.4.0，未修改）
- Minecraft：1.20.1；Java：17；ForgeGradle：6；Mojmap：`1.20.1`
- Forge 最低验证版：47.4.10；目标验证版：47.4.23
- YSM：`2.6.5-forge+mc1.20.1-release`，SHA-256 `25B5E902B96F4C298690208F8B433CBC31737C23F87590354DBD86F00207BC8F`
- EMF：Forge 3.1.1（CurseForge 文件 7938793），SHA-256 `AF720271ED14779F2465D0035A6DB8237C186778E6ED302D4AEC9BCA1D22CF22`
- ETF：Forge 7.0.6，SHA-256 `93C5809268B8A7752E6CCCA49A611E61368365DFE5647EC31EE263D2D95CF532`
- Oculus：1.8.0；Embeddium：0.3.31；Bliss：2.1.2，SHA-256 `F41DB92ACC585FE9FFE0CC124D6ECAEA27CEF9DED91129EBC2A8E60226947B2C`

## 已执行

- Forge 47.4.10 `clean build`：通过；包括 Halo/HaloCore 测试、reobf、合包内容、core 唯一副本、来源文件、专用服类加载及 API v2 外部调用方门禁。
- Forge 47.4.10 专用服启动：到达 `Done`。
- Forge 47.4.23 专用服启动：到达 `Done`。
- Forge 47.4.23 原生客户端：完成 Halo 初始化、资源重载及 mesh generation 上传；无 Halo Mixin 或类加载失败。
- Oculus 1.8.0 + Embeddium 0.3.31：无光影客户端启动并上传 mesh generation。
- Oculus + Bliss 2.1.2：日志确认 flat、lit-solid、lit-translucent 三套材质建立，并在管线切换时释放及重建 generation。
- 官方 YSM Forge JAR 签名门禁：通过，未跳过。
- 官方 EMF Forge JAR ABI 门禁：通过，未跳过；确认 3.1.1 版本、`EMFModelPart` 渲染入口及 `EMFModelPartVanilla.name`。

EMF 3.1.1 + ETF 7.0.6 在 ForgeGradle 的 `forgeclientuserdev` 中会因 EMF 自身发布 JAR 未带开发命名空间 refmap，在 EMF 的 `MixinResourceReloadStart` 处失败；在该失败之前 Halo 已记录 `verified EMF 3.1.1 detected`。因此这里只记为 Halo ABI/Mixin 选择门禁通过，不把 EMF 开发客户端启动或实际捕获记为通过。

## 尚未验证

- 专用服加两个客户端的完整多人流程，以及 Halo 客户端连接无 Halo 服务端、Halo 服务端接受无 Halo 客户端的实际联机。
- 全部命令、权杖 GUI/会话、持久化、后加入/重连、死亡重生、卸载、跨维度与全部姿态的游戏内操作矩阵。
- YSM/EMF 的世界与物品栏预览实际模型捕获；EMF 仍需带替换玩家模型的资源包。
- BSL、IterationRP，以及 LabPBR 三图元的法线、smoothness/metalness、emission 像素级目视结果。
- billboard/ring/OBJ 全组合、大坐标、透明排序、F3+T 与 `compatibility`/`cached` 热切换的逐项截图比对。
- GPU 分析器下的稳定帧上传次数与 EBO 更新次数；自动测试仅覆盖相应缓存和 generation 不变量。

最终 JAR、哈希、构建日志和本记录副本保存在 `F:\codex-cache\halo-1.20.1-forge-verification`。
