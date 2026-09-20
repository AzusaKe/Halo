# Halo 平台发布自动化

本流程将成功的 GitHub 标签构建产物上传到 Modrinth 和 CurseForge。功能开发、跨版本适配、提交顺序和实际游戏验收仍遵循 [开发指南](../DEVELOPMENT.md)。

## 部署结构

共享入口是默认分支 `1.20.1-fabric` 上的 [publish-platforms.yml](../.github/workflows/publish-platforms.yml)。agent 在目标标签 CI 成功、修改完 Release 正文后，通过 `workflow_dispatch` 触发发布。入口支持 [配置清单](../.github/release-targets.json) 中的 9 个维护分支，无需向各适配分支复制发布脚本。

CI 完成和 Release 创建本身不再触发上传，避免抢在 agent 整理发布说明之前分发。触发时绑定最终正文的 SHA-256，工作流排队期间或平台预检期间正文发生变化，会停止该项上传。两个平台使用同一份经过校验的正文快照。

发布器只从默认分支加载实现；目标标签中的文件只作为数据读取，不检出或执行其脚本，不使用目标构建的缓存。Fork、PR、普通分支构建、失败的 CI、冻结平台和未知标签不会获得平台上传资格。

## 一次性配置

项目已配置为：

| 平台 | 项目 | ID |
| --- | --- | --- |
| Modrinth | [Halo](https://modrinth.com/mod/halo-azusake) | `k2fEt5RO` |
| CurseForge | [作者后台项目](https://authors.curseforge.com/#/projects/1582156/description) | `1582156` |

在 [GitHub Actions Secrets](https://github.com/AzusaKe/Halo/settings/secrets/actions) 配置 `MODRINTH_TOKEN` 和 `CURSEFORGE_TOKEN`。使用有权向上述项目发布文件的令牌；CurseForge 使用作者上传令牌，不是面向第三方查询应用的 API key。令牌不进入仓库、发布清单或聊天。

工作流提交并集成到默认分支后，先运行只读预览，再在明确的版本发布指令下完成首次真实上传。无需设置 Actions Variables；旧的 `HALO_AUTO_PUBLISH` 开关已移除，即使设置也不会启用 CI 完成后直接上传。发布指令中的整理正文、触发和跟踪由 agent 连续执行，不要求人类再次手动点击。

不要为接入自动化移动既有标签。需要向既有 Release 补发时，使用原成功标签构建的 run ID。

## 人类和 agent 的日常用法

人类指定本次版本、目标分支、更新日志和平台范围；agent 按开发指南检查验收记录并锁定 Halo/core SHA，先推送 core，再推送引用它的 Halo 分支。目标分支 CI 成功后推送其唯一平台标签。

标签推送仍会创建 GitHub Release，但不会直接分发到模组平台。agent 完成下面的正文定稿步骤后，根据人类指定的平台范围触发上传。仅检查或编辑发布说明的请求不等于要求上传模组。

发布流程：

1. 既有 CI 执行正式构建和相关检查，创建 GitHub Release。
2. agent 等待整个源 CI 成功，包括所有平台构建矩阵成员；编写最终说明并更新 GitHub Release。
3. agent 使用本地定稿文件触发发布。发布器核对正文哈希、源 workflow、run attempt、标签 SHA、目标维护分支祖先关系和 core gitlink。
4. 只选取完整文件名匹配的正式 JAR，排除源码包等附属产物。
5. 核对 GitHub asset 的 SHA-256、JAR 中的 `halo-build.json`、模组 ID 和版本。
6. 上传前逐平台重新读取 Release，确认正文仍与定稿快照一致；分别上传并保留每个平台的结果，某个平台失败不阻止另一个平台尝试。
7. 在 Actions Summary 和 artifact 中输出计划、平台状态和可用链接。

最终 GitHub Release 正文作为平台更新日志；上传器本身不改写或翻译。agent 先按人类要求修改正文，再触发上传。GitHub 正式 Release 对应 `release`，预发布对应 `beta`，版本名中含独立 `alpha` 段的预发布对应 `alpha`。

### agent 定稿与触发命令

将审核过的最终说明保存为 UTF-8 Markdown 文件，例如 `.local/release-notes.md`。在默认分支的工作区执行以下顺序；标签和 run ID 必须对应同一次已验收发布，示例不代表要求补发此版本：

```powershell
gh release edit v2.4.2-neoforge-26.2-adapter.1 --repo AzusaKe/Halo --notes-file .local/release-notes.md
if ($LASTEXITCODE -ne 0) { throw 'Release notes update failed' }
$env:GH_TOKEN = gh auth token
try {
    python scripts/release/dispatch.py --run-id 35518557732 --notes-file .local/release-notes.md --platform both --publish
} finally {
    Remove-Item Env:\GH_TOKEN
}
```

`dispatch.py` 将 GitHub 正文与该文件逐字比较，自动计算 SHA-256，再触发工作流。正文不同则不触发，避免文件尚未提交到 GitHub 就上传旧说明。不带 `--publish` 时只触发只读预览。命令结束只代表已触发，agent 还应跟踪运行结果并报告两个平台的状态。

定稿使用精确 UTF-8 内容，包含换行；不自动去空白、规范化或截断。如果定稿后需要继续改正文，应停止尚未上传的任务并重新定稿。跨平台没有原子事务：若一个平台已完成，后续正文修改需要单独处理其平台元数据，不能宣称修改会撤回已经完成的上传。

Modrinth 的版本号上限为 32 个字符，因此采用紧凑格式，例如 `2.4.2+neoforge.1.21.1.a1`，仍保留功能版本、加载器、游戏版本与适配修订。GitHub 标签和 JAR 文件名保持原约定。预检会检查长度限制，不静默截断版本号。

Fabric 版本声明 Fabric API 为必需依赖，Forge/NeoForge 不额外声明 Fabric API。core 已合并到 JAR，不作为需要玩家另行安装的依赖。

兼容版本采用每个分支的精确构建基线。`26.1-fabric` 的标签用 `26.1`，成品和平台兼容版本用 `26.1.2`；源文档注明完整三版本运行验收仍待完成，因此此配置不扩展为全部 `26.1.x`。平台尚未登记某个 Minecraft 版本或加载器时，该项失败，不自动替换成相邻版本。

## 手动预览与补发

GitHub Actions 中选择 **Publish Halo to mod platforms**，在默认分支运行：

- `run_id`：原成功标签构建的 ID。不要填普通分支构建 ID或发布器自己的运行 ID。
- `platform`：`both`、`modrinth` 或 `curseforge`。
- `dry_run`：默认 `true`，只读检查；只有明确选择 `false` 才上传。
- `notes_sha256`：真实上传必须提供最终正文的 SHA-256；建议使用上面的 `dispatch.py` 自动填写。只读预览可留空。

例如下面的命令只检查既有 `2.4.2` / `26.2-neoforge` Release，不上传：

```powershell
gh workflow run publish-platforms.yml --repo AzusaKe/Halo --ref 1.20.1-fabric -f run_id=35518557732 -f platform=both -f dry_run=true
```

本地预览需要 Python 3.11+，无需安装 Python 依赖。在仓库根目录执行：

```powershell
$env:GH_TOKEN = gh auth token
try {
    python scripts/release/publish.py --run-id 35518557732 --output .local/halo-publish
} finally {
    Remove-Item Env:\GH_TOKEN
}
```

本地上传脚本需要同时传入 `--publish` 和 `--notes-sha256 <最终正文哈希>` 才会上传。真实上传还需要环境变量中的平台令牌，推荐通过 `dispatch.py` 交给 Actions Secrets 注入，避免在本机处理平台令牌。

只读预览验证 GitHub CI 与正式成品，不验证平台令牌、上传 API 或审核结果。不能将预览通过写成发布验收通过。

## 去重、失败恢复和限制

每个平台在原 GitHub Release 下保存两个小型 JSON 附件：

- `halo-publish-<平台>.pending.json`：调用上传 API 前保存的意图记录。
- `halo-publish-<平台>.json`：成功后的回执，包括平台文件 ID 和链接。

附件只包含公开发布的标识和哈希，不包含令牌。平台名分别是 `modrinth` 和 `curseforge`。附件名称唯一，创建时不覆盖已有附件，这也用于阻止同一 Release 的并发重复上传。工作流对相同源 run 额外串行执行。

回执的 identity 固定版本标签、Halo/core SHA、成品 SHA-256、目标项目、兼容版本、加载器、发布类型、名称和更新日志。CI 的 run ID 或 attempt 改变不影响 identity。有一致回执时直接跳过该平台；回执冲突时停止，不覆盖已发布文件。

Modrinth 额外查询项目现有版本，以成品 SHA-512 检查是否已经上传，并核对加载器、游戏版本、发布类型、必需依赖和最终正文。已存在且一致的成品可以生成回执，不重复上传；已有平台正文不一致时停止并提示单独更新元数据，不以“文件已存在”冒充最终说明已经同步。

CurseForge 的文档化 Upload API 不提供按文件哈希查询现有上传的接口。因此：

- 首次补发历史 Release 前，须核对该成品是否已经通过人工或其他工具上传到 CurseForge。没有本流程回执不能证明平台上没有文件。
- 若网络超时、服务端错误或进程中断导致结果不明，保留 pending 记录并停止自动重试。
- 明确的请求拒绝（例如鉴权或元数据错误）会移除本次 pending，修正原因后可以补发。
- 正常成功上传与公开可下载是不同状态；CurseForge 可能仍在审核。

恢复时按实际证据处理：

1. 若上传已成功但写 GitHub 回执失败，先下载本次 Actions 的 JSON artifact；其中可能已有 `halo-publish-curseforge.json`。核对 ID 与平台文件后，将该回执作为新附件上传至原 Release。不要覆盖或替换 JAR，也不要重新上传模组。
2. 若只有 pending，先在平台核对对应文件；若已经存在，下载成品确认 SHA-256 与 pending/plan 一致，并核对项目、加载器、兼容版本和发布类型。用 pending 中的 `identity` 及已核实的文件 ID、链接构造成功回执，再上传至原 Release。
3. 若确认平台完全没有接受该文件，才删除该平台的 pending 附件，并重新运行仅该平台的补发。结果仍不明时保留 pending。

人工恢复的回执形状如下；其中所有值必须来自实际检查，不能照抄示例占位内容：

```json
{
  "identity": "从该发布的 pending 记录复制",
  "result": {
    "id": 123456,
    "url": "已核实的平台文件链接",
    "status": "reconciled_after_manual_verification"
  }
}
```

成功后修改 Release 正文会改变 identity。应将此视为平台元数据修订，单独核对和更新平台说明；不要通过删除回执重新上传同一 JAR。

## 验证和维护

自动测试：

```powershell
python -m unittest discover -s scripts/release -p 'test_*.py' -v
```

`test-publisher.yml` 会在发布脚本或配置变化时运行测试。测试覆盖来源校验、标签移动、构建矩阵失败、源码包排除、开发包拒绝、提交与版本不匹配、只读默认行为、单平台失败、重复运行以及上传结果不明时的恢复路径。

2026-09-20 的本地验证已对全部 9 个 `2.4.2` GitHub Release 运行只读预检，均通过正式 JAR、SHA-256、Halo/core SHA 和标签一致性检查。没有进行平台真实上传、令牌鉴权或审核状态验证。此次不涉及模组运行代码，未重新执行游戏或 Gradle 验收。

增加新维护分支、改变构建 JAR 命名、调整 CI 名称/构建 job 名称/Release step 名称时，需要同步更新配置或校验。发布器仅接受当前约定的三段功能版本及平台标签，旧 flash 和其他标签保持拒绝。

参考：[GitHub workflow_dispatch](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#workflow_dispatch)、[Modrinth 创建版本 API](https://docs.modrinth.com/api/operations/createversion/)、[CurseForge Upload API](https://support.curseforge.com/support/solutions/articles/9000197321)。
