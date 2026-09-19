# Forge 1.20.1 mesh 黑色回归修复

日期：2026-09-19。目标分支 `1.20.1-forge`，基线 `a655ddb14bfe6b45f69fa5a24383631bad4cd922`。
适配修订从 1 增加到 2；core 2.4.0 保持锁定
`224551ef3f76c36b60fd5c84caaf4fe8a71b5e3a`，源码与 gitlink 均未改变。

## 原因与契约

Forge 适配器为受光图元调用实体 `RenderType.setupRenderState()` / `clearRenderState()`。
后者经 `LightTexture.turnOffLightLayer()` 将 `RenderSystem` 的纹理槽 2 清零。
旧实现只在一批绘制开始时绑定一次 lightmap，因此后续全亮 mesh 即便使用
`LightCoord=(240,240)`，仍会从空纹理读取黑色。绘制先后顺序决定是否触发。

这是平台 GPU 提交的状态问题，不改变 core 的全亮/受光、材质、颜色、UV、法线或排序契约，
也不改变定义、存档、网络格式。`HaloDrawSubmitter` 现在在每次 mesh、缓存图元或 CPU run
的 RenderType setup 之后重新绑定当前 lightmap；原有状态保存与恢复范围保持不变。

任务开始时 `HaloMeshShader.java` 已有一份未提交的原生 sampler 显式赋值补丁，本次保留其行为，
纠正了说明。Minecraft 的 `VertexBuffer.drawWithShader` 本身也会从 RenderSystem 填充 sampler；
显式 `setSampler` 不能补救已经被清零的槽 2。基线复现时已包含这份补丁。

## GPU 回归

使用真实 Forge 客户端、NVIDIA RTX 4060 Laptop GPU、Java 17、32×32 离屏 framebuffer
执行平台生产提交代码并读回像素。测试不使用模拟 GL，也不将测试类放入生产 JAR。

- 修复前，红色测试三角形单独绘制为 `(255,64,32,255)`；前面插入一个受光图元后变成
  `(0,0,0,255)`。世界/GUI、VBO/CPU 四种组合均复现，GL error 为 0。
- 修复后四种组合都保持正常颜色；另外覆盖缓存旧图元和其 CPU 回退的受光→全亮顺序。
- 通过 core 读取五个内置 mesh demo 的定义，按固定时间生成真实绘制命令，使用原始模型、
  纹理和遮罩；对比独立绘制与前插受光图元的整张 framebuffer，要求逐字节相同且有可见彩色像素。
- 同样检查用户提供的 `Seia_mesh_import.zip` 中 `model:seia` 的完整七部分模型。
  资源包只复制到隔离测试目录，不纳入仓库。
- Forge 47.4.0：启动后与资源重载后两轮全部通过。
- Forge 47.4.21：隔离单人世界进入后、资源重载后、退回主界面重新进入后三轮全部通过。
  世界里的全亮 lightmap 返回 `(252,63,32,255)`，前插受光图元前后保持一致。
- Forge 47.4.21 + Oculus 1.8.0 + Embeddium 0.3.31（关闭光影）：同一隔离世界的三轮检查全部通过。

日志与像素记录位于工作树的 `.local/mesh-regression/`；隔离客户端位于 `.local/mesh-gpu-test/`。
这是针对本缺陷的真实客户端像素验证，不替代完整多人、第三方模型和开启光影后的 LabPBR 目视验收。

## 重放方法

```powershell
# 两轮检查：初次资源加载及资源重载。没有外部包时仅检查内置 demo。
.\gradlew.bat -PmeshGpuTest runMeshGpuTest --console=plain

# 在隔离客户端 saves 下准备可丢弃的 1.20.1 世界副本，再检查重进世界。
.\gradlew.bat -PmeshGpuTest '-PmeshGpuWorld=Mesh Regression' '-Pforge_version=47.4.21' runMeshGpuTest --console=plain
```

可选 Seia 验证：将该 ZIP 放进 `.local/mesh-gpu-test/resourcepacks/` 并在这个隔离客户端启用。
结果写入 `.local/mesh-gpu-test/mesh-gpu-result.txt`，末行必须为 `PASS`，否则 Gradle 任务失败。
开启 `meshGpuTest` 时才创建专用 source set 与运行任务；普通构建不包含测试模组代码。

## 开发构建与用户验收

- Forge 47.4.0 `gradlew.bat clean build --console=plain` 通过；Halo 123 项、core 324 项测试报告均为零失败、零跳过。
  YSM 2.6.5 与 EMF 3.1.1 发布包签名门禁通过；core 未变化，联合构建复用了部分有效的 core 检查缓存。
- 合包、core 锁定、来源信息、Mixin refmap、专用服类加载和新旧 API v2 调用方门禁通过。
- 成品为 `halo-1.20.1-forge-2.4.0+adapter.2.dev.jar`，检查确认不含 `MeshGpuRegression` 测试类。
- SHA-256：`E63A35ED960991480049E94BD17D92D12AE19078C88B40122ADB14786AC00E57`。
- 未重跑开启光影后的 LabPBR 材质响应或完整多人矩阵；本记录不扩展这些兼容性结论。

用户于 2026-09-19 完成实际游戏复测并确认通过，授权转为正式版并发布。

## 正式发布

正式版本为 `2.4.0+adapter.2`，标签为 `v2.4.0-forge-1.20.1-adapter.2`，
成品名为 `halo-1.20.1-forge-2.4.0+adapter.2.jar`。提交后通过
`gradlew.bat build '-Pforge_version=47.4.23' -Prelease=true --console=plain`
执行正式构建门禁；标签 CI 独立验证 Forge 47.4.0 与 47.4.23，并上传正式成品。
正式 JAR 的提交来源与哈希以该标签的 CI/Release 附件为准，上述开发版哈希仅用于验收追溯。

发布页：[Halo 2.4.0 Forge adapter.2](https://github.com/AzusaKe/Halo/releases/tag/v2.4.0-forge-1.20.1-adapter.2)。
