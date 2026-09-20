# Halo 开发入口

若处于非flash/网易/copilot分支，则开始代码或跨版本任务前，阅读根目录 [DEVELOPMENT.md](DEVELOPMENT.md)。这是人类与 agent 共用的开发、调试、验收和推送流程；core 的类型与坐标契约见 [core/README.md](core/README.md)。

- 先检查 Halo 和 `core` 各自的分支、状态、未提交改动及锁定提交。当前开发主线为 `1.20.1-fabric`；`*-flash` 保持冻结。
- 平台无关的功能规则和计算进入 core；Halo 负责平台输入、存储/通信、反馈和绘制提交。先明确数据契约，在主线联调，再按需迁移目标版本适配器及必要资源。
- `core` 是独立 Git 仓库。提交其源码后才在 Halo 提交 gitlink；推送时 core 在前、引用它的 Halo 在后。不要自动追踪 core 分支最新提交，也不要移动已发布标签。
- 新功能/行为变化按开发指南进行相关测试与实际环境验证。文档修改只做相应文档检查；不能把编译成功、跳过项或未执行的游戏测试写成验收通过。
- 任务结束说明两个仓库的变更、验证、版本/SHA和提交推送状态。涉及发布时，注意 Halo 当前 `v*` 标签会触发 CI 创建 GitHub Release。
- 向 Modrinth / CurseForge 发布时，先等标签 CI 成功并完成 Release 正文编辑，再按默认分支的 [发布自动化文档](docs/release-automation.md) 用定稿文件触发上传；不得在正文定稿前分发。其他适配分支缺少此工具时使用默认分支工作区的集中入口，不复制上传实现。
- 每次平台发布可用 `--options-file` 明确指定已验证的 Minecraft 版本列表、Java 版本列表和是否附带 sources JAR。Modrinth 无独立 Java 标签，须在定稿正文中说明 Java 要求；不得把源码附件当主安装包，也不得将上传标签当作兼容性验收。
