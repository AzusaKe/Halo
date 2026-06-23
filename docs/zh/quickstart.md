# 快速入门：制作你的第一个光环资源包

欢迎！本教程将带你一步步创建一个自定义光环，无需编写任何代码——只需要准备一张图片和一个 JSON 文件。

完成后，你的光环将在游戏中跟随实体的头部运动，并带有平滑的衰减物理效果。

---

## 目录

- [准备工作](#准备工作)
- [第一步：创建资源包文件夹](#第一步创建资源包文件夹)
- [第二步：编写光环定义文件](#第二步编写光环定义文件)
- [第三步：准备纹理图片](#第三步准备纹理图片)
- [第四步：安装并使用](#第四步安装并使用)
- [第五步：调试与迭代](#第五步调试与迭代)
- [下一步](#下一步)

---

## 准备工作

你需要：

- 一个**文本编辑器**（如 VS Code、Notepad++）
- 一个**图像编辑器**（如 Photoshop、GIMP、Aseprite），用于制作 PNG 贴图
- 已安装 Halo 模组的 Minecraft 1.20.1（Fabric）

---

## 第一步：创建资源包文件夹

首先，创建一个文件夹作为你的资源包根目录。这里以 `yourhalo_pack` 为例：

```
yourhalo_pack/
```

在根目录下创建 `pack.mcmeta` 文件，内容如下：

```json
{
  "pack": {
    "pack_format": 15,
    "description": "我的第一个光环资源包"
  }
}
```

> **说明**：`pack_format: 15` 对应 Minecraft 1.20.x。`description` 可以随意填写，会在游戏内资源包列表中显示。

---

## 第二步：编写光环定义文件

光环定义文件告诉模组：光环长什么样、放在哪里、如何运动。

### 创建目录和文件

在资源包内创建以下路径：

```
yourhalo_pack/
├── pack.mcmeta
└── assets/
    └── halo/
        └── halo_definitions/
            └── yourhalo.json
```

### 编写 JSON

将以下内容写入 `yourhalo.json`：

```json
{
  "id": "halo:yourhalo",
  "orientation_mode": "sync",
  "layers": [
    {
      "position": [0.0, 0.0, 0.0],
      "rotation": [0.0, 0.0, 0.0],
      "scale": 1.0,
      "primitive": {
        "type": "billboard",
        "texture": "halo:textures/halo/yourhalo.png",
        "size": [0.5, 0.5]
      }
    }
  ],
  "animation": {
    "offset": {
      "y": [{ "function": "sin", "A": 0.01, "omega": 0.5 }]
    }
  },
  "hide_on_sleep": false,
  "positioning": {
    "offset": [0.0, 0.4, 0.35],
    "scale": 1.5
  },
  "damping": {
    "linearFactor": 0.5,
    "angularFactor": 0.1,
    "maxLinearDistance": 0.5,
    "maxAngularDegrees": 180.0
  }
}
```

### 字段说明

以下是这份 JSON 中每个字段的含义：

| 字段 | 说明 |
|------|------|
| `id` | 光环的唯一标识符。格式为 `命名空间:名称`。命名空间与文件夹对应：`halo:yourhalo` 表示该定义文件位于 `assets/halo/` 下。你可以使用自己的命名空间（如 `mypack:myhalo`），此时定义文件应放在 `assets/mypack/` 下。后续用 `/halo show` 命令时需要用到这个 ID。 |
| `orientation_mode` | 光环的朝向模式。`"sync"` 表示光环跟随实体头部的旋转——玩家抬头时光环也抬头，转头时光环也转头。本教程以 `sync` 作为示例。（灵感来源于 Blue Archive 的光环跟随方式。） |
| `layers` | 图层数组。每个光环可以由多个图层叠加组成。图层通过各自的位置（`position`）决定空间中的前后关系，而非数组中的排列顺序。这里我们只用了一个图层。 |
| `layers[0].position` | 该图层在光环局部空间中的位置 `[X, Y, Z]`（单位为格）。`[0, 0, 0]` 代表光环的**原点**——这个原点会和阻尼物理计算出的跟随位置对齐。图层在空间中的实际位置 = 阻尼跟随位置 + 此处定义的偏移。 |
| `layers[0].rotation` | 该图层的初始旋转角度 `[pitch, yaw, roll]`（单位为度）。`[0, 0, 0]` 表示不做旋转。 |
| `layers[0].scale` | 该图层的缩放倍率。`1.0` 为原始大小。 |
| `layers[0].primitive` | 该图层的渲染方式。`"billboard"` 是一个无厚度的四边形面片（因其没有厚度而得名），这是当前唯一可用的图元类型。 |
| `layers[0].primitive.texture` | 贴图路径。格式为 `命名空间:textures/halo/文件名.png`。这个路径以 `assets/` 为根目录，其中**命名空间**对应 `assets/` 下的文件夹名。例如 `halo:textures/halo/yourhalo.png` 中 `halo` 是命名空间，对应 `assets/halo/` 文件夹。如果你使用自己的命名空间（如 `mypack`），则将文件放在 `assets/mypack/` 下，贴图路径写为 `mypack:textures/halo/yourhalo.png`。 |
| `layers[0].primitive.size` | 面片的尺寸 `[宽, 高]`（单位为格）。`[0.5, 0.5]` 是一个 0.5×0.5 格的正方形。 |
| `animation` | 光环整体的动画。这里我们添加了一个 Y 轴的正弦浮动效果——光环会以微小的幅度上下浮动，让光环看起来更有"活着"的感觉。 |
| `animation.offset.y` | Y 轴偏移动画。`A: 0.01` 是振幅（0.01 格），`omega: 0.5` 是频率。值越大浮动越明显。 |
| `hide_on_sleep` | 是否在实体睡觉时隐藏光环。`true` = 睡觉时暂时不渲染，醒来后自动恢复；`false`（默认）= 始终渲染。 |
| `positioning.offset` | 光环整体相对于实体头部的位置偏移 `[X, Y, Z]`。`[0, 0.4, 0.35]` 表示在头顶上方 0.4 格、后方 0.35 格的位置。 |
| `positioning.scale` | 光环整体的缩放倍率。`1.5` 即放大到 1.5 倍。 |
| `damping` | 物理跟随参数，控制光环位置如何平滑跟随实体。 |
| `damping.linearFactor` | 线性跟随速度（0 = 不跟随，1 = 瞬间跟随）。`0.5` 提供适中的平滑跟随效果。 |
| `damping.angularFactor` | 角度跟随速度。`0.1` 较慢，适合光环这种不需要快速跟随旋转的装饰品。 |
| `damping.maxLinearDistance` | 最大跟随距离（格）。光环与实体的距离达到此值后将被**钳制**在此距离，不会继续拉远，直到跟随速度放缓后自然回弹。 |
| `damping.maxAngularDegrees` | 最大角度偏差（度）。光环与实体的角度差达到此值后将被**钳制**在此角度，不会继续偏转，直到跟随速度放缓后自然回弹。 |

---

## 第三步：准备纹理图片

### 创建贴图

在资源包内创建贴图文件：

```
yourhalo_pack/
├── pack.mcmeta
└── assets/
    └── halo/
        ├── halo_definitions/
        │   └── yourhalo.json
        └── textures/
            └── halo/
                └── yourhalo.png
```

### 贴图要求

- **格式**：PNG，必须包含透明通道（RGBA）
- **尺寸**：建议正方形，如 512×512 或 714×714 像素。不需要是 2 的幂次方
- **内容**：在透明背景上绘制你的光环图案。光环以外的区域保持透明即可——只有你画的部分会显示

> **提示**：光环贴图绘制的是光环的**正面**外观。由于光环图层是渲染在无厚度的面片（billboard）上，其朝向由 `orientation_mode` 决定——在 `sync` 模式下会跟随实体头部旋转。你可以参考模组内置的贴图（位于 `assets/halo/textures/halo/`）来了解推荐风格。

### ⚠️ 纹理方向说明（重要）

光环的每一层渲染在一个无厚度的面片（billboard）上。当图层的 `position` 和 `rotation` 均设为 `[0, 0, 0]`，且光环被挂载在实体头部的某个 `offset` 上时：

**基本方向对应**：
- 图片浏览器中的**上方** = 游戏中玩家的**上方**（世界 Y+，天空方向）— **永远不会翻转**
- 图片浏览器中的**下方** = 游戏中玩家的**下方**（世界 Y-，脚底方向）— **永远不会翻转**

**观察方向的影响**：

- **从玩家头部中心向外看**（类似旁观者模式自由视角，将摄像机移到玩家头部位置向外看）：贴图显示为**完全正向**——与你在图片浏览器中看到的完全一致
- **从外部看向玩家**（普通游戏视角，从玩家前方看）：贴图显示为**左右翻转**（水平镜像），但上下方向不变

**总结**：光环贴图**对内是正的**（从玩家头部向外看时，与图片浏览器中看到的完全一致），**对外是左右翻转的**（从外部看向玩家时，贴图为水平镜像）。上下方向始终不变。

> **提示**：如果光看文字仍不确定，可以去开源仓库 [AzusaKe/Halo](https://github.com/AzusaKe/Halo) 查看内置的示例光环定义和对应贴图，对比它们的写法来加深理解。你也可以参考 [Ba-Halo-Definition](https://github.com/AzusaKe/Ba-Halo-Definition) 仓库中已制作好的 Blue Archive 人物光环资源包作为示例。

---

## 第四步：安装并使用

### 安装资源包

1. 将 `yourhalo_pack` 文件夹（或压缩好的 `.zip` 文件）放入 Minecraft 的 `resourcepacks` 目录中
   - 如果你不知道 `resourcepacks` 目录在哪：启动游戏后进入 **选项 → 资源包 → 打开资源包文件夹**，即可直接打开
2. 启动游戏（如果已启动，先退出再重新启动，以便 Minecraft 识别新资源包），进入 **选项 → 资源包**，找到"我的第一个光环资源包"，点击箭头将其移到右侧"已启用"列表
3. 点击"完成"应用

> **提示**：Minecraft 同时支持文件夹形式和 `.zip` 形式的资源包——你可以将整个文件夹直接放入 `resourcepacks`，也可以使用压缩后的 `.zip` 文件。`.zip` 更适合分享和转移。**注意**：压缩时请确保 `pack.mcmeta` 在压缩包的最上层——即直接进入压缩包就能看到 `pack.mcmeta` 和 `assets/` 文件夹，而不是里面还套了一层文件夹。
>
> **便捷工具**：如果你不想手动打包，可以使用 [Halo-Packing-Tool](https://github.com/AzusaKe/Halo-Packing-Tool)，一键将 JSON 和 PNG 打包为资源包。

### 加载光环

资源包启用后，在游戏内按 `T` 打开聊天栏，输入：

```mcfunction
/reload
```

这条命令让模组重新加载所有光环定义。你会看到新定义被加载的提示。

然后，给自己戴上光环：

```mcfunction
/halo show @s yourhalo
```

> `@s` 是 Minecraft 的目标选择器，表示"自己"（self）。`yourhalo` 是你在 JSON 中定义的 ID 的名称部分（模组会自动补全为 `halo:yourhalo`）。

如果一切顺利，你应该能看到光环浮现在你的头顶！

---

## 第五步：调试与迭代

### 常用调试命令

| 命令 | 用途 |
|------|------|
| `/halo list` | 列出所有已加载的光环定义。确认你的 `yourhalo` 在列表中 |
| `/halo dump` | 输出所有光环定义的详细信息（包括图层、动画、阻尼参数） |
| `/halo active` | 列出当前所有佩戴光环的实体 |
| `/halo inspect @s` | 查看自己身上光环的运行时状态 |
| `/halo hide @s` | 移除自己的光环 |

### 修改与热加载

光环定义在游戏中是支持热加载的。修改 JSON 后无需重启游戏：

1. 修改 `yourhalo.json` 并保存
2. 在游戏内运行 `/reload`
3. 光环立即以新配置渲染

这意味着你可以一边调整参数一边看效果，非常方便。

### 常见问题排查

**光环没有显示？**
- 检查 `/halo list` 是否包含你的光环 ID。如果没有，检查 JSON 文件路径是否正确（`assets/halo/halo_definitions/yourhalo.json`）
- 检查贴图路径是否与 JSON 中的 `texture` 字段一致
- 检查贴图是否为有效的 PNG 文件

**光环位置不对？**
- 调整 `positioning.offset` 的 Y 值来改变高低，Z 值来改变前后
- 调整 `positioning.scale` 来改变整体大小

**光环不够跟手/太跟手？**
- 调高 `damping.linearFactor`（接近 1.0）让光环更快跟随
- 调低 `damping.linearFactor`（接近 0）让光环更有"飘"的感觉

---

## 下一步

恭喜！你已经成功创建了第一个自定义光环。从这里开始，你可以尝试：

- **添加多个图层**：在 `layers` 数组中追加更多图层，每层使用不同的贴图和 Y 偏移，制作有层次感的光环
- **添加旋转动画**：在图层上使用 `animation.rotation` 让某些层持续旋转
- **调整浮动动画**：修改 `animation.offset` 中的 `A`（振幅）和 `omega`（频率）来改变浮动效果
- **尝试其他朝向模式**：将 `orientation_mode` 改为 `"locked"` 或 `"free"`：
  - `locked`：光环始终指向玩家头部中心，并利用头部球面极点让光环像指南针一样稳定指向，不会绕法线随意旋转
  - `free`：光环同样指向玩家头部中心，但绕法线的旋转不受控制
  - `sync`（本教程使用的模式）：初始姿态由 `locked` 模式的首帧确定，随后与玩家头部同步旋转

> 详细的字段参考手册正在编写中，届时会涵盖所有可用字段、动画函数和常见模式。敬请期待！
>
> **参考示例**：你也可以直接查看 [Ba-Halo-Definition](https://github.com/AzusaKe/Ba-Halo-Definition) 仓库中已制作完成的 Blue Archive 人物光环资源包，作为实际项目的参考。

---

*Halo Mod v1.0.1 · [GitHub](https://github.com/AzusaKe/Halo)*
