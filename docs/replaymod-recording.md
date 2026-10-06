# ReplayMod 临时录制适配

- 分支：repaymod/1.20.1-fabric；从 1.20.1-fabric 的 2cb61117b8e07e6710c3c39dfcd46544ab0d3dad 创建。
- 平台：Minecraft 1.20.1 Fabric；适配修订 3。
- HaloCore：2.4.2，锁定 1d3cf90478011c1e6f956f1b7dc978d00e796099，源码和 gitlink 均未修改。
- 唯一指定目标：ReplayMod 1.20.1-2.6.23。

## 数据契约

适配层通过 ReplayMod 注入 GameRenderer 的 replayModRender_getHandler 接口读取当前导出任务。
仅当 handler 的 RenderInfo 是 VideoRenderer 时启用合成帧时钟；
普通回放、游玩、未安装 ReplayMod 和截图任务使用正常流逝时间。

动画毫秒时间和物理纳秒时间来自同一个连续时钟。导出中累计时间为
(frame - firstFrame) / RenderSettings.getFramesPerSecond()，与单帧渲染耗时无关；
使用累计帧差换算纳秒，避免非整毫秒 FPS 的逐帧取整漂移。
相同帧编号的时钟采样保持不变。进入、退出、重启导出时保持动画年龄连续，
退出时不补入导出过程中消耗的墙钟时间。ClientRuntime 的生命周期计时也使用这个时钟，
避免渲染时钟和光环创建/隐藏计时来自不同时间域。

ReplayMod 通过反射可选接入，不打包它的类，不添加硬依赖或第三方 Mixin。
版本检查严格限定 1.20.1-2.6.23，桥接错误会记录警告并退回普通客户端计时。

## 验证范围

RenderFrameClockTest 检查 24/30/60/120 FPS 下每帧耗时数秒仍只累计对应的视频时长，
重复采样、普通计时、退出导出以及导出重启的连续性。
已通过 javap 对照用户指定 jar 的接口，以及 fabric.mod.json 的精确版本号。
构建结果见工作目录 build-replaymod.log 和 build/reports/tests/。

按用户的紧急交付要求，仅执行构建与自动检查；未启动游戏，未进行实际 ReplayMod 视频导出验收。
立体/全景多视角渲染与光影组合也未实测。该临时版本不推送远端、不打发布标签。
安装时替换原 Halo jar，不要将两个 Halo jar 同时放入 mods。
