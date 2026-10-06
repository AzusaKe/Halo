# 保留的 BA 实验资源

## 评估与保存范围

2026-10-06：用户确认已测试的 BA 外观通过，决定暂不将人物材质移入 core/适配器
主线。功能定位不符合以光环为主的模组，且接入影响普通 LabPBR，造成材质错误。
该兼容问题尚未修复。描边和地面模型投影保留为未来独立适配、回归后的主线候选。
详细结论见仓库 README；不要将本目录光影用于声称正式版兼容。

- 已保存：[Halo BA Experimental (Chocapic13' Shaders edit).zip](<shaderpacks/Halo BA Experimental (Chocapic13' Shaders edit).zip>)，
  即最终 v4 / adapter.3 的独立光影。
- 已保存：原文 [LICENSE.md](licenses/Bliss-Chocapic13-LICENSE.md)、
  [CREDITS.txt](licenses/Bliss-CREDITS.txt)及文件 [SHA256 清单](SHA256SUMS.txt)。
- 未上传：Arisu BA Base v1.zip、Arisu BA Companions v1.zip、原始 blend 及参考图片。
  指定 blend 和导出资源中未找到可确认的再分发许可，用户随后明确要求 Arisu 模型
  暂不上传；几何、基础贴图和 Mask/Spac 一并遵循此决定。原本的本地交付包均保留。
- [ASSET-MANIFEST.json](ASSET-MANIFEST.json)仅记录来源、文件名、大小、摘要和保存状态，
  不含 Arisu 几何或图像。历史本地成品和公开分支附件的范围不同。

## 光影来源与许可

上游 **Bliss v2.1.2 / release11**，作者 X0nk；基于 Chocapic13 的光影修改版。
[上游源码](https://github.com/X0nk/Bliss-Shader/tree/release11)、
[上游许可证](https://raw.githubusercontent.com/X0nk/Bliss-Shader/release11/LICENSE.md)、
[Chocapic13 官方页面](https://www.curseforge.com/minecraft/customization/chocapic13-shaders)。
底包 SHA256 为 `f41db92acc585fe9ffe0cc124d6ecaea27cef9ded91129ebc2a8e60226947b2c`。

修改内容包括专用实体前向材质、Mask/Spec、曝光适配、光环全局自发光和后续合成标识。
完整上游分享规则随光影 ZIP 和本目录附带：保留 Chocapic13 edit 命名、明确上游署名、
不用盈利跳转链接、不将上游代码宣称为 Halo 原创，并沿用上游修改和分享条件。
允许依照附带规则继续修改和再分发此实验修改版；不附加新的分享限制。
**本目录第三方光影及其派生内容不受 Halo 根目录 MIT 许可覆盖。**
再分享时须一并附上完整原许可及署名，并遵守其中关于下载、分享和截图/视频署名的规则。

## 本地复测

安装 `2.5.0-ba.2+adapter.3` JAR，把上述 ZIP 放入隔离实例 shaderpacks 并选择它。
用户自己的、具有使用许可的模型资源包需要独立准备；BA 伴生图协议及复测步骤见
[实验记录](../../docs/ba-experimental.md)、[光环全局发光](../../docs/ba-halo-global-emission.md)。
光影设置 → Halo BA → 光环全局自发光，默认 1.0。普通 LabPBR 当前有用户报告的
回归，不能以此底包替代正式使用的普通材质光影。

恢复光影修改可使用 `scripts/ba/build_shaderpack.py` 和核对过的底包；源码中的本机路径
需在其他机器上调整。附带 ZIP 是实际测试副本，不要求用户从本机缓存重建。
