# 网易 1.20.1 测试适配

- Halo 基线：`1.20.1-forge@7481a6b85206a6ca2af464641e5a5a6b3f33542b`。
- HaloCore：2.4.2 / `1d3cf90478011c1e6f956f1b7dc978d00e796099`，源码和 gitlink 不变。
- 目标环境：本机网易版本配置 `versions/1.20/1.20.json` 中的 Minecraft 1.20.1、Forge 47.3.0、Java 17。
- Forge 构建版本从 47.4.0 降为 47.3.0，依赖范围改为 `[47.3.0,48)`。
- 移除 YSM 世界/预览锚点适配类、Mixin、帧回调、开发运行依赖和对应测试；保留原版、EMF、Oculus 及公共 API v2 路径。
- core 中的历史 YSM 配置字段仍可读写，已有配置不需迁移；本适配器不再使用这些字段执行 YSM 接入。
- 成品前缀为 `halo-1.20.1-forge-netease`，普通未提交测试构建带 `.dev`。

## 验证状态

构建命令：`./gradlew.bat build --console=plain`（Java 17）。

2026-10-08：联合构建成功。Halo 测试 117 项，116 项通过，1 项 EMF 官方发布包签名测试因未提供外部 JAR 跳过；core 测试 330 项全部通过。分发合包、Mixin refmap、新旧 API v2 调用方检查通过。成品元数据确认 Forge 范围为 `[47.3.0,48)`，YSM 适配类和 Mixin 配置数量为 0。用户于 2026-10-08 确认本次修改可用，网易客户端已正常启用。未提供单人/多人、权杖、预览、渲染及资源重载逐项结果，不能将这些矩阵记为全部通过。不创建发布标签或 Release。

成品：build/libs/halo-1.20.1-forge-netease-2.4.2+adapter.3.dev.jar

SHA-256：00F0DF646D067024857270097F75D959AE9A82CC62884EB8286BA7B3BAA5A3A9

维护分支为 1.20.1-forge-netease；core 干净，未修改。CI 的 push/PR 目标及构建版本均切换为网易分支和 Forge 47.3.0。保留冻结的 1.20.1-forge-netease-flash 历史分支。
