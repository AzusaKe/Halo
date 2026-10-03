# Caustica 许可证与分发边界

核查日期：2026-10-03。范围为本实验分支与锁定的
`rewrite@0cc9d0af4f4118cd26b830084a14f2bafc0d904d`，不替代未来依赖升级的检查。

## 结论与依据

Halo 与 HaloCore 保留 MIT，不需要因本次可选接入整体改为 GPL/LGPL。
Caustica 的 [LICENSE.md](https://github.com/ComfyFluffy/Caustica/blob/0cc9d0af4f4118cd26b830084a14f2bafc0d904d/LICENSE.md)
明确将其项目代码和文档授权为 `LGPL-3.0-or-later`。
同时附带 COPYING（GPL v3）和 COPYING.LESSER（LGPL v3）是 LGPL v3 的组成方式，
不是要求使用者从纯 GPL 和 LGPL 中任选一个。

[LGPL v3](https://www.gnu.org/licenses/lgpl-3.0.html) 第 0 节区分使用接口的应用与库本身，
第 4 节允许组合物采用应用自己的许可，但规定通知、许可证文本和可替换/重新链接等条件。
[GNU 对 Java 的说明](https://www.gnu.org/licenses/lgpl-java.en.html) 也说明使用 LGPL 库
不要求应用采用 LGPL；该说明最初针对 v2.1，具体条件以本次 v3 原文为准。

本结论针对以下当前实现，不是仅凭 `compileOnly` 或“未打包类”得出的通用豁免：

- core 不引用 Caustica；新增保留式计算和数学逻辑保持平台无关。
- Java 适配器调用 Caustica 的场景/资源 API；Mixin 包装现有帧及实体捕获调用，
  读取完成的姿态，没有在 Halo 中替换或复制其帧捕获方法体。
- Halo 分发自己的 Slang 源文件。Caustica shader API 模块由独立安装的 Caustica
  在运行时提供并编译；开发工具输出的链接 SPIR-V 不进入 Halo JAR。
- LabPBR 金属常量属于规范中的数值参数；不将 Caustica 的材质类或 shader API 实现
  复制进 Halo。今后若复制或改写上游实现，必须记录来源并保留适用的 LGPL 授权，不能统一标作 MIT。

## 已完成的分发措施

`META-INF/halo/COMPAT-NOTICES.txt` 包含上游署名、锁定来源、LGPL 使用声明、
替换与调试修改的权利说明。JAR 同时附带未经改写的 GPL v3 和 LGPL v3 文本：
`CAUSTICA-COPYING.txt`、`CAUSTICA-COPYING.LESSER.txt`。
Halo 根 LICENSE、core LICENSE 和模组自身的 MIT 元数据保持不变。

`verifyCausticaIsolation` 检查许可证文件存在，并拒绝 Caustica/LWJGL 实现类、
Caustica 资源/原生库目录以及预编译 SPIR-V 进入 Halo JAR。
该检查保护本次分发边界，不单独证明所有许可证条件已满足。

运行时没有锁定 Caustica 文件哈希或签名。用户可替换独立 Caustica JAR；
本实验适配器需要修改版保留 Java API 0.8.0、Shader ABI 6 以及本次使用的内部帧/实体钩子。
`[0.1.0]` 版本依赖是本次测试范围，接口兼容的修改版可沿用版本号。
接口或钩子不兼容需要调整 Halo 适配器，而不是绕过许可证权利。

编译时 `caustica-lock.json` 的哈希仅保证可复现的已检查依赖。
如需针对自己的 Caustica 构建重新编译 Halo，可用 `-PcausticaJar=<文件路径>`，
在工作副本中明确更新锁定哈希并检查 API/ABI；构建文件和适配器源码均可修改。
源码检查确认没有运行时哈希限制；本轮没有另外构建并运行一份修改版 Caustica。

## 后续改变范围时

若另外分发 Caustica 原版或修改版 JAR，需按 LGPL/GPL 对应源码及分发条件处理，
并核查其 [第三方通知](https://github.com/ComfyFluffy/Caustica/blob/0cc9d0af4f4118cd26b830084a14f2bafc0d904d/THIRD_PARTY_NOTICES.md)：
NVIDIA DLSS/NGX、NRD 与 Slang 具有各自的许可证，不能全部视作 LGPL。
本 Halo JAR 不携带这些组件。

若未来依赖改为纯 GPL、移入上游实现或分发链接 shader 二进制，需重新判断组合物的
授权和源码交付范围；本次维持 MIT 的结论不能直接沿用。许可证文本的存在本身不改变 Halo 的授权。

## 本轮检查

使用 Java 25 执行 `verifyCausticaIsolation verifyDistribution` 成功，确认新通知及许可证
进入 JAR、无上述禁止打包项，且 core 平铺合包和构建来源记录通过检查。
复制到源码资源目录的两份 GNU 文本与锁定 Caustica 文件的 SHA-256 分别一致。
本轮只补文档、许可证资源及分发检查，没有追加游戏或性能测试，也没有修改 Caustica。
