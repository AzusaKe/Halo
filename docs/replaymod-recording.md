# ReforgedPlayMod 临时录制适配

- 分支：replaymod/1.21.1-neoforge，基于 1.21.1-neoforge 的 4a1d086。
- HaloCore：2.4.2 / 1d3cf90478011c1e6f956f1b7dc978d00e796099，源码和 gitlink 未修改。
- 平台：Minecraft 1.21.1 NeoForge；适配修订 2。
- 唯一目标：reforgedplaymod-1.21.1-0.3.jar，其实际 modId 为 reforgedplaymod，版本为 0.3；
  jar 另声明 replaymod 2.6.18，检测使用前者。
- 本次构建使用 -Pneo_version=21.1.222，对应用户录制环境；不改变分支默认构建基线。

## 数据契约

通过 ReforgedPlayMod 注入 GameRenderer 的 replayModRender_getHandler 接口读取导出任务。
仅当 handler 的 RenderInfo 是 VideoRenderer 时启用合成帧时钟。
动画毫秒时间、物理纳秒时间、客户端生命周期计时使用同一个连续时钟；
导出时间差为 (frame - firstFrame) / RenderSettings.getFramesPerSecond()，
与单帧渲染耗时无关。累计帧差换算纳秒，避免非整毫秒 FPS 的逐帧取整漂移。
相同帧编号的时钟采样保持不变；进入、退出、重启导出保持动画年龄连续，
退出时不补入导出消耗的墙钟时间。

普通游玩、普通回放、截图任务和未安装目标模组时，使用正常流逝时间。
反射接入无硬依赖，不打包录制模组的类，不引入第三方 Mixin；
桥接失败记录警告并退回普通客户端计时。仅平台输入层有变化。

## 验证范围

通过 javap 对照指定 jar 的渲染接口、帧时间实现，并核对 neoforge.mods.toml 的模组 ID/版本。
RenderFrameClockTest 验证 24/30/60/120 FPS、慢速导出、重复采样、普通计时、
退出和重启导出的时间连续性。构建日志见工作目录 build-replaymod.log；
自动测试报告见 build/reports/tests/。

按用户紧急交付要求执行构建与自动检查，未启动游戏，未实测 ReforgedPlayMod 视频导出；
全景/立体多视角以及光影组合亦未实测。不推送远端，不打发布标签。
安装时替换原 Halo jar，不要同时保留两个 Halo jar。
