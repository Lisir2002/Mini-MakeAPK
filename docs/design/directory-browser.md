# 目录浏览器组件（DirectoryBrowser）

> 状态：已生效（rc12 重构）
> 可复用、与数据源解耦的卡片列表式文件管理器。本文说明其定位、模型与接入方式。

## 1. 定位

`ui/components/DirectoryBrowser.kt` 是一个**纯 UI 组件**：它只认中性模型
`FsItem`（`ui/fs/FileSystem.kt`）与一组操作回调 `DirectoryBrowserCallbacks`，
不依赖任何业务实体（`ProjectFile` / `ProjectEntry` 等）。因此「项目代码」页只是
第一个接入方，后续资产包浏览、导出产物浏览等页面可直接复用。

设计目标（参照主流网盘 / 文件 App）：

- **逐层进入**：列表只显示当前目录的直接子项，点文件夹进入下一层；
- **当前目录头 + 面包屑**：显示"根目录"/当前目录名，子目录提供"返回上一级"与可点路径段；
- **卡片条目**：圆角卡片 + 左侧圆角图标底（按扩展名映射图标与底色 token）+ 两行文本；
- **操作全部上抛**：新建 / 重命名 / 删除 / 复制 / 剪切 / 粘贴均经回调交给业务层落库；
- **剪贴态内部持有**：复制 / 剪切的源路径 + 模式由组件内 `ClipboardState` 暂存
  （**不跨组件全局单例**），进入其他目录后由"粘贴"按钮触发 `onPaste`。

## 2. 中性模型（ui/fs/FileSystem.kt）

| 类型 | 说明 |
|------|------|
| `RawFsEntry(name, sizeBytes)` | 适配器注入的原始路径条目（`name` 以 `/` 结尾表示空目录约定） |
| `FsItem(name, path, isDirectory, sizeBytes, childCount, isEntry)` | 中性树节点（扁平列表） |
| `PasteMode` / `PasteRequest` | 粘贴模式与请求（源路径 + 目标目录） |

纯函数（均有单测 `ui/fs/FileSystemTest.kt`）：

- `buildFsItems(entries, entryPath)` — 原始路径 → 中性节点列表（目录来自显式空目录条目与
  文件路径前缀推导，二者共存；自动排序、计算 `childCount`）；
- `validateFsName(rawName, existingSiblingPaths)` — 结构性校验（非空 / 无 `\`、`..` /
  前缀冲突），返回错误文案常量；
- `resolveCopyName(desiredName, taken)` — 同名冲突自动改名（`name 副本` / `name(2)`…）；
- `renamePathPrefix(path, from, to)` — 路径前缀替换（重命名 / 移动 / 目录复制核心）。

## 3. 空文件夹约定

`List<ProjectFile>` 无法表达空目录，约定：**目录以 `name` 以 `/` 结尾、`content` 为空**
的 `ProjectFile` 条目持久化（如 `assets/img/`）。`buildFsItems` 同时支持显式空目录条目与
由文件路径隐式推导出的目录，旧数据天然兼容（无此类条目 = 无空目录）。

## 4. 接入方式（以「项目代码」页为例）

`ui/editor/ProjectCodeTab.kt` 作为**薄适配层**：

1. 把 `List<ProjectFile>`（含入口 HTML 视作一个带 `isEntry` 的文件）映射为 `RawFsEntry`，
   调用 `buildFsItems` 得到 `List<FsItem>`；
2. 把组件回调转接到 ViewModel：
   - `onAddFile` / `onFolderAdded` → `onProjectFileAdded` / `onProjectFolderAdded`
   - `onRename` → `onProjectFileRenamed`（前缀替换，目录递归）
   - `onDelete` → `onProjectFileDeleted`（目录递归）
   - `onPaste` → `onProjectFilesPasted`（复制 / 剪切落库）

ViewModel 全走现有"更新 UiState → [save] 落库"同一路径，无独立文件表、无 schema 迁移。

## 5. 设计令牌

组件所有颜色取自 `MaterialTheme.colorScheme` 或 `LocalMmaDesign`，圆角取自
`ControlShapes` / `MaterialTheme.shapes`，间距取自 `Spacing`，字号取自 `MaterialTheme.typography`，
严禁字面量颜色 / 圆角 / 间距 / 字号（CI 门禁 `scripts/gitops/check_token_usage.py`）。
