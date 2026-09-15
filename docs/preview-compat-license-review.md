# 预览兼容代码范围核对（2026-09-15）

本记录针对 `7493b0c` 之后的 adapter.2 改动，依据维护者在本次会话提供的 YSM 授权范围：可以在源码中包含部分反编译符号，并再现必要的 YSM 计算过程。未取得授权原文，因此这是按所述范围作出的工程判断，不将其表述为无条件法律保证或面向所有人的 YSM 授权。

## YSM 2.6.5

- 实际测试 JAR 的 `fabric.mod.json` 标记 `All-Rights-Reserved`；新版本源码的许可证不用于推断这个旧版本的权限。
- `YsmV265Symbols` 新增同一个准备方法的 intermediary / named 描述符；类名、方法名、Head getter 和骨骼 getter 沿用原适配器。
- 两个 Mixin 在准备方法返回后执行 Halo 自己的捕获回调。隐身时仍由 YSM 执行其准备和动画方法，Halo 不复制、替换或重现这些方法体。
- `YsmPreviewCapture` 使用已有 `YsmV265Adapter.captureHeadMatrix` 和 `YsmHeadMath`，仅把坐标参照改为当前 GUI 根矩阵。既有骨骼组合计算没有新增或改写。
- Halo 源码、成品中没有 YSM 类文件、反编译方法体、模型或纹理；本地分析文件和测试模组只保留在 `F:/codex-cache/`。

因此，本轮新增源码仍落在维护者所述“必要符号及既有必要计算”的范围，未新增 YSM 动画实现的复用。若原授权实际限定了逐项符号清单或另有用途限制，则应以原文为准。

## EMF 3.1.1

实际测试 JAR 标记 `LGPL-3.0`；[上游许可文本](https://github.com/Traben-0/Entity_Model_Features/blob/master/LICENSE)提供 LGPL v3 或更新版本的许可，区分独立调用方与组合发行，并允许符合条件的运行时链接。

- 捕获、作用域、丢弃顶点接收器及取消绘制回调均由 Halo 编写。
- 隐身补丁调用实际安装的 EMF 部件 `render`，由 EMF 自己执行动画；随后在现有钩子停止几何输出。没有复制动画求值器或部件渲染实现。
- 不捆绑 EMF；保留原有版本与 ABI 检查，不限制用户替换为 ABI 相容的修改版本。
- 成品附带兼容告知及上游完整 LGPL/GPL 许可文本。Halo 的独立代码继续使用自己的许可证。

告知位于 `src/main/resources/META-INF/halo/COMPAT-NOTICES.txt`，许可文本位于同目录的 `EMF-LICENSE.txt`。本次没有重分发外部模组或资源包。
