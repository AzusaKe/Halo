# 指定 Arisu 文件的活动节点审计

来源：`D:\download\blender\Arisu_4.5-halo_bending_re.blend`；SHA256 `902f66f44d44ddfdd9036213db1e91190973ee7af8c75a74e6b0710f2fdfde22`。
Blender 4.5.6 LTS，参考帧 49。读取时 `--disable-autoexec` / `use_scripts=False`；没有运行内嵌脚本，没有保存源文件。

参考图以独立进程临时改用 `BLENDER_EEVEE_NEXT`。原场景保存的 `BLENDER_EEVEE` 在 4.5.6 中不可用；参考图不是原旧版引擎的保证等价截图。嘴部非法驱动保持跳过。

| 材质 | 活动 Surface 输入 | 活动节点数/总数 |
| --- | --- | --- |
| CH0334_Body | ba_body_shader | 4/4 |
| CH0334_Body_Arms | ba_body_shader | 4/4 |
| CH0334_Face | ba_body_shader | 3/3 |
| CH0334_Hair | ba_hair_shader | 5/5 |
| BA_Halo_Emission | ba_halo | 3/3 |
| CH0334_Weapon01 | ba_weapon_shader | 4/4 |
| CH0334_EyeMouth | ba_no_shadow | 3/3 |
| CH0334_Eyebrow | ba_no_shadow | 3/5 |

## 数学及实际输入

- Body/Arms/Face 分别保存实际实例参数。ShadowClamp 使用严格 `>` 的 A 分段；G 控制明暗偏置。Face 没有 Mask 链接，原输入 RGB=0、A=0.5。Fresnel IOR=1.5，按实际阈值和 Alpha 条件选择边缘色。
- Hair 活动路径含 BA_MASK、BA_SPEC。Spec RGB 解码 `2*RGB-1` 后归一化；A 控制形状扣减和 `<0.07` 排除；硬高光为严格 `>`，不把 Spec 当粗糙度/金属度。Mask G 使用不截断 Map Range，阴影 TRUNC 向零截断。
- `hair_spec_normal` 世界空间驱动为 `(-rotX,-rotZ,-rotY)`；Incoming 旋转 Y=pi/2。导出 helper 参考四元数，运行时根据部件姿态重算驱动。
- Halo 颜色距离平方选择、替换和混色后乘有效发光强度约 0.5（原始浮点 -0.500000119 + 1）。
- Weapon Diffuse Roughness=1，Mask R 为 Bump 高度，Distance=0.001，invert=true。G 经 `2G-1` 从阴影输入减去；B 经 `2B-1` 作为 AO 混色。`Math.009` 静音，直接透传第一输入，不能额外扣除 `1-B`。metallic=0，跳过金属 BSDF 支路。MC 的漫反射输入目前不是 Blender Oren–Nayar 的严格复现。
- EyeMouth/Eyebrow 活动 ba_no_shadow，发光强度 1；阴影射线透明。eyebrow_in_front 只接到非活动 Material Output，默认不启用。
- 描边：实际活动几何节点读取 outline 属性，法线偏移、合并/删除、翻面及材质替换后实例输出。导出阶段仅在内存中追加 Realize Instances。三个描边颜色从实际 Emission 读取，生成 sRGB 常量色图；不以纹理打包代替壳体。

## 活动组的节点列表

以下以活动 Group Output 反向跟踪，排除未连接节点、静音第二输入及 metallic=0 支路。组输入会在材质实例中覆盖默认值；原始完整链接见交付目录 node-audit.json。

### ba_body_shader

活动祖先节点 33/33：

`Group Output`, `Group Input`, `Mix.005`, `Mix.006`, `Mix.007`, `Group`, `Group Input.001`, `Fresnel`, `Math.029`, `Mix.008`, `Mix`, `Diffuse BSDF.001`, `Shader to RGB`, `Map Range`, `Math`, `Math.001`, `Math.002`, `Separate XYZ.002`, `Group Input.002`, `Reroute`, `Math.003`, `Reroute.002`, `Reroute.003`, `Separate Color`, `Separate Color.001`, `Mix.001`, `Map Range.001`, `Math.004`, `Math.005`, `Mix.002`, `Math.006`, `Math.007`, `Math.008`

### ShadowClamp

活动祖先节点 12/12：

`Group Output`, `Group Input`, `Math`, `Math.001`, `Math.002`, `Math.003`, `Mix`, `Mix.001`, `Mix.002`, `Mix.003`, `Mix.004`, `Math.004`

### ba_hair_shader

活动祖先节点 21/25：

`Group Output`, `Group Input`, `Mix.005`, `Mix.004`, `Math.015`, `Math.018`, `Math.020`, `Mix.006`, `Mix.007`, `Group.002`, `Group Input.001`, `Mix.008`, `Reroute.001`, `Mix.009`, `Color Ramp.003`, `Group.005`, `Hue/Saturation/Value`, `Mix.010`, `Reroute.002`, `Hue/Saturation/Value.001`, `Mix.011`

### BA_MASK

活动祖先节点 19/21：

`Group Output`, `Group Input`, `Math.003`, `Separate Color.007`, `Math.004`, `Math.005`, `Math.006`, `Math.007`, `Math.008`, `Diffuse BSDF`, `Shader to RGB`, `Math.009`, `Math.013`, `Map Range.004`, `Math.010`, `Math`, `Math.014`, `Math.015`, `Math.011`

### BA_SPEC

活动祖先节点 61/65：

`Group Output`, `Group Input`, `Vector Math.001`, `Vector Math.002`, `Vector Math.006`, `Vector Math.004`, `Vector Rotate`, `Geometry.001`, `转接点`, `Vector Math.003`, `Vector Math.007`, `Vector Math.011`, `SpecDirMultiplier`, `Math.016`, `分离 XYZ.002`, `Math.017`, `Mix`, `Math.018`, `Math.019`, `Math.020`, `Math.021`, `Math.022`, `Math.023`, `Math.024`, `Math.025`, `Vector Rotate.002`, `Combine XYZ.004`, `Math.009`, `Math.026`, `Math.027`, `Fresnel`, `Math.029`, `Math.028`, `Math.036`, `分离 XYZ.003`, `分离 XYZ.005`, `SpecDirMultiplier.001`, `Reroute.002`, `Reroute`, `Reroute.001`, `Reroute.003`, `Reroute.004`, `Reroute.005`, `Reroute.006`, `Reroute.007`, `Reroute.008`, `Math.031`, `Math.037`, `Reroute.009`, `Math.038`, `Group Input.001`, `Mix.001`, `Math.039`, `Mix.002`, `Vector Rotate.003`, `Vector Math`, `Vector Math.005`, `Combine XYZ`, `Reroute.010`, `Combine XYZ.001`, `Reroute.011`

### ba_halo

活动祖先节点 13/13：

`Group Output`, `Group Input`, `Emission`, `Vector Math`, `Vector Math.001`, `Math`, `Math.001`, `Math.002`, `Mix`, `Mix.001`, `Mix.002`, `Math.004`, `Math.006`

### ba_weapon_shader

活动祖先节点 34/35：

`Group Output`, `Group Input`, `Separate XYZ.001`, `Math`, `Diffuse BSDF`, `Shader to RGB`, `Vector Math`, `Geometry`, `Mix.001`, `Math.002`, `Math.003`, `Math.004`, `Math.005`, `Math.001`, `Mix.002`, `Reroute`, `Math.006`, `Mix.003`, `Math.007`, `Math.008`, `Group Input.001`, `Invert Color`, `Invert Color.001`, `Bump`, `Map Range`, `Math.009`（静音透传）, `Math.010`, `Reroute.001`, `Mix`, `Hue/Saturation/Value`, `Metallic BSDF`, `Mix Shader`, `Math.011`, `Invert Color.003`

### ba_no_shadow

活动祖先节点 6/6：

`Group Output`, `Group Input`, `Emission`, `Light Path`, `Transparent BSDF`, `Mix Shader`

BA_SPEC 的 Kajiya-Kay、MatCap 等不在实际活动输出路径中，因此未移植。纹理和参数仅以此指定文件为依据；附近旧导出不作为节点真值。
