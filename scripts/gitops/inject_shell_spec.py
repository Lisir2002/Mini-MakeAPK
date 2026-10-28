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
    / `{"type":"native","kind":"..."}` / `{"type":"internal_page","pageId":"..."}`
    / `{"type":"unknown"}`（未知 type 由 app 端降级，本脚本拒绝非白名单 type 以保证质量）；
  - 枚举：`selectionStyle∈{INDICATOR,PILL,TEXT}`、`contentSource∈{NATIVE,WEB}`、
    `width.mode∈{SMALL,MEDIUM,LARGE,CUSTOM}`。

## 用法

    # 校验一份导出壳（不写盘，仅校验）：
    python3 scripts/gitops/inject_shell_spec.py --check path/to/shell_spec.json

    # 注入并构建导出 APK（闭环一键）：
    python3 scripts/gitops/inject_shell_spec.py --in path/to/shell_spec.json --build

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

# 白名单（与 Kotlin `ShellSpec` / `ShellActionSurrogate` 严格对齐）。
ACTION_TYPES = {"url", "native", "internal_page", "unknown"}
SELECTION_STYLES = {"INDICATOR", "PILL", "TEXT"}
DRAWER_SOURCES = {"NATIVE", "WEB"}
DRAWER_WIDTH_MODES = {"SMALL", "MEDIUM", "LARGE", "CUSTOM"}


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


def cmd_inject(src: Path, build: bool) -> int:
    rc = cmd_check(src)
    if rc != 0:
        return rc
    text = src.read_text(encoding="utf-8")
    # 规范化写盘（保留原缩进/字段顺序，便于可审阅 diff）。
    obj = json.loads(text)
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"已注入：{src} → {TARGET}（{len(text)} 字节）")
    if build:
        return run_build()
    return 0


def run_build() -> int:
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
    return 0


def main() -> int:
    p = argparse.ArgumentParser(description="壳契约注入 / 构建管线")
    p.add_argument("--in", dest="src", type=Path, help="要注入的 ShellSpec JSON 路径")
    p.add_argument("--check", dest="check", type=Path, help="仅校验该 ShellSpec（不写盘）")
    p.add_argument("--print", action="store_true", help="打印当前工程内已注入的壳")
    p.add_argument("--build", action="store_true", help="注入后构建导出 APK（需配合 --in）")
    args = p.parse_args()

    if args.check:
        return cmd_check(args.check)
    if args.print:
        return cmd_print()
    if args.src:
        return cmd_inject(args.src, args.build)
    sys.stderr.write("用法：--in <json> [--build] | --check <json> | --print\n")
    return 2


if __name__ == "__main__":
    sys.exit(main())
