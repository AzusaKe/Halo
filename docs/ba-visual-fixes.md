# BA v2 亮度与头发方向修正

目标为 Fabric 1.20.1，core `2.5.0-ba.2` / adapter 1；两个仓库继续使用
`Experimental/1.20.1-fabric`。基础纹理同目录添加 `_mask.png` / `_spac.png`
的启用协议保持 v1，无需追加材质 JSON。武器不属于本轮适配和测试范围。

## 原因及行为

- **头发高光**：旧适配器把 `Camera.getRotation()` 当作实际 OpenGL 视图的逆旋转。
  MC 1.20.1 的 camera +Z、yaw 和 pitch 约定与提交视图不同，会把相机俯仰带入
  物体 helper 方向。现在使用 `RenderSystem.getInverseViewRotationMatrix()`，
  core `BaOrientation.fromView` 重建部件世界方向；只转动相机朝向时，helper 保持不变。
  相机位置或头部自身运动仍会按活动 BA_SPEC 节点改变高光，硬阈值也仍可能产生跳变。
- **夜间过亮**：旧定义组的 `glowing` 默认 true；导出器之前在 primitive 上写 false，
  并未控制组光照，BA 收到满格天空/方块光。`MaterialState.Mesh.baEnvironmentLight`
  现在单独保存真实局部光。BA 程序选择它；旧程序、程序失败回退继续使用原来的
  group light。全暗 `(0,0)` 是有效输入，不再等同于不可用或满亮。
- **眼睛、嘴巴和描边**：不受普通方向/投影影响的材质现在乘无方向性的局部天空与方块光。
  删除 BA 专属路径的固定 minimum-light 底值，普通底包材质不变。准心朝向不参与这些
  部件的光照计算。此前固定 HDR 发光会被底包随画面变化的自动曝光放大。
- **光环**：保留节点颜色替换/混色以及实际 0.5 强度，在主机 HDR 中乘环境亮度，
  并保留 `0.04 / exposure` 的小发光底值。这个底值的曝光后贡献稳定；环境部分仍受
  底包曝光、Bloom 和色调映射影响，不宣称完全取消全屏自动曝光变化。

`halo.ba.fixedLight` 对照模式继续使用单位环境增益，便于核对原节点数学。
运行时环境策略是明确的 Minecraft 适配，不能将其声称为 Blender 光照引擎等价实现。
整材质参数仍为逐绘制 uniform，原始伴生图通道、Alpha 和过滤协议不变。

用户在测试基础包加入的 `display_in_invisible: true` 已保留，导出器也同步此默认值。
没有重新导出覆盖用户当前模型。原始 blend、原始光影、v1 交付压缩包均保留。

## 验证证据

- core wrapper build 和 Fabric 联合 build 均成功；340 core 用例通过。
  平台 123 用例中 122 通过、1 项外部 YSM 签名检查因缺少独立包而跳过。
- 新增实际渲染输出测试：自动 BA 材质在默认 glowing 组中读取 `(0,15)` 和 `(0,0)`；
  非 BA 与失败回退保留 full-bright。旧构造器兼容检查通过。
- 20 组 yaw/pitch 与非均匀缩放测试，验证 helper 和世界 Incoming 不被相机朝向重复旋转。
- RTX 4060 实际 GLSL 编译/链接及 15 个 GPU 数值用例通过，包括全暗眼口/描边、
  无方向环境增益与三种曝光下的光环底值。
- 实际 Iris 客户端成功创建 BA / shadow 程序，固定相机室外正午与午夜截图已保存。
  午夜身体、头发和眼口明显随环境变暗；光环保持弱发光。相机位置不变、pitch 为
  0 / 10.8 / 22 的画面未见原先明显的高光滑动或眼口独立发亮。
  这属于该场景的观察结果，不代替完整视觉验收。
- 运行记录 `game-v2-light.log`。出现过一次 Windows 剪贴板占用导致命令未输入，
  重试后由服务器日志确认执行；该警告不是着色程序编译错误。

## 复测

安装 v2 JAR 与 v2 独立 Bliss 副本，启用 Base + Companions，叠加包置于上方。
若使用旧 v1 光影，需同时替换光影文件并重载，不能仅换 JAR。

隔离实例 `.local/smoke-client`，世界 New World，测试位置：

```text
/gamerule doDaylightCycle false
/tp @s 203.620 68 -231.512 166 10.8
/time set 6000
/time set 18000
```

固定位置仅把 pitch 改成 0 / 22；随后移动相机和转动模型/头部，区分真实视线变化
与相机朝向变化。观察高光、眼口、光环与周围草地亮度；保留底包自动曝光时等待其稳定。
还需用户复测动态头部、室内/树荫/方块光、多实例、完整普通 LabPBR 回归及精确 Blender 对照。
静态 OBJ、透明交错和一帧阴影姿态限制见 v1 记录。没有承诺完全一致。

交付路径 `.local/ba/delivery-v2` 与 `.local/ba/Halo-BA-experimental-v2.zip`。
两个源码提交及交付文件 SHA 记录在交付 README / SHA256SUMS；全部仅本地，未推送、发布或移动标签。
