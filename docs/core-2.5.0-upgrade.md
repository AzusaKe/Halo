# 正式 HaloCore 2.5.0 升级 — 1.21.1-fabric

2026-10-09；本轮选用正式标签 v2.5.0 的精确提交 `a22160a842e15fffb1bc03b5b189183557f6d510`。
远端标签已只读核对，未自动追踪 core/main，未改写已发布标签。
目标版本 `2.5.0+adapter.2`，用户于 2026-10-09 确认集中测试通过；生命周期修复保留，各分支专有渲染/录制能力不跨分支混入。

## 契约与责任

2.5.0 新增可选 `ScenePort.renderScene(FrameScene)`，原有 ClientPort、帧构造器、光栅输出兼容。
每个逻辑帧仅调用一个世界入口；普通适配器继续 renderFrame，Caustica 继续其已有 renderScene 分流。
新增场景输出保留稳定绘制键、共享局部几何、double 世界原点和法线矩阵；core 保持 Java 17，无 Minecraft 或加载器类型。
正式 core 的运行及源码 JAR 包含 MIT 许可证；本轮运行成品另检查 ScenePort 和 core 许可证存在。
BA 实验分支按用户指示保留其自己的 core/版本，未纳入正式 core 升级。

## 源码与提交状态

Halo gitlink 记录本轮选定的 `a22160a842e15fffb1bc03b5b189183557f6d510`；core 源码保持正式 v2.5.0 原样。
提交后的正式构建要求 coreCommit、coreLock 均为该 SHA，haloCommit 为实际 Halo 提交，development=false。
本轮不移动既有标签；core 的正式提交已可从远端取得，Halo 随后提交/推送 gitlink。

## 验证

- wrapper 联合 build：通过；Halo 129 项用例，失败/错误 0，跳过 1；生命周期 5/5 通过。core 报告 336 项，失败/错误 0，跳过 0，未改动任务可能使用已有结果。
- 既有 core 行为与新 retained scene、旧接口二进制调用方、平台构建及五项生命周期行为：随 build 验证。
- 实际游戏：用户于 2026-10-09 确认本轮集中测试通过；不扩大为未测试的画面组合或性能验收。
- Flashback 初始快照兼容：仍未实施。

跳过的外部签名检查：

- network.azusake.halo.compat.ysm.YsmReleaseSignatureTest: official YSM 2.6.5 jar retains every pinned adapter signature
