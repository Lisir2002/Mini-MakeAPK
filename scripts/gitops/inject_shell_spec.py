#!/usr/bin/env python3
"""壳契约注入 / 构建管线（方向 1 闭环：「编辑 → 导出 APK」）。

把「任意项目导出的 ShellSpec JSON」注入到子模板工程的编译入口
`template/src/main/res/raw/shell_spec.json`，并可选地直接构建导出 APK，
从而打通 **预览 == 导出 ⟺ 同一份 ShellSpec + 同一份 ShellHost** 的工程闭环：

    app 编辑壳 → 导出 ShellSpec.json → (本脚本注入) → template 编译 → template-debug.apk
                                                  ↑ 同一份 JSON 被 app 预览与 template 导出共用

## 契约形态

注入的 JSON 必须是 `ShellSpec` 的**编码形态**（与 app 端 `Json{ignoreUnknownKeys=true}`
以及 `ShellActionSurrogate` 解码一致）：

  - 顶层：`specVersion`(int, 默认 1) + 四个区 `topBar / topTabs / bottomBar / drawer`；
  - 条目 `ShellMenuItem`：`id`(str) `title`(str) `iconRef`(str|null)
    `action`(obj) `visible`(bool) `selected`(bool)；
  - `action` 用判别符形态：`{"type":"url","url":"...","openInNewTab":bool}`
    / `{"type":"local_page","path":"..."}`（项目内本地页面，**非 URL**：path 不做任何规范化）
    / `{"type":"native","kind":"..."}` / `{"type":"internal_page","pageId":"..."}`
    / `{"type":"unknown"}`（未知 type 由 app 端降级，本脚本拒绝非白名单 type 以保证质量）；
  - 枚举：`selectionStyle∈{INDICATOR,PILL,TEXT}`、`contentSource∈{NATIVE,WEB}`、
    `width.mode∈{SMALL,MEDIUM,LARGE,CUSTOM}`。

## 本地页面注入（`--project`，让导出 APK 的本地页面真能打开）

`local_page` 的 `path` 是**项目内的文件名**，壳契约本身不携带内容 —— 只注入壳契约会
导出一个"点了没反应"的 APK。故本脚本支持一并注入项目页面：

  - `--project` 指向 `RuntimeJson.encodeEntry(entry)` 的产物，即
    `{"type":"html","html":"...","entryFile":"index.html","files":[{"name":..,"content":..}]}`
    （也接受 `{"entry":{...}}` 包装形态）；非 `html` 型入口（URL 项目）没有本地页面；
  - 注入位置：`template/src/main/assets/mma_pages/<path>` —— **选 assets 而非 res/raw**，
    因为 `res/raw` 的文件名不允许 `/`（无法保留 `page/about.html` 这类相对路径子目录），
    assets 则原样保留目录结构、文件名限制也宽松得多；
  - 导出壳用 WebView 标准的 `file:///android_asset/mma_pages/<path>` 直读
    （**零新增依赖**：不引入浏览器内核、不拷贝文件到私有目录、不需要本地 HTTP 服务）；
  - 重新注入前会**清空** `mma_pages` 目录（只清这一个目录，避免上一项目的残留文件混入）；
  - **构建期硬校验**：壳里每个 `local_page.path` 必须在注入文件集中存在且是 `.html/.htm`，
    否则**拒绝注入并报错** —— 把"点了没反应"拦在构建期，而不是留给用户去猜；
  - **文件名白名单**：项目文件名是不可信输入，含控制字符（含 NUL）、长度 > 128、
    含反斜杠（Windows 上 `\\` 是分隔符，`..\..\evil.html` 会真的写出 `assets/` 之外）
    一律入库前拒绝并 `::error::` 留痕，绝不让它走到写盘去抛 traceback；
  - **原子替换**：页面先写暂存目录 `mma_pages.staging`，全部成功后整体 `os.rename` 成
    `mma_pages`——不存在"壳已写入 / 页面半套"的半写入态（那会让 `--print` 误导排查）。

## 用法

    # 校验一份导出壳（不写盘，仅校验）：
    python3 scripts/gitops/inject_shell_spec.py --check path/to/shell_spec.json

    # 注入并构建导出 APK（闭环一键）：
    python3 scripts/gitops/inject_shell_spec.py --in path/to/shell_spec.json --build

    # 壳里含「本地页面」条目：必须同时给 --project（否则拒绝注入）：
    python3 scripts/gitops/inject_shell_spec.py --in spec.json --project entry.json --build

    # 仅注入不构建（CI 门禁/本地预览用）：
    python3 scripts/gitops/inject_shell_spec.py --in path/to/shell_spec.json

    # 打印当前工程内已注入的壳（自检）：
    python3 scripts/gitops/inject_shell_spec.py --print

退出码：0 = 成功；1 = 校验/写入失败；2 = 环境错误（缺依赖/构建失败）。
"""
import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
TARGET = REPO_ROOT / "template" / "src" / "main" / "res" / "raw" / "shell_spec.json"
GRADLEW = REPO_ROOT / "gradlew"

# 本地页面注入位置：assets 保留相对路径子目录（res/raw 不允许文件名含 "/"）。
# 目录名与 template/TemplateActivity.LOCAL_PAGE_ASSET_DIR 必须一致。
LOCAL_PAGE_ASSET_DIR = "mma_pages"
PAGES_TARGET = REPO_ROOT / "template" / "src" / "main" / "assets" / LOCAL_PAGE_ASSET_DIR

# 白名单（与 Kotlin `ShellSpec` / `ShellActionSurrogate` 严格对齐）。
ACTION_TYPES = {"url", "local_page", "native", "internal_page", "unknown"}
SELECTION_STYLES = {"INDICATOR", "PILL", "TEXT"}
DRAWER_SOURCES = {"NATIVE", "WEB"}
DRAWER_WIDTH_MODES = {"SMALL", "MEDIUM", "LARGE", "CUSTOM"}
# 可加载页面扩展名（与 Kotlin `isLocalPageFileName` 对齐）。
PAGE_EXTENSIONS = (".html", ".htm")
# 项目入口 JSON 的判别符（RuntimeJson.encodeEntry：ProjectEntry.BundledHtml 的 @SerialName）。
ENTRY_TYPE_HTML = "html"

# ── 文件名白名单（M1/M2 加固）────────────────────────────────────────────────
# 项目文件名来自用户导出的 JSON，是**不可信输入**：不设闸会把脚本打成 traceback 退出
# （traceback 退出码 1 但无 ::error::，CI/人肉都看不出原因），更糟的是此时
# shell_spec.json 已写盘、mma_pages 已被 rmtree —— 工程停在半写入态，
# 后续 `--print` 报"未注入/页面缺失"会误导排查方向。故在**入库**（而非写盘）时拦截：
MAX_FILE_NAME_LENGTH = 128  # 单段名上限；留足余量避免撞 ENAMETOOLONG（Linux 255 / Windows 260）

# 页面暂存目录名（原子替换的中转站，与 PAGES_TARGET 同级 → 同文件系统 → os.rename 原子）。
PAGES_STAGING_SUFFIX = ".staging"


# ── 校验器 ──────────────────────────────────────────────────────────────────
def _err(msg: str) -> str:
    return msg


def validate_spec(obj, path: str = "$") -> list[str]:
    """返回错误列表（空 = 通过）。递归校验 ShellSpec 编码形态。"""
    errs: list[str] = []
    if not isinstance(obj, dict):
        return [_err(f"{path}: 顶层必须是对象")]

    # specVersion
    sv = obj.get("specVersion", 1)
    if not isinstance(sv, int) or isinstance(sv, bool):
        errs.append(_err(f"{path}.specVersion: 期望 int，实为 {type(sv).__name__}"))

    regions = {
        "topBar": obj.get("topBar"),
        "topTabs": obj.get("topTabs"),
        "bottomBar": obj.get("bottomBar"),
        "drawer": obj.get("drawer"),
    }
    for name, region in regions.items():
        if region is None:
            continue
        if not isinstance(region, dict):
            errs.append(_err(f"{path}.{name}: 期望对象或缺失，实为 {type(region).__name__}"))
            continue
        # actions / items 列表
        for list_key in ("actions", "items"):
            items = region.get(list_key)
            if list_key in region:
                if not isinstance(items, list):
                    errs.append(_err(f"{path}.{name}.{list_key}: 期望数组，实为 {type(items).__name__}"))
                else:
                    for i, it in enumerate(items):
                        errs.extend(_validate_item(it, f"{path}.{name}.{list_key}[{i}]"))
        # 枚举校验
        if name == "topTabs":
            ss = region.get("selectionStyle", "INDICATOR")
            if ss not in SELECTION_STYLES:
                errs.append(_err(f"{path}.{name}.selectionStyle: 取值 {ss!r} 不在 {sorted(SELECTION_STYLES)}"))
        if name == "drawer":
            cs = region.get("contentSource", "NATIVE")
            if cs not in DRAWER_SOURCES:
                errs.append(_err(f"{path}.{name}.contentSource: 取值 {cs!r} 不在 {sorted(DRAWER_SOURCES)}"))
            w = region.get("width")
            if w is not None:
                if not isinstance(w, dict):
                    errs.append(_err(f"{path}.{name}.width: 期望对象，实为 {type(w).__name__}"))
                else:
                    m = w.get("mode", "MEDIUM")
                    if m not in DRAWER_WIDTH_MODES:
                        errs.append(_err(f"{path}.{name}.width.mode: 取值 {m!r} 不在 {sorted(DRAWER_WIDTH_MODES)}"))
    return errs


def _validate_item(item: object, loc: str) -> list[str]:
    errs: list[str] = []
    if not isinstance(item, dict):
        return [_err(f"{loc}: 条目必须是对象")]
    if not isinstance(item.get("id"), str):
        errs.append(_err(f"{loc}.id: 期望字符串（稳定标识）"))
    if not isinstance(item.get("title"), str):
        errs.append(_err(f"{loc}.title: 期望字符串"))
    icon = item.get("iconRef")
    if icon is not None and not isinstance(icon, str):
        errs.append(_err(f"{loc}.iconRef: 期望字符串或 null"))
    # action
    act = item.get("action")
    if act is None:
        # 允许缺省（= NavigateUrl("") 默认值），app 端会兜底
        pass
    elif not isinstance(act, dict):
        errs.append(_err(f"{loc}.action: 期望对象，实为 {type(act).__name__}"))
    else:
        t = act.get("type")
        if t not in ACTION_TYPES:
            errs.append(_err(f"{loc}.action.type: 取值 {t!r} 不在白名单 {sorted(ACTION_TYPES)}（app 端会降级，但本脚本拒绝以保证质量）"))
        if t == "url" and not isinstance(act.get("url"), str):
            errs.append(_err(f"{loc}.action.url: type=url 时 url 必须是字符串（含空串）"))
        # local_page 的 path 是**项目内文件名**（非 URL），只做字符串校验，绝不规范化。
        if t == "local_page":
            p = act.get("path")
            if not isinstance(p, str):
                errs.append(_err(f"{loc}.action.path: type=local_page 时 path 必须是字符串（项目内页面文件名）"))
            elif not p.strip():
                errs.append(_err(f"{loc}.action.path: 不得为空（本地页面必须指向项目内真实文件）"))
        if t == "native" and not isinstance(act.get("kind"), str):
            errs.append(_err(f"{loc}.action.kind: type=native 时 kind 必须是字符串"))
        if t == "internal_page" and not isinstance(act.get("pageId"), str):
            errs.append(_err(f"{loc}.action.pageId: type=internal_page 时 pageId 必须是字符串"))
    vis = item.get("visible", True)
    if not isinstance(vis, bool):
        errs.append(_err(f"{loc}.visible: 期望布尔，实为 {type(vis).__name__}"))
    sel = item.get("selected", False)
    if not isinstance(sel, bool):
        errs.append(_err(f"{loc}.selected: 期望布尔，实为 {type(sel).__name__}"))
    return errs


# ── 本地页面（项目文件）──────────────────────────────────────────────────────

def local_page_paths(obj: object) -> list[str]:
    """取壳契约里全部 `local_page` 的 path（按出现顺序，去重保序）。"""
    paths: list[str] = []
    if not isinstance(obj, dict):
        return paths
    for region in ("topBar", "topTabs", "bottomBar", "drawer"):
        r = obj.get(region)
        if not isinstance(r, dict):
            continue
        for list_key in ("actions", "items"):
            items = r.get(list_key)
            if not isinstance(items, list):
                continue
            for it in items:
                if not isinstance(it, dict):
                    continue
                act = it.get("action")
                if isinstance(act, dict) and act.get("type") == "local_page":
                    p = act.get("path")
                    if isinstance(p, str) and p.strip() and p not in paths:
                        paths.append(p)
    return paths


def unsafe_file_name_reason(name: str) -> str | None:
    """
    文件名白名单判定：返回不安全原因（None = 安全）。

    拦三类不可信输入（M1/M2）：
      - **反斜杠**：Windows 的 `Path` 把 `\\` 当分隔符，`..\\..\\evil.html` 会真的写出
        `assets/mma_pages` 之外（Linux 上只是个怪文件名，故最容易漏）——一律拒绝，不做规范化
        （规范化会让"能写成功"与"写到哪"随平台漂移，不如直接拒绝来得明确）；
      - **控制字符**（含 NUL、换行）：`open()/write_bytes()` 直接抛 `ValueError`，不拦就 traceback；
      - **超长名**：撞 `ENAMETOOLONG`（`OSError`），同样落到 traceback。

    注意：这里只看**字符**，路径结构（`..` / 绝对路径 / `./`）由调用方另行判定。
    """
    if "\\" in name:
        return "含反斜杠（Windows 上会被当作路径分隔符，可能写出注入目录之外）"
    ctrl = sorted({ord(c) for c in name if ord(c) < 32 or ord(c) == 127})
    if ctrl:
        return f"含控制字符（码位 {ctrl}）——写盘会被操作系统拒绝"
    if len(name) > MAX_FILE_NAME_LENGTH:
        return f"长度 {len(name)} 超过上限 {MAX_FILE_NAME_LENGTH}"
    return None


def load_project_files(project_path: Path) -> tuple[list[tuple[str, str]], list[str]]:
    """
    读取项目入口 JSON，展开为 `(文件名, 内容)` 列表（入口在前，其余按存储序）。

    接受两种形态：
    - `RuntimeJson.encodeEntry(entry)` 直出：`{"type":"html","html":..,"entryFile":..,"files":[..]}`；
    - 外层包装：`{"entry": {上面那一份}}`。

    返回 `(files, errs)`；非 html 型入口返回空文件集（URL 项目没有本地页面）。
    """
    try:
        obj = json.loads(project_path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        return [], [f"找不到项目文件 {project_path}"]
    except json.JSONDecodeError as e:
        return [], [f"{project_path} 不是合法 JSON：{e}"]
    if not isinstance(obj, dict):
        return [], [f"{project_path}: 顶层必须是对象"]
    if isinstance(obj.get("entry"), dict):
        obj = obj["entry"]
    if obj.get("type") != ENTRY_TYPE_HTML:
        return [], []  # URL / 资产包项目：没有本地页面（由调用方按 spec 是否引用再判）

    files: list[tuple[str, str]] = []
    seen: set[str] = set()
    errs: list[str] = []

    def add(name: object, content: object) -> None:
        n = name.strip() if isinstance(name, str) else ""
        if not n or n.endswith("/"):
            return  # 空名 / 目录条目不是文件
        # ① 字符白名单（控制字符 / 超长 / 反斜杠）——必须在任何路径判定之前，
        #    否则 `..\..\evil.html` 在 Windows 上会先被 Path 当分隔符吃掉再拼出越界路径。
        reason = unsafe_file_name_reason(n)
        if reason is not None:
            errs.append(f"项目文件名不安全，已跳过：{n!r}（{reason}）")
            return
        # ② 路径结构判定（此时 n 已保证不含反斜杠，`/` 是唯一分隔符）。
        if n.startswith("/") or ".." in n.split("/") or n.startswith("./"):
            errs.append(f"项目文件名不安全，已跳过：{n!r}（不得为绝对路径或含 ..）")
            return
        if n in seen:
            return
        seen.add(n)
        files.append((n, content if isinstance(content, str) else ""))

    add(obj.get("entryFile") or "index.html", obj.get("html") or "")
    extra = obj.get("files") or []
    if not isinstance(extra, list):
        errs.append("项目 files 必须是数组")
        extra = []
    for f in extra:
        if not isinstance(f, dict):
            errs.append("项目 files 的每一项必须是对象")
            continue
        add(f.get("name"), f.get("content"))
    return files, errs


def validate_local_pages(spec_obj: object, file_names: list[str]) -> list[str]:
    """
    构建期硬校验：壳引用的每个本地页面都必须**真实存在于项目文件里**且是页面文件。

    目的：把"导出 APK 里点了没反应"拦在构建期（用户几乎无法自查这一类）。
    """
    errs: list[str] = []
    known = set(file_names)
    for p in local_page_paths(spec_obj):
        if p not in known:
            errs.append(
                f"壳引用了本地页面 {p!r}，但项目里没有这个文件（导出后会「点了没反应」）；"
                f"可用页面：{sorted(known) or '（空）'}"
            )
        elif not p.lower().endswith(PAGE_EXTENSIONS):
            errs.append(f"本地页面 {p!r} 不是可加载页面（须为 .html / .htm）")
    return errs


def staging_pages_dir() -> Path:
    """页面暂存目录（与 PAGES_TARGET 同级 → 同文件系统 → 提交时 os.rename 是原子操作）。"""
    return PAGES_TARGET.parent / (PAGES_TARGET.name + PAGES_STAGING_SUFFIX)


def stage_project_pages(files: list[tuple[str, str]]) -> tuple[Path, int, int]:
    """
    把项目页面先写进**暂存目录**（相对路径子目录原样保留），此时**目标目录完全未被触碰**。

    M1 关键：写盘失败（NUL 字节 → ValueError / ENAMETOOLONG → OSError）时自行清理暂存目录
    并把异常抛给调用方，工程保持"上一套完整页面 + 上一版壳"的原状，绝不出现半写入态。

    返回 `(暂存目录, 文件数, 总字节数)`；调用方须用 `commit_project_pages()` 提交。
    """
    staging = staging_pages_dir()
    if staging.is_dir():
        shutil.rmtree(staging)  # 上次异常退出留下的残骸
    staging.mkdir(parents=True, exist_ok=True)

    total = 0
    try:
        for name, content in files:
            out = staging / name
            out.parent.mkdir(parents=True, exist_ok=True)
            data = content.encode("utf-8")
            out.write_bytes(data)
            total += len(data)
    except BaseException:
        # 半套暂存文件没有价值：清掉，避免下次注入把它当"上一版"或误编进 APK。
        shutil.rmtree(staging, ignore_errors=True)
        raise
    return staging, len(files), total


def commit_project_pages(staging: Path, count: int, total: int) -> tuple[int, int]:
    """
    把 `stage_project_pages()` 的产物**原子替换**进 `PAGES_TARGET`（先清后移）。

    暂存目录与目标目录同级 ⇒ `shutil.move` 走 `os.rename`（同一文件系统，原子），
    不存在" rm 掉旧目录后、新目录还没到位"的中间窗口。
    """
    if PAGES_TARGET.is_dir():
        # 只清本地页面目录本身：绝不触碰工程其它资产。
        shutil.rmtree(PAGES_TARGET)
    elif PAGES_TARGET.exists():
        PAGES_TARGET.unlink()  # 极少数情况目标是个文件/链接
    PAGES_TARGET.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(staging), str(PAGES_TARGET))
    return count, total


def inject_project_pages(files: list[tuple[str, str]]) -> tuple[int, int]:
    """
    把项目页面写进 `template/src/main/assets/mma_pages/`（相对路径子目录原样保留）。

    = `stage_project_pages()` + `commit_project_pages()`：**先暂存成功后原子替换**，
    目标目录只在"全部文件已写好"的瞬间被整体换掉。返回 `(文件数, 总字节数)`。
    """
    staging, count, total = stage_project_pages(files)
    return commit_project_pages(staging, count, total)


def clear_project_pages() -> None:
    """移除已注入的本地页面目录（项目不再含本地页面时用，保持产物干净）。"""
    if PAGES_TARGET.is_dir():
        shutil.rmtree(PAGES_TARGET)
    # 顺带清暂存残骸：它若留在 assets/ 下会被打进 APK（未被引用的幽灵页面）。
    shutil.rmtree(staging_pages_dir(), ignore_errors=True)


# ── 命令 ────────────────────────────────────────────────────────────────────
def cmd_check(src: Path) -> int:
    try:
        text = src.read_text(encoding="utf-8")
        obj = json.loads(text)
    except FileNotFoundError:
        sys.stderr.write(f"::error::找不到文件 {src}\n")
        return 2
    except json.JSONDecodeError as e:
        sys.stderr.write(f"::error::{src} 不是合法 JSON：{e}\n")
        return 1
    errs = validate_spec(obj)
    if errs:
        sys.stderr.write(f"::error::{src} 壳契约校验失败（{len(errs)} 处）：\n")
        for e in errs:
            sys.stderr.write(f"  - {e}\n")
        return 1
    print(f"校验通过：{src} 是合法 ShellSpec（specVersion={obj.get('specVersion', 1)}，"
          f"topTabs={len(obj.get('topTabs', {}).get('items', []))} 项，"
          f"bottomBar={len(obj.get('bottomBar', {}).get('items', []))} 项，"
          f"drawer={len(obj.get('drawer', {}).get('items', []))} 项）")
    return 0


def cmd_inject(src: Path, project: Path | None, build: bool) -> int:
    rc = cmd_check(src)
    if rc != 0:
        return rc
    text = src.read_text(encoding="utf-8")
    # 规范化写盘（保留原缩进/字段顺序，便于可审阅 diff）。
    obj = json.loads(text)

    # ── 本地页面：壳引用了就必须能提供内容（构建期硬校验）──
    referenced = local_page_paths(obj)
    files: list[tuple[str, str]] = []
    if project is not None:
        files, file_errs = load_project_files(project)
        if file_errs:
            sys.stderr.write(f"::error::{project} 项目文件校验失败：\n")
            for e in file_errs:
                sys.stderr.write(f"  - {e}\n")
            return 1
    if referenced:
        if project is None:
            sys.stderr.write(
                f"::error::壳引用了 {len(referenced)} 个本地页面（{referenced}），"
                f"但未提供 --project：只注入壳契约会导出「点了没反应」的 APK\n"
            )
            return 1
        if not files:
            sys.stderr.write(
                f"::error::壳引用了 {len(referenced)} 个本地页面（{referenced}），"
                f"但项目入口不是 html 型（URL 项目没有本地页面）\n"
            )
            return 1
    page_errs = validate_local_pages(obj, [name for name, _ in files])
    if page_errs:
        sys.stderr.write("::error::本地页面校验失败（构建期拦截）：\n")
        for e in page_errs:
            sys.stderr.write(f"  - {e}\n")
        return 1

    # ── 顺序：先暂存页面 → 再写壳 → 最后原子替换 ──
    # M1：旧实现是"先写壳 → rmtree 旧页面 → 逐个写新页面"，中途抛异常（NUL / 超长名）
    # 会留下「壳是新的、页面是空的」半写入态，且以 traceback 退出（无 ::error::），
    # 后续 `--print` 只报"本地页面未注入"，排查方向被带偏。现在任一步失败都原样退出。
    staged: tuple[Path, int, int] | None = None
    if files:
        try:
            staged = stage_project_pages(files)
        except (OSError, ValueError) as e:
            sys.stderr.write(
                f"::error::本地页面写入失败（{type(e).__name__}）：{e}\n"
                f"  → 工程未被改动：壳与 assets/{LOCAL_PAGE_ASSET_DIR} 均保持原状，"
                f"修正项目文件后可重新注入\n"
            )
            return 1

    try:
        TARGET.parent.mkdir(parents=True, exist_ok=True)
        TARGET.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    except (OSError, ValueError) as e:
        if staged is not None:
            shutil.rmtree(staged[0], ignore_errors=True)  # 壳没写成 → 暂存页面也不要留
        sys.stderr.write(
            f"::error::壳契约写入失败：{TARGET}（{type(e).__name__}）：{e}\n"
            f"  → 工程未被改动，可修正后重新注入\n"
        )
        return 1
    print(f"已注入：{src} → {TARGET}（{len(text)} 字节）")

    # 页面落位（或清理）：有则原子替换，无则清空残留。
    if staged is not None:
        try:
            count, total = commit_project_pages(*staged)
        except (OSError, ValueError) as e:
            sys.stderr.write(
                f"::error::本地页面落位失败（{type(e).__name__}）：{e}\n"
                f"  → 壳已注入但页面仍是上一套：请重新执行一次注入以自愈\n"
            )
            return 1
        print(
            f"已注入本地页面：{count} 个文件 → {PAGES_TARGET.relative_to(REPO_ROOT)}"
            f"/<path>（{total} 字节 ≈ {total/1024:.1f} KB）"
        )
        print(
            "体积影响：增量 = 页面文件本身（文本经 APK zip 压缩后通常远小于原始字节数）；"
            "页面是用户自己的网页内容，不含任何新增运行时/内核"
        )
    else:
        clear_project_pages()
        print("本地页面：本壳未引用（已清空 assets/mma_pages 残留）")

    if build:
        return run_build(referenced)
    return 0


def run_build(referenced_pages: list[str] | None = None) -> int:
    referenced_pages = referenced_pages or []
    if not GRADLEW.is_file():
        sys.stderr.write(f"::error::找不到 gradlew（{GRADLEW}）\n")
        return 2
    print("构建导出 APK：./gradlew :template:assembleDebug")
    proc = subprocess.run([str(GRADLEW), ":template:assembleDebug", "--no-daemon"], cwd=REPO_ROOT)
    if proc.returncode != 0:
        sys.stderr.write("::error::模板构建失败\n")
        return 2
    apk = REPO_ROOT / "template" / "build" / "outputs" / "apk" / "debug" / "template-debug.apk"
    if apk.is_file():
        size = apk.stat().st_size
        print(f"导出完成：{apk}（{size/1024/1024:.1f} MB）")
        # 自检：APK 内确实嵌入了刚注入的壳契约。
        import zipfile
        with zipfile.ZipFile(apk) as z:
            names = z.namelist()
        embedded = "res/raw/shell_spec.json" in names
        print(f"自检：APK 内 res/raw/shell_spec.json {'已嵌入 ✓' if embedded else '缺失 ✗'}")
        # 自检：壳引用的本地页面确实被打进 APK（否则真机上就是"点了没反应"）。
        pages = [n for n in names if n.startswith(f"assets/{LOCAL_PAGE_ASSET_DIR}/")]
        missing = [p for p in referenced_pages if f"assets/{LOCAL_PAGE_ASSET_DIR}/{p}" not in names]
        print(f"自检：APK 内本地页面 {len(pages)} 个"
              f"{'' if not referenced_pages else f'（壳引用 {len(referenced_pages)} 个）'}"
              f"{' 全部命中 ✓' if referenced_pages and not missing else ''}")
        if referenced_pages:
            for p in missing:
                sys.stderr.write(f"::error::本地页面 {p} 未打进 APK\n")
            if not embedded or missing:
                return 2
        return 0 if embedded else 2
    sys.stderr.write("::error::构建产物缺失 template-debug.apk\n")
    return 2


def cmd_print() -> int:
    if not TARGET.is_file():
        sys.stderr.write(f"::error::未找到注入的壳 {TARGET}\n")
        return 2
    try:
        obj = json.loads(TARGET.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        sys.stderr.write(f"::error::注入的壳不是合法 JSON：{e}\n")
        return 1
    errs = validate_spec(obj)
    if errs:
        sys.stderr.write(f"::error::当前注入的壳校验失败：\n")
        for e in errs:
            sys.stderr.write(f"  - {e}\n")
        return 1
    print(f"当前注入壳：specVersion={obj.get('specVersion', 1)}，"
          f"四区 enabled = "
          f"topBar={obj.get('topBar', {}).get('enabled', False)}/"
          f"topTabs={obj.get('topTabs', {}).get('enabled', False)}/"
          f"bottomBar={obj.get('bottomBar', {}).get('enabled', False)}/"
          f"drawer={obj.get('drawer', {}).get('enabled', False)}")
    # 本地页面自检：壳引用了就必须已注入。
    page_files = sorted(
        str(p.relative_to(PAGES_TARGET)).replace("\\", "/")
        for p in PAGES_TARGET.rglob("*") if p.is_file()
    ) if PAGES_TARGET.is_dir() else []
    referenced = local_page_paths(obj)
    print(f"本地页面：壳引用 {len(referenced)} 个 {referenced or ''}；"
          f"assets/{LOCAL_PAGE_ASSET_DIR} 已注入 {len(page_files)} 个文件")
    missing = [p for p in referenced if p not in page_files]
    if missing:
        sys.stderr.write(f"::error::以下本地页面未注入：{missing}\n")
        return 1
    return 0


def main() -> int:
    p = argparse.ArgumentParser(description="壳契约注入 / 构建管线")
    p.add_argument("--in", dest="src", type=Path, help="要注入的 ShellSpec JSON 路径")
    p.add_argument("--check", dest="check", type=Path, help="仅校验该 ShellSpec（不写盘）")
    p.add_argument(
        "--project",
        dest="project",
        type=Path,
        help="项目入口 JSON（RuntimeJson.encodeEntry 产物）：壳含 local_page 时必填，用于注入页面内容",
    )
    p.add_argument("--print", action="store_true", help="打印当前工程内已注入的壳")
    p.add_argument("--build", action="store_true", help="注入后构建导出 APK（需配合 --in）")
    args = p.parse_args()

    if args.check:
        return cmd_check(args.check)
    if args.print:
        return cmd_print()
    if args.src:
        return cmd_inject(args.src, args.project, args.build)
    sys.stderr.write("用法：--in <json> [--project <entry.json>] [--build] | --check <json> | --print\n")
    return 2


if __name__ == "__main__":
    sys.exit(main())
