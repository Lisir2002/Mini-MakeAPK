#!/usr/bin/env python3
"""MMA 设计令牌门禁（规约见 docs/ARCHITECTURE.md 设计令牌链路）。

用法：
    python3 scripts/gitops/check_token_usage.py            # 报告模式（CI 默认，不拦构建）
    python3 scripts/gitops/check_token_usage.py --strict   # 严格模式（有违规即失败）

令牌链路（唯一事实来源）：
    DESIGN_LANGUAGE.md → ui/theme/{Color,MmaDesign,Motion,Theme}.kt → MaterialTheme 引用

检查项（范围：见下方 `SCAN_ROOTS` —— 含 app 的 ui/ 包与 template 模块自有源码根，均排除 theme/ 包）：
    1. 颜色字面量     Color(0x…) / Material 命名色直用      → 应走 colorScheme 令牌
    2. 圆角字面量     RoundedCornerShape(…)                  → 应走 shapes 令牌
    3. 间距字面量     N.dp ∉ 允许集                          → 应走 Spacing 阶梯
    4. 字号字面量     N.sp                                    → 应走 typography 令牌

豁免机制：
    - theme/ 包为令牌定义处，天然豁免；
    - 行内注释 `// token-gate: allow` 逐行豁免（须写理由，见 CODE_STYLE.md）；
    - 允许集之外的 dp 值新增，须先评估入阶梯 or 加白名单并注明来源。

退出码：0 = 通过（或无违规的报告模式）；1 = --strict 且存在违规；2 = 环境错误。
"""

import argparse
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
THEME_DIRNAME = "theme"
EXEMPT_MARK = "token-gate: allow"

# 扫描根（相对 REPO_ROOT）：
# - app 的 ui/ 包（壳/页面源码，排除 theme/ 令牌定义处）；
# - template 模块自有源码根（ui/shell 经 sourceSets 共享 app 物理文件、不会在此出现，
#   此处只覆盖模板自有文件如 TemplateActivity.kt，借此消除模板侧覆盖缺口）。
SCAN_ROOTS = [
    REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "make" / "apk" / "ui",
    REPO_ROOT / "template" / "src" / "main" / "java" / "com" / "make" / "apk",
]

# ── 允许集（带来源审批记录；新增值必须注明理由） ──────────────────────────
# Spacing 阶梯（theme/MmaDesign.kt SpacingScale，DESIGN_LANGUAGE §4 紧凑档）
SPACING_LADDER = {3, 6, 9, 12, 18, 24, 36}
# 组件规范尺寸（非间距语义，均为 M3 / 图标规范值）
COMPONENT_SIZES = {
    0,    # 无间距
    1,    # 磨砂容器描边宽度（MmaTranslucentSurface / DESIGN_LANGUAGE §7）
    2,    # 准星竖线宽度（ProjectTypeSelector / project-editor-design.md §9.1）
    24,   # 图标默认尺寸（M3 Icon / MmaIcons.size 默认）
    32,   # 权限弹窗圆底（64dp）内图标（PermissionSpec 视觉规格）
    36,   # 项目页空态图标（ProjectsScreen）
    40,   # 项目列表行图标（ProjectsScreen / ProjectIcon）
    48,   # 「我的」页信息卡图标（ProfileScreen）
    56,   # 顶栏规范高度（M3 TopAppBar，MmaTopBar.heightIn 下限）
          #   兼 ProjectIcon 默认边长（新建项目页身份区，§4.1）
    64,   # 结果页图标容器（ResultScreen）/ 权限弹窗图标底 / 开发中页图标
    72,   # 构建脉冲图标容器（BuildingPulse）
}
ALLOWED_DP = SPACING_LADDER | COMPONENT_SIZES

# Material 命名颜色常量直用（androidx.compose.ui.graphics.Color 伴生对象）
# 注意：Color.Transparent 不在拦截名单——「完全透明」是通用工具色（磨砂容器
# 交还视觉给 hazeChild tint 等场景必需），不属于设计令牌管辖的表面色决策。
COLOR_NAMED_RE = re.compile(
    r"\bColor\.(?:Red|Blue|Green|Yellow|Black|White|Gray|Cyan|Magenta|"
    r"LightGray|DarkGray)\b"
)
COLOR_HEX_RE = re.compile(r"\bColor\s*\(\s*0x")
ROUNDED_RE = re.compile(r"\bRoundedCornerShape\s*\(")
DP_RE = re.compile(r"(?<![\w.])(\d+(?:\.\d+)?)\s*\.dp\b")
SP_RE = re.compile(r"(?<![\w.])(\d+(?:\.\d+)?)\s*\.sp\b")
LINE_COMMENT_RE = re.compile(r"//.*$")


def iter_kotlin_files():
    """各扫描根下全部 Kotlin 文件，跳过 theme/ 包（令牌定义处）。"""
    for root in SCAN_ROOTS:
        if not root.is_dir():
            continue
        for path in sorted(root.rglob("*.kt")):
            if THEME_DIRNAME in path.relative_to(root).parts:
                continue
            yield path


def strip_comment(line: str) -> str:
    """剥离行注释，避免注释里的示例值误报（字符串内 // 极端罕见，可接受）。"""
    return LINE_COMMENT_RE.sub("", line)


def check_file(path: Path) -> list[tuple[int, str, str]]:
    """返回 [(行号, 规则, 原文)] 违规列表。"""
    violations = []
    for lineno, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if EXEMPT_MARK in raw:
            continue
        code = strip_comment(raw)
        rel = path.relative_to(REPO_ROOT)

        if COLOR_HEX_RE.search(code) or COLOR_NAMED_RE.search(code):
            violations.append((lineno, "颜色字面量", raw.strip()))
        if ROUNDED_RE.search(code):
            violations.append((lineno, "圆角字面量", raw.strip()))
        # 圆角参数内的 dp（如 RoundedCornerShape(16.dp)）已由圆角规则覆盖，不重复报间距
        rounded_spans = [m.span() for m in ROUNDED_RE.finditer(code)]
        for m in DP_RE.finditer(code):
            if any(s <= m.start() < e for s, e in rounded_spans):
                continue
            value = float(m.group(1))
            if value not in ALLOWED_DP:
                violations.append((lineno, f"间距字面量 {m.group(1)}.dp", raw.strip()))
        if SP_RE.search(code):
            violations.append((lineno, "字号字面量", raw.strip()))
    return violations


def main() -> int:
    parser = argparse.ArgumentParser(description="MMA 设计令牌门禁")
    parser.add_argument("--strict", action="store_true", help="有违规即失败（默认报告模式）")
    args = parser.parse_args()

    print("=== 设计令牌门禁 ===")
    for root in SCAN_ROOTS:
        rel = root.relative_to(REPO_ROOT)
        mark = "✓" if root.is_dir() else "✗(目录不存在,跳过)"
        print(f"范围: {rel}（排除 {THEME_DIRNAME}/ 包） {mark}")
    print(f"模式: {'严格（违规即失败）' if args.strict else '报告（仅打印，不拦构建）'}")
    print(f"间距允许集: {sorted(ALLOWED_DP)}")
    print()

    total = 0
    for path in iter_kotlin_files():
        for lineno, rule, snippet in check_file(path):
            total += 1
            rel = path.relative_to(REPO_ROOT)
            print(f"  x {rel}:{lineno} [{rule}] {snippet}")
            print(f"    ::error file={rel},line={lineno}::{rule}，应改用设计令牌"
                  f"（见 docs/ARCHITECTURE.md 令牌链路）")

    print()
    if total == 0:
        print("=== 校验通过：无令牌违规 ===")
        return 0
    print(f"=== 发现 {total} 处违规 ===")
    if args.strict:
        print("严格模式：修复违规，或加行内豁免 `// token-gate: allow <理由>`。")
        return 1
    print("报告模式：不拦构建。请按令牌链路修复（DESIGN_LANGUAGE → theme → 页面）。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
