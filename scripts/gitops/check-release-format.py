#!/usr/bin/env python3
"""MMA 发版格式校验（规约见 docs/RELEASE_NOTES.md）。

用法：
    python3 scripts/gitops/check-release-format.py --tag v0.0.0.1-rc1
    python3 scripts/gitops/check-release-format.py --latest

校验项：
    1. 标题格式（MMA {版本号} — {≤20字概括}，无 emoji）
    2. 正文无占位符、无 emoji、无内部术语关键词
    3. 条目格式（**4-9字小标题**：20-40字说明）
    4. 分类顺序（新功能 → 改进 → 移除 → 修复 → 安全 → 已知问题）
    5. 三处载体一致（Release 正文 = CHANGELOG 条目 = 独立版本文档）

退出码：0 = 通过；1 = 存在错误。
"""

import argparse
import re
import subprocess
import sys
from pathlib import Path

SOFTWARE = "MMA"
REPO_ROOT = Path(__file__).resolve().parent.parent.parent
CHANGELOG = REPO_ROOT / "docs" / "Version Log" / "CHANGELOG.md"
VLOGS_DIR = REPO_ROOT / "docs" / "Version Log" / "MMA v-Logs"

CATEGORIES = ["新功能", "改进", "移除", "修复", "安全", "已知问题"]
CATEGORY_INDEX = {name: i for i, name in enumerate(CATEGORIES)}

# 内部术语 / 元变更关键词（出现即违规，见 RELEASE_NOTES.md §5.9）
FORBIDDEN_KEYWORDS = [
    "文档", "docs/", "规范文档", "设计文档", "README", "AGENTS",
    "CHANGELOG", "workflow", "构建脚本", "versionCode", "versionName",
    "单元测试", "覆盖率", "重构", "依赖升级", "lint", "proguard",
    "R8", "commit", "gitops", "yml",
]
PLACEHOLDERS = ["请补充", "请提炼", "待补充", "TODO", "XXX", "占位"]

# emoji 及常见符号字形（⚠ ✅ ❌ 🎉 等）
EMOJI_RE = re.compile(
    "[\U0001F000-\U0001FAFF\U00002600-\U000027BF\U0001F1E6-\U0001F1FF"
    "\U00002B00-\U00002BFF\uFE0F\u200D]"
)
ENTRY_RE = re.compile(r"^- \*\*(.+?)\*\*：(.+)。$")


def run_git(args: list[str]) -> str:
    return subprocess.run(
        ["git", *args], cwd=REPO_ROOT, capture_output=True, text=True
    ).stdout.strip()


def gh_release(tag: str) -> dict:
    """通过 gh CLI 获取 Release 信息。"""
    proc = subprocess.run(
        ["gh", "release", "view", tag, "--json", "tagName,name,body,isPrerelease"],
        cwd=REPO_ROOT, capture_output=True, text=True,
    )
    if proc.returncode != 0:
        print(f"::error::无法获取 Release {tag}：{proc.stderr.strip()}")
        sys.exit(2)
    import json
    return json.loads(proc.stdout)


def latest_tag() -> str:
    tags = run_git(["tag", "--list", "v*", "--sort=-v:refname"]).splitlines()
    formal = [t for t in tags if "-rc" not in t]
    return formal[0] if formal else (tags[0] if tags else "")


def extract_changelog_entry(version: str) -> str:
    """从 CHANGELOG.md 提取 [version] 条目（去掉标题行）。version 不含 v 前缀。"""
    if not CHANGELOG.exists():
        return ""
    lines = CHANGELOG.read_text(encoding="utf-8").splitlines()
    out, in_section = [], False
    for line in lines:
        if line.startswith("## ["):
            in_section = line.startswith(f"## [{version}]")
            continue
        if in_section:
            out.append(line)
    return "\n".join(out).strip()


def normalize(text: str) -> list[str]:
    """归一化：去标题标记/引用标记/首尾空白，去空行——用于三处一致性比对。"""
    result = []
    for line in text.splitlines():
        line = re.sub(r"^#{1,6}\s+", "", line)
        line = re.sub(r"^>\s?", "", line)
        line = line.strip()
        if line:
            result.append(line)
    return result


def check_title(title: str, tag: str) -> list[str]:
    errors = []
    prefix = f"{SOFTWARE} {tag} — "
    if not title.startswith(prefix):
        errors.append(f"标题须为「{SOFTWARE} {tag} — {{概括}}」格式，实为：{title}")
        return errors
    summary = title[len(prefix):]
    if not (1 <= len(summary) <= 20):
        errors.append(f"标题概括须 1-20 字，实为 {len(summary)} 字：{summary}")
    if EMOJI_RE.search(title):
        errors.append("标题包含 emoji")
    return errors


def check_body(body: str) -> tuple[list[str], list[str]]:
    errors, warnings = [], []

    if EMOJI_RE.search(body):
        errors.append("正文包含 emoji")

    for ph in PLACEHOLDERS:
        if ph in body:
            errors.append(f"正文包含占位符：{ph}")

    for kw in FORBIDDEN_KEYWORDS:
        if kw in body:
            errors.append(f"正文包含内部术语/元变更关键词：{kw}")

    # 分类顺序
    seen = []
    for line in body.splitlines():
        m = re.match(r"^###\s+(.+?)\s*$", line)
        if m and m.group(1) in CATEGORY_INDEX:
            seen.append(CATEGORY_INDEX[m.group(1)])
    if seen != sorted(seen):
        errors.append(f"分类顺序错误（应为 {' → '.join(CATEGORIES)}）：实为 {seen}")

    # 条目格式
    for line in body.splitlines():
        if not line.startswith("- "):
            continue
        m = ENTRY_RE.match(line)
        if not m:
            errors.append(f"条目格式错误（须为 **4-9字小标题**：20-40字说明。）：{line}")
            continue
        title, desc = m.group(1), m.group(2)
        if not (4 <= len(title) <= 9):
            errors.append(f"小标题须 4-9 字，实为 {len(title)} 字：{title}")
        if not (20 <= len(desc) <= 40):
            errors.append(f"说明须 20-40 字，实为 {len(desc)} 字：{desc[:30]}…")
    return errors, warnings


def main() -> int:
    parser = argparse.ArgumentParser(description="MMA 发版格式校验")
    parser.add_argument("--tag", help="校验指定 tag 的 Release")
    parser.add_argument("--latest", action="store_true", help="校验最新正式版本")
    args = parser.parse_args()

    tag = args.tag or (latest_tag() if args.latest else "")
    if not tag:
        print("::error::请指定 --tag 或 --latest")
        return 1

    release = gh_release(tag)
    version = tag[1:] if tag.startswith("v") else tag
    title = release.get("name", "")
    body = (release.get("body", "") or "").strip()

    print(f"=== 发版格式校验: {tag} ===")
    print(f"标题: {title}")
    print(f"正文长度: {len(body)} 字符")
    print()

    errors: list[str] = []

    print("--- 标题校验 ---")
    title_errors = check_title(title, tag)
    errors.extend(title_errors)
    for e in title_errors:
        print(f"  x {e}")
    if not title_errors:
        print("  ok 标题格式正确")
    print()

    print("--- 正文校验 ---")
    body_errors, _ = check_body(body)
    errors.extend(body_errors)
    for e in body_errors:
        print(f"  x {e}")
    if not body_errors:
        print("  ok 正文格式正确")
    print()

    print("--- 三处一致性校验 ---")
    changelog_entry = extract_changelog_entry(version)
    if not changelog_entry:
        errors.append(f"CHANGELOG.md 中无 [{version}] 条目")
        print(f"  x CHANGELOG.md 中无 [{version}] 条目")
    else:
        norm_body = normalize(body)
        norm_changelog = normalize(changelog_entry)
        if norm_body != norm_changelog:
            errors.append("Release 正文与 CHANGELOG 条目不一致")
            print("  x Release 正文与 CHANGELOG 条目不一致")
        else:
            print("  ok Release 正文 = CHANGELOG 条目")

    vlog = VLOGS_DIR / f"{SOFTWARE} {version} Version Log.md"
    if not vlog.exists():
        errors.append(f"独立版本文档缺失：{vlog.relative_to(REPO_ROOT)}")
        print(f"  x 独立版本文档缺失：{vlog.relative_to(REPO_ROOT)}")
    elif changelog_entry:
        vlog_lines = vlog.read_text(encoding="utf-8").splitlines()
        if vlog_lines and vlog_lines[0].startswith("# "):
            vlog_lines = vlog_lines[1:]  # 去掉 H1 标题行（# MMA {版本} Version Log）
        norm_vlog = normalize("\n".join(vlog_lines))
        norm_changelog = normalize(changelog_entry)
        if norm_vlog != norm_changelog:
            errors.append("独立版本文档与 CHANGELOG 条目不一致")
            print("  x 独立版本文档与 CHANGELOG 条目不一致")
        else:
            print("  ok 独立版本文档 = CHANGELOG 条目")
    print()

    if errors:
        print(f"=== 校验失败：{len(errors)} 个错误 ===")
        return 1
    print("=== 校验通过 ===")
    return 0


if __name__ == "__main__":
    sys.exit(main())
