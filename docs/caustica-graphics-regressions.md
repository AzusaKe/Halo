# Caustica 实验后端：画面回归记录

2026-10-03，MC 26.2 / NeoForge 26.2.0.59；Halo 2.5.0 + adapter 1 开发包。
Halo 基线 `9a722a19bd1cbbc84c6cb69bea5e7f2f79daa579`，core 基线
`1d3cf90478011c1e6f956f1b7dc978d00e796099`。收尾提交的 core 为
`bc2b7b5e24ff51919f3177bfeb4d75bd4c55fe24`；Halo 最终 SHA 由包含本记录的 Git 提交确定。
Caustica 为未修改的 `rewrite@0cc9d0af4f4118cd26b830084a14f2bafc0d904d`。
隔离实例及原始证据放在 `F:/codex-cache/caustica/validation`。

## 日间场景全白

测试脚本曾在正午设置 `exposure.mode=manual`、`exposure.manual-ev=0`，导致场景曝光饱和。
恢复 `exposure.mode=auto` 后，石台、天空和光环恢复正常显示。脚本已经修正。
全白不是预期场景，不作为 Halo 或 Caustica 材质效果；`performance/aa-01` 样本已标为无效，
不能据此给出性能结论。

## 实体退出客户端跟踪范围后，光环不恢复

在同一已加载服务端区域内，移开约 200 格再返回，旧实现的 RT 实例数从 1 变成 0，返回后仍为 0；
服务端佩戴 NBT 保留。移开约 1000 格造成服务端区块卸载、重新恢复实体时，新的挂载通知又能恢复光环。

`EntityLeaveLevelEvent` 原先根据 `!living.isAlive()` 调用 `ClientRuntime.died`。
Minecraft 已移除的健康客户端实体也会报告 `isAlive() == false`，于是非玩家的客户端权威副本被删除。
现在用 `isDeadOrDying()` 判定明确死亡；普通卸载仅调用 `unload`，保留佩戴记录供再次跟踪恢复。

自动测试覆盖健康实体卸载保留记录、明确死亡撤销非玩家记录，以及玩家死亡保留重生记录。
实际游戏连续三轮跟踪范围往返全部得到 `1 → 0 → 1`；几何/纹理累计上传量与 BLAS 准备次数不变。
证据：`evidence/lifecycle/tracking-fixed-results.json` 与对应七份 JFR/摘要。
重复方式：`python tools/caustica/lifecycle.py <隔离实例目录> <证据目录>`。
脚本使用已经布置好的 `halo_rt_bench` 标记实体，不应对用户原存档执行。

## Yuuka / Toki 强光下灰白色覆盖基础色

这两个资源的 `_s` 金属区域使用 G=230（预定义铁）；Mika 的检查区域使用 G=10（非金属 F0）。
旧 shader 给预定义金属赋值其计算得到的反射率，直接替换 `base_color`，遗失基础纹理染色。
[LabPBR 规范](https://shaderlabs.org/wiki/LabPBR_Material_Standard#How_metals_work)
要求预定义金属用 albedo 给反射染色。因此修正为线性 ACEScg 基础色乘以预定义金属 F0。
Caustica 的 OpenPBR 金属 `base_color` 表达 F0，这个错误发生在 Halo 的转换处。

同时修正法线贴图的切线变换：切线、位切线跟随实例线性变换，基础法线跟随显式法线矩阵；
Halo 的 V 已向下，DirectX 法线的绿色沿 `dP/dV`，不再重复翻转绿色或 OBJ 的 V。
当前及前一帧使用各自的变换。

材质前后对照只切换上述金属染色表达式，保留其他修正，使用同一隔离存档、摄像机、正午/侧光时间、
自动曝光、关闭帧生成，以及 lit/albedo 视图。控制包是验证用变体，不是可交付版本。
截图与状态记录在 `evidence/material`；`tools/caustica/material_capture.py` 可重复相同输入。
有效对照为 `untinted-verified-*` / `tinted-verified-*`，运行前断言只有一个 Halo JAR，
记录该文件 SHA256，并核对 shader 表达式。最初的 `untinted-control-*` 因重复 JAR 被加载器
选成修正版，已经明确作废，不能用于比较。
八对截图的摄像机和设置快照一致；修正版包 SHA256 为
`3e42b1fbea22e7e7b7b852e3e7c9fdaf1802198ec9e9078b93644dfae433ee43`，
单公式控制包为 `db948044a615fee5c2e2f643cbf2d5da97d66d516afce147111c58a82ac0ef71`。
只去掉染色的有效控制包重现 Yuuka 大片灰白反射，以及 Toki 上、下金属弧的灰白色；
修正版在相同视角与时间保留深色外壳及蓝色纹理。这里有 Halo 适配器错误的直接游戏证据，
无需修改 Caustica 或将该问题归因于它。

Caustica 当前的公开 OpenPBR 子集没有导体 F82 边缘染色；其 Fresnel 近似在掠射角仍趋向白色。
此限制与此次丢掉基础色是不同问题。修正不承诺所有高光、掠射角或曝光条件都不会变白，
也不通过降低反射强度掩盖正常受光。
截图只能证明指定场景，不能据此宣称所有 LabPBR、POM、材质 AO 或湿润效果已验收。

## Seia halo mesh 的黑边

单独保留 `model:seia` 的 halo 组，使用原 OBJ、纹理和定义变换，仍能重现黑边；
纹理没有黑色 texel，原始 trace-radiance 视图也有黑边，因而不是角色其他组遮挡或仅重建产生。
部分 OBJ 平滑法线与三角形法线夹角很大。把平滑法线放到几何法线的半球内，
仍不能保证它朝向当前射线。Caustica 的 BSDF 在 `dot(Ns,V)<=0` 时把受光项算成零。
这种情况只剩下远低于正午照度的自发光，看起来像黑色描边。

第一次修正仅对上述无效朝向回退到 Caustica 提供的、朝向射线的三角形法线，
并对上一帧采用同样的回退选择。有效的导入法线和法线贴图保留；没有修改资源或增加描边。
这是对极端平滑法线的实验容错，临界朝向存在法线切换，不能据此宣称与光栅平滑插值完全相同。

固定 1920×1080、相机 `(3,113.12,7.2)`、yaw=180、pitch=0、时间 6000，
定义、资源及曝光设置一致。对象区域 `[745,205]..[1175,535]` 中，PNG 最大 RGB 小于 55
的像素从 5535 降为 0；修复前其中约 93.5% 的诊断法线背向视线。
两次 preExposure 分别为 `6.034002e-5` 和 `6.0334314e-5`，无非有限法线值。
该像素统计有低分辨率诊断缓冲对齐误差，不用作全模型质量指标。
截图、原始 EXR、投影元数据与统计在 `evidence/graphics2/seia-*`、`graphics-analysis.json`。
修复包 SHA256：`2ff4e90c736cea6a9f443ae8a235d163f41c85654951da2924122da7263cac79`。
与上一修正版按 ZIP 条目内容比较，仅 `halo_surface.slang` 不同；记录为 `seia-jar-diff.json`。
可用 `graphics_capture.py` 在已布置的隔离实例重现，`graphics_analysis.py` 复算读图统计。

## Seia 同色块：连续法线修正

用户继续观察背面斜视角时，第一次修正产生新的突兀同色块。正常平面受光均匀本身不是错误，
但此次部分区域被硬回退为同一个三角形法线，形成了人为边界。这是前一修正的副作用；
第一次修正包只保留为对照，已被本节修正版替代。

本轮只继续检查 Seia，不再探究 Toki 或 Yuuka。先在用户原视角记录合成、法线诊断和
原始 trace-radiance，再用同一存档、资源、时间 6000 和固定相机进行新旧 JAR 对照。
`hard-back` / `smooth-back` 的玩家位置为
`(2.7707311799230094,112.05515974718541,5.0635223159544624)`，
yaw=-369.9654、pitch=19.622818；`hard-front` / `smooth-front` 为上一黑边复现视角。
四次截图均使用自动曝光、相同的 RT/重建设定和关闭帧生成。

新 shader 先按三角形本身确定正反面，避免根据插值法线的符号在面内突然翻转。
随后保留着色法线在几何面内的方向，只在无效的方向上连续地向几何法线收拢，
取最小旋转角，使镜面反射位于三角形上方。设法线在 `Ng` 和切线方向 `x` 的平面内，
`n'=sin(theta)*x+cos(theta)*Ng`，求解
`Vz*cos(2*theta)+Vx*sin(2*theta)=0.05*Vz` 的上界根。
这是直接从反射方程推导的闭式计算，无每像素迭代；满足约束的法线不变。
有关几何法线与着色法线的半球差异，见
[PBRT 的说明](https://pbr-book.org/4ed/Reflection_Models/BSDF_Representation)。
同一修正系数用于前一帧的法线和几何法线，保留实例旋转/变换造成的正常法线运动。
当前 API 没有前一帧 outgoingDirection，因此这不是独立重算上一帧视线约束的完整历史求值。

指定背面视角中，原先集中在 `(0,0.765625,-0.642578125)` 附近的诊断像素
从 4070 降为 0，法线诊断和合成画面均显示渐变代替突兀色块。
自动曝光 preExposure 为 `5.067746e-5` / `5.0520386e-5`，差约 0.31%，
没有通过改变纹理、材质或曝光掩盖边界。前面黑边视角的同一 PNG 阈值统计仍为 0。
两个固定视角的横向相邻法线跳跃（欧氏差 >0.4）从 586/888 降为 161/260；
此统计包含模型自身的边缘，不是平滑程度或正确性的通用验收指标。

另采集横向移动的七个相邻视角，检查了首、中、末的合成截图；原始诊断均为有限值，
对象区域没有背向视线的着色法线（考虑 EXR/像素对齐误差以 -0.001 为阈值）。
这是有限视角的抽样，未进行全视角视频、所有镜像/非均匀动画或其他资源法线贴图的游戏验收。
证据在 `evidence/graphics3`；`normal-analysis.json` 与 `normal_analysis.py` 可复算统计。
七视角路径及元数据由 `normal_sweep.py` 记录，结束后恢复用户原相机、创造模式、正常视图与自动曝光。

新包 SHA256 为 `6b67a70023dc302aaa9b69d3d83aa27f8b1a81eaddaa9708bbcdea6d118b99a4`。
新旧包按 ZIP 条目内容比较仅 `halo_surface.slang` 不同，记录在 `normal-jar-diff.json`。
联合构建通过，测试记录仍为 core 333 / Halo 146，无失败或跳过；Slang 编译通过，
输出 18408 字节 SPIR-V，世界 shader 在普通 NeoForge 实例实际编译并运行。
本轮没有新增 core 变更，两个仓库的完整基线 SHA 见本文开头；未提交、未推送。
Caustica 源码保持干净，性能采样继续暂停。

## Yuuka 夜间蓝色发光变白

使用同一相机和资源，对比白天 6000、夜间 18000，以及夜间降低曝光的控制。
未曝光的 `nrd-stable-radiance` 中，蓝色发光像素的 ACEScg 中位数三次均为
`[22.6875,41.4375,91.8125]`；Halo 的发光颜色没有在夜间改变。
自动曝光的 preExposure 从白天 `5.950157e-5` 增至夜间 `0.15846512`，约 2663 倍。
显示阶段经 Caustica 的曝光和 ACES 输出转换后，明亮蓝色接近白色。
夜间手动曝光 `-7`（此实现的曝光倍率为 `2^EV`，不应与相机 EV100 混用）
恢复蓝色，原始发光值仍相同。控制截图同时很暗，不能把它当作推荐的全场景曝光。

证据在 `evidence/graphics2/yuuka-{day-auto,night-auto,night-manual-minus7}*`。
另一次 `manual8` 是增加曝光的饱和诊断，不作为恢复颜色的控制。
本次现象发生在 Caustica 的显示处理阶段，与之前 Halo 丢掉金属染色的问题分别记录。
没有改动 Caustica，也没有在 Halo 中随时间降低亮度或补偿色调映射。
若要求夜间始终保持蓝色显示，需要另行选择曝光、发光强度或输出转换策略；
这些选择会改变屏幕亮度或物理发光行为。

## Toki 波浪没有出现

当前实际启用的是 `Toki-LabPBR-Parallax.zip`。其中 `toki_3_n.png` 为 694×687，
全部 476778 个像素的 R/G 都是 128；波浪数据存于 A，范围 64..255，B 恒为 255。
[LabPBR 规范](https://shaderlabs.org/wiki/LabPBR_Material_Standard#Normal_Texture_(_n))
定义 R/G 为法线方向、A 为高度/位移，因此该资源没有波浪法线方向数据。
这不是这张贴图的法线采样失败：本轮保证 RG 法线映射，但明确未覆盖 POM/高度位移。
没有擅自从高度生成法线或修改用户资源。要显示该波浪需要增加高度效果，
或由资源提供真正变化的 RG 法线；二者不是同一种视觉效果。
资源直读统计在 `evidence/graphics2/labpbr-audit.json`，可用 `labpbr_audit.py` 复算。
这也不能替代具有实际变化 RG 数据的法线贴图游戏验收。

## 收尾：小缩放的保留式法线矩阵

收尾复核发现新 ScenePort 的 mesh 法线计算沿用了 `abs(det)>1e-8` 判断，
普通 billboard/ring 在未提供显式法线时也经由旧 PrimitiveDraw 的同一阈值。
小的可逆缩放会因此错误地产生 identity 法线矩阵。
现只在保留式输出中使用 double 计算行列式与逆转置，再检查转换后的 float 矩阵是否有限。
不使用固定行列式绝对阈值，也不修改旧光栅输出的计算规则。
face_camera 的显式法线不经此计算；真正的奇异矩阵仍保留原始奇异放置，
由适配器拒绝并诊断，不伪造可逆变换。无法表示为 float 的法线矩阵直接诊断。

新增三项 RetainedSceneTest 回归覆盖旋转、镜像、非均匀小缩放、float 行列式下溢、
billboard/ring 的逆缩放、显式 facing 法线，以及零缩放。
其中两个测试在旧实现复现失败；修正后与原三项 ScenePort 测试一起通过。
core 独立 build（Java 21 运行、Java 17 编译契约）通过；目标联合 build 通过。
本轮筛选执行 core 6 项及 CausticaMaterialTest 3 项，均无失败或跳过，
并执行边界、旧调用方及包内容检查；没有重跑其他游戏功能或性能测试。
开发包已重建，当前运行中的游戏没有自动替换或重启。
该修复随本轮实现一并提交；core 先提交，Halo 随后记录 gitlink。基线 SHA 保留供差异比较。

## 验证边界

目标联合构建、旧 API 调用方兼容检查和修正后的 Slang 编译已通过；实际游戏加载并编译了世界 shader。
此前完整测试记录为 core 333 项、Halo 146 项，均无失败或跳过；本轮筛选测试见上节。Slang 2026.14.1 编译输出
18408 字节 SPIR-V，伴随自动补充 profile capabilities 的警告；实际世界 shader 同样编译成功。
旧编译检查只消费 base color / coverage；现已加入法线、前一帧法线、发光和材质输出，
使用可逆镜像/非均匀变换，避免编译器删除未消费的法线路径。编译检查本身不执行 GPU shader。
性能采样因画面回归暂缓，完整配对采样尚未执行。专用服、缺少 Caustica 的实际加载、EMF、
全部换维度/重载及透明覆盖组合仍需独立记录，不能用本次两项回归替代。
本记录不代表发布验收，没有推送、标签或 Release，也没有发布外部 issue。
实际实例已经恢复修正版，留下 Toki、Yuuka 与独立 Seia halo 组供查看，保持自动曝光和正常合成视图。
性能恢复前应把 `halo_rt_bench` 全部恢复为 Invisible/Marker，并回到固定测试镜头；
本次近距离材质截图场景不能直接与旧性能样本混用。
