# OBJ mesh 与透明遮罩

Halo 2.0.0 开发版新增 `mesh` 图元，JSON schema 为 **1.1.0**。旧图元和旧定义继续兼容。
先在资源包中放置文件，再从定义引用：

```text
assets/mypack/halo_definitions/example.json
assets/mypack/models/halo/example.obj
assets/mypack/textures/halo/example.png
assets/mypack/textures/halo/example_mask.png
```

下面是一份完整定义。可省略 `material`，得到普通贴图模型。

```json
{
  "version": "1.1.0",
  "id": "mypack:example",
  "positioning": {"offset": [0, 0.4, 0], "scale": 1},
  "layers": [{
    "position": [0, 0, 0],
    "rotation": [0, 0, 0],
    "primitive": {
      "type": "mesh",
      "model": "mypack:models/halo/example.obj",
      "texture": "mypack:textures/halo/example.png",
      "size": [0.5, 0.5, 0.5],
      "material": {
        "double_sided": true,
        "effects": [{
          "type": "alpha_mask",
          "texture": "mypack:textures/halo/example_mask.png",
          "mode": "linear",
          "threshold": 0.5,
          "uv_offset": {
            "u": [{"function": "linear", "start": 0, "speed": 0.1}],
            "v": [{"function": "sin", "A": 0.05, "omega": 1, "phi": 0}]
          }
        }]
      }
    }
  }]
}
```

## 坐标与尺寸

`model`、`texture` 和三个分量的 `size` 必填。`size` 是沿模型本地 X/Y/Z 的目标包围盒尺寸，单位为格，
各轴独立缩放，之后再应用所属 group 和祖先的缩放。大小不是乘数；`[1,1,1]` 会将模型包围盒适配为一格见方。

保留导出的原点，不居中、不换轴、不额外旋转。OBJ 的 `(0,0,0)` 对应父组原点；父组位移和旋转为零时坐标轴一致。
需要围绕特定点旋转时，在建模软件中设置导出后的实际坐标，使该点为原点。位移、旋转和动画放在 group 上，
沿用 `[yaw,pitch,roll]` 角度制及 Y-X-Z 顺序。

`size` 必须为有限非负数。平面模型的零跨度轴只接受 `size=0`；该轴保持原始坐标，不产生除零。
例如 XY 平面模型使用 `[0.5,0.5,0]`。有跨度的轴允许缩放到零。

## OBJ 导出

1. 使用静态网格，应用所需的对象变换，导出 Y 向上、与 Halo 定义本地轴一致的坐标。
2. 展开 UV，将所有需要的表面合并使用一张 PNG；模型中的对象/组可保留，但共用 JSON 材质。
3. 建议在导出前完成三角化。加载器也接受平面凸四边形；凹面、非平面四边形和更多顶点的多边形要求预先三角化。
4. 每个面角点必须有 UV。支持 `v/vt`、`v/vt/vn`、正负索引和独立 UV 接缝；法线可导出，但首版不计算方向光。
5. MTL 不会加载；`mtllib`、`usemtl`、对象名、组名和平滑组不改变材质。不支持骨骼、模型内部动画或曲线曲面。

### Blender 与外部 OBJ

与 Halo Blender 编辑器现有坐标转换一致：Blender `(x,y,z)` 导出为 Halo `(x,z,-y)`，
逆向导入编辑器为 `(x,-z,y)`。这个转换的行列式为 **+1**，只是旋转，不会镜像模型或反转面绕序。
Blender 原生 OBJ 导出可使用 **Forward = -Z、Up = Y**（Python：`forward_axis='NEGATIVE_Z', up_axis='Y'`）；
若插件已逐顶点应用 `(x,z,-y)`，不要再让 OBJ 导出器重复换轴。

外部 OBJ 没有统一的轴向元数据，Halo 按文件里的 XYZ 原样读取，不能猜测它来自 Z-up 还是 Y-up 软件。
先在 Blender 中按源文件约定导入并确认朝向，之后统一执行上述 Halo 导出转换。
OBJ 的顶点应相对于对应 group 的原点：对象变换可以烘焙进顶点，但已经写入 JSON group 的变换不能再烘焙一次。
希望保留导出几何的绝对尺寸时，将 `size` 设置为导出顶点包围盒的实际 XYZ 跨度；任意填 `[1,1,1]` 会独立拉伸三个轴。

已用 Blender 5.2 实际导出一个原点偏移、三个轴长度不同的四面体，验证 `(0.25,0.5,1)`
进入 OBJ 后为 `(0.25,1,-0.5)`，导入 core 后保持该坐标、面绕序及 UV 接缝；没有额外居中或镜像。

OBJ 的 V 在导入时转换一次为现有贴图坐标：U 向右、V 向下。不要为了 Halo 再手动翻转图片。
加载上限为 16 MiB OBJ 文本、合计 1,000,000 个声明的位置/UV/法线元素及 250,000 个三角形。
这是加载保护，不是流畅运行承诺；建议装饰模型保持数百至数千三角形。

## 材质参数

首版只有 mesh 支持 `material`。`effects` 可省略或为空，最多一个 `alpha_mask`；未知类型或重复效果会报告定义错误。

| 字段 | 默认 | 行为 |
| --- | --- | --- |
| `double_sided` | `true` | 双面绘制；`false` 按面绕序剔除背面 |
| `effects[].texture` | 必填 | 与基础 PNG 宽高相同的灰度遮罩 |
| `mode` | `linear` | `linear` 使用灰度；`step` 根据阈值二值化 |
| `threshold` | `0.5` | `[0,1]`，仅 `step` 生效；灰度大于等于阈值时显示 |
| `uv_offset.u/v` | 零偏移 | 与现有动画相同的 term 数组，相同通道各项求和 |

遮罩读取 PNG 的 R 通道数值，忽略遮罩自身 alpha，不做 sRGB 转换：黑为 0，白为 1，128 灰为 `128/255`。
最近邻、循环采样，不改变共享纹理的过滤设置。`linear` 表示灰度与透明度的对应关系，不表示双线性过滤。

```text
maskUV = fract(baseUV + offset(t))
maskAlpha = mode == step ? (gray >= threshold ? 1 : 0) : gray
finalAlpha = baseTextureAlpha × groupAndAnimationAlpha × maskAlpha
```

基础纹理 UV 保持不变。正 U/V 增大采样坐标，视觉上的图案向反方向移动；偏移 1 表示一个完整纹理周期。
`linear` 使用 `start + speed*t`；`sin` 使用 `A*sin(omega*pi*t+phi)`；`cos` 同理。时间单位为秒，phi 为弧度。
遮罩复用周期动画的时钟，在启动/关闭过渡期间冻结，并衔接恢复；资源重载不重新佩戴或重置实例时钟。

亮度继续受 group 的 `glowing`、`animation.glow` 及继承规则控制。首版不提供 PBR 或法线方向光。
渐变透明使用深度测试和普通透明排序；相交网格及与原版水、玻璃交叠仍可能存在常规透明排序局限。

mesh 在实体缓冲区完成后提交，避免不写深度的透明 mesh 被随后刷出的实体覆盖。
开启 Iris 时，在光影加载阶段创建独立的 `gbuffers_textured` 材质变体，先处理基础贴图的遮罩 alpha，
再交给光影包处理，写入 Iris 管理的世界缓冲区并参与后续后处理。不会修改旧图元共用的光影程序，不新增 Iris 运行时依赖。
Iris 路径中，未被丢弃的 mesh 片元也写入透明阶段深度 `depthtex0`，包括线性遮罩；
光影包需要据此还原 mesh 的位置并计算合成和雾，沿用玻璃或天空背景的深度会让近处模型错误淡出。
此时不透明深度 `depthtex1` 已复制完成。零 alpha 片元仍被丢弃，透明度混合和排序继续保留。
关闭光影时，渐变透明 mesh 仍按原有规则关闭深度写入。
具体颜色、雾、bloom 等效果由光影包决定；首版仍不新增 PBR 或独立的 mesh 投射阴影通道。
此适配位于 1.20.1 Halo 层，core 不依赖 Iris。光影包若使用该通道的额外 geometry/tessellation 阶段，
或不兼容的基础采样方式，相关 mesh 暂停绘制并报告原因；切换光影包会重建材质。

## 示例与重载

内置 `halo:mesh_demo`、`halo:mesh_mask_demo`、`halo:mesh_step_demo` 分别演示普通贴图、线性遮罩与阈值遮罩。
使用 `/halo show @s halo:mesh_mask_demo` 佩戴，`/halo hide @s` 关闭。

模型、基础纹理和遮罩均由客户端资源包提供，按资源包优先级覆盖。修改后使用 **F3+T**；服务端 `/reload` 是数据包重载。
专用服不需要加载 OBJ 或 PNG，也不会通过 Halo 协议传输这些文件；希望玩家看到同样外观时，应分发相同的客户端资源包。

文件缺失或损坏只跳过相关 mesh；遮罩尺寸不匹配同样跳过该 mesh，日志会说明资源 ID 和原因。
修复并重载即可恢复，佩戴关系和其他有效图元保留。
