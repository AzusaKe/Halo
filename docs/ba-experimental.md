# BA 实验 / Fabric 1.20.1

## 基线与职责

Halo 从 `1.20.1-fabric@2cb61117b8e07e6710c3c39dfcd46544ab0d3dad`、core 从锁定的
`1d3cf90478011c1e6f956f1b7dc978d00e796099` 建立各自 `Experimental/1.20.1-fabric`。
工作树 `F:/codex-cache/halo-ba-1.20.1`；原仓库、原包及其他版本保持原状。
实验 core 版本 `2.5.0-ba.1` / adapter 1。本地提交，无推送、发布或标签移动。

先审计指定 blend 活动输出和实例参数，再实施 BA_SPEC、body/face/hair/halo、unlit、
几何描边及独立投影。详见 [节点审计](ba-node-audit.md)。用户后续撤销武器适配验收；
已有计算保留为实验代码，验证包不提交武器绘制。

core 的 BaMaterial/BaMath/BaOrientation 定义类型、参数、数学和方向；
VisualAssetLoader.load(DefinitionSnapshot) 在重载阶段发布不可变快照。
HaloMeshResources 上传纹理，HaloBaShader 完整上传逐绘制参数，IrisMeshBridge
创建私有程序及动态采样器，HaloRenderer 调度前向绘制及阴影。
HaloRenderState 恢复纹理、采样器、FBO、混合和深度等状态。
旧接口、普通材质、AlphaMask 和回退时原有 lighting 标志保持兼容。

## 底包与接入

选择本地 Bliss v2.1.2 / 官方 release11。其 all_translucent.fsh 提供可读的前向实体入口，
适合在普通延迟光照后提交 BA，复用世界光照、阴影、雾、Bloom、曝光和色调映射。
本地 BSL 的许可来源未明确，其他候选存在不同再分发限制，未用于实验。

[固定源码](https://github.com/X0nk/Bliss-Shader/tree/release11)、
[前向入口](https://raw.githubusercontent.com/X0nk/Bliss-Shader/release11/shaders/dimensions/all_translucent.fsh)、
[该版本许可](https://raw.githubusercontent.com/X0nk/Bliss-Shader/release11/LICENSE.md)。
原 ZIP SHA256 `f41db92acc585fe9ffe0cc124d6ecaea27cef9ded91129ebc2a8e60226947b2c`。
脚本拒绝不匹配输入，保留许可证、署名及 Chocapic13 edit 命名，不使用盈利链接。

实际需要修改底包。Iris 用 Water 前向源码创建 Entity 格式的私有 BA 程序。
BA 分支关闭水扰动、普通 LabPBR/GGX 与重复 emission，输出完成的线性 HDR 颜色。
colortex7 alpha 的 `64/255` 分类使用离散采样、分类附件关闭混合；composite3 跳过玻璃染色、
折射和伤害材质处理。GUI、不支持的包或程序失败走旧材质。仅验证指定底包。

## 安装与协议 v1

1. MC 1.20.1 Fabric 安装交付目录的 Halo 实验 JAR 和 Fabric API。
   实测 Iris `1.7.6+mc1.20.1` / Sodium `0.5.13`。
2. 独立光影 ZIP 放入 shaderpacks 并选择它。
3. Arisu Base 与 Companions 两包放入 resourcepacks，伴生图包置于基础包之上，F3+T 重载。
4. 用 Halo 选择器指派 `model:arisu_ba`。约 1.75 方块高的静态分部件 OBJ，无骨骼动画。

已有兼容模型不需要额外材质 JSON。只在基础纹理同目录添加：

| 基础图 | 语义 | 伴生图 |
| --- | --- | --- |
| `<角色>_body.png` | body / arms 按部件区分 | `<角色>_body_mask.png` |
| `<角色>_hair.png` | hair | `<角色>_hair_mask.png`、`<角色>_hair_spac.png` |
| `<角色>_face.png` | face / eyebrow 按部件区分 | 可用节点默认输入 |
| `<角色>_eyemouth.png` | unlit | 可用节点默认输入 |
| `<角色>_halo.png` | halo | 可用节点默认输入 |

源 CH0334_Hair_Spec.png 的原始字节以 ch0334_hair_spac.png 提供。运行时小写路径，
不依赖 Windows 大小写宽容；完整后缀从右侧匹配，角色名可含下划线；model 部件优先于纹理。
一个定义有有效伴生图时启用相关预设；移除全部伴生图并重载恢复普通材质。
未知角色使用明确的 v1 类型默认值，不宣称匹配其未提供的 Blender 参数。

原始 Mask/Spac RGBA、Alpha、尺寸和 UV 全部保留，不预乘，不转换粗糙度/金属度，
不借用 `_n/_s`。颜色精确 sRGB 转线性；伴生图 Non-Color、重复 UV、线性过滤、无 mipmap。
旧 AlphaMask 仍是独立 nearest/step 契约。参数通过逐绘制 uniform 完整覆盖，
core Parameters.with 提供实例参数接口；第一版没有额外用户 JSON 参数覆盖入口。

## 数学、方向与环境亮度

Blender world `(x,y,z)` → OBJ/MC `(x,z,-y)`，右手坐标；法线逆转置。
Incoming 从表面指向相机；世界、视图及 BA_SPEC 方向由 BaOrientation 统一。
hair_spec_normal 参考四元数结合当前部件旋转重建 WORLD_SPACE 驱动，不冻结导出帧 Euler。
body 严格阈值、G/A 遮罩、调色与 Fresnel，hair 的 BA_MASK/TRUNC/坡道/BA_SPEC，
halo 替换、混色及当前约 0.5 emission 来自实际活动节点。未连接 Kajiya-Kay/MatCap 不移植。
眉毛前置位于非活动输出，默认不启用；unlit、halo、描边壳体不提交普通投影。

用户指出白天过暗后，body/face/hair 的艺术色彩乘 Bliss 的直接+间接 HDR 光照，
包含太阳/月亮、阴影、天空和方块光，不再作为固定自发光输出。
这是有意的 MC 适配，不是 Blender lighting engine 的等价重现；halo 和实际 unlit 保留发光。
`-Dhalo.ba.fixedLight=0.2` 固定节点 light 并用单位环境增益对照公式；默认 -1 使用环境。
固定曝光须在 Bliss 设置中另选手动曝光。

描边通过只读导出进程内临时 Realize Instances 保留实际壳体、反绕序和材质分组。
阴影复用最近完成的主帧姿态，不再次推进动画或物理，有一帧延迟。

## 验证及复测

core wrapper build：338 tests、零失败；Fabric 联合 build：123 platform tests、零失败，
包含旧 API 链接与平台隔离检查。RTX 4060 实际 GLSL 编译/链接及 10 个 GPU 数值用例通过，
覆盖默认输入、发光、环境增益和全暗无身体自发光。core 覆盖严格边界、命名、共享纹理、
仅增加/移除伴生图、缺图和坐标；平台覆盖不同实例参数完整覆盖。
游戏成功创建 `BA=true, shadow=true` 程序；已观察白天、夜间和相机侧移，保存截图。
较早失败/回退轮次不能作为视觉通过证据，以 round5 及之后日志为准。
最终提交客户端日志验证：未启用叠加包时 generation 1 为 0 个 BA 材质部件；
只添加伴生图包并应用资源重载后 generation 2 为 11 个。基础包 SHA256 前后一致，
重载未报错；之前的同客户端关闭/重新开启也完成。此为自动发现与重载验证，非完整视觉验收。

隔离实例 `.local/smoke-client` / 世界 New World；原实例未修改。
正面 `/tp @s 203.620 68 -231.512 180 10.8`；侧面 `/tp @s 205.620 68 -231.512 150 10.8`。
`/time set 6000` / `18000`，`/gamerule doDaylightCycle false`，固定天气、曝光和视距。

复测清单：

1. 基础包不变，关闭伴生图包 → F3+T → 旧材质；重新启用仅伴生图包 → F3+T → BA。
2. 固定相机与照明对照 Blender 图；当前两者相机/曝光没有精确匹配，不能作像素验收。
3. sync 模式转动实体、头部，观察高光；非均匀缩放和不同朝向。
4. 遮顶室内、树荫、方块光和日夜；Bloom、透明边缘、描边与实际阴影边界。
5. 多实例不同 Parameters 交替绘制；光影开关、不同光影和资源重载。
6. Seia 原包和普通 LabPBR 回归。

未完成视觉验收：室内、实际阴影边界、多实例 GPU 状态、精确相机/照明对照和完整普通 PBR 回归。
夜间 Bloom/边缘提亮仍偏强，待调校。静态 OBJ 不包含骨骼，Blender 旧 EEVEE 使用
4.5.6 EEVEE_NEXT 渲染，部分无效驱动被 Blender 跳过；未保存源文件。
透明排序仍有原有有界三角排序和世界透明表面交错限制。编译和程序创建不代表全部视觉通过，
不承诺完全一致。武器退出验收范围。

## 重建与交付

Java 17：`core/gradlew.bat -p core build`、`gradlew.bat build`。
Blender 运行 audit_blend.py / export_fixture.py 时加
`--background --factory-startup --disable-autoexec --python ...`，不运行内嵌脚本。
`python scripts/ba/build_shaderpack.py` 生成独立底包；verify_glsl.py 使用 `.local/ba/python`
的 task-local moderngl/glcontext。工具输入路径在脚本开头，换机器须提供同一核对输入。
交付 `.local/ba/delivery` 包含 JAR、独立光影、基础包、仅伴生图包、原始节点审计、参考图、
游戏截图、构建/运行日志、GLSL 结果与 SHA256 清单。
