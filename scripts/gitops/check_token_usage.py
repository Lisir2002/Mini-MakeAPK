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
    5. 废弃色黑名单   RETIRED_COLORS 出现在任何 .kt           → 一次一除，禁止回潮
       （黑名单扫描**全仓库** Kotlin 源码：主源码 + 测试。存在原因——2026-10 设计重构把
        主色珊瑚粉换蓝时，裸 ARGB 字面量 `0xFFE39E97.toInt()` 不匹配 Color(0x…) 正则、
        且 data/model 层不在令牌扫描范围，旧默认主题色漏进数据层默认值 / 主题色板 / 测试
        固定值三处。黑名单 + 全仓扫描是兜底铁闸，逐色登记、永不摘除。）

豁免机制：
    - theme/ 包为令牌定义处，天然豁免（仅对检查项 1-4；黑名单对 theme/ 同样生效）；
    - 行内注释 `// token-gate: allow` 逐行豁免检查项 1-4（须写理由，见 CODE_STYLE.md）；
    - 行内注释 `// legacy-color <理由>` 逐行豁免**仅检查项 5**（专用于模拟历史数据的
      迁移测试，如旧行的珊瑚粉 themeColor；主源码禁止使用该豁免）；
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

# 废弃色黑名单扫描根：全仓库 Kotlin 源码（主源码 + 测试）。
# 不排除 theme/——令牌定义处出现废弃色同样违规；data/ 层、ViewModel 默认值是
# 本次事故的漏网层（裸 ARGB 字面量不经过 Color(0x…)），必须一并罩住。
BLACKLIST_ROOTS = [
    REPO_ROOT / "app" / "src",
    REPO_ROOT / "template" / "src",
]

# ── 废弃色黑名单（一次一除，永不摘除；新退役颜色继续往下登记） ────────────
# 2026-10 UI 重构（黑白为主 + 蓝点缀）退役的旧珊瑚粉系配色。
RETIRED_COLORS = {
    "E39E97": "旧默认主题色珊瑚粉 #E39E97（新默认 #2563EB 蓝，见 ProjectDefaults.DEFAULT_THEME_COLOR）",
    "F19E97": "旧主色珊瑚粉 #F19E97（新主色 #2563EB 蓝，见 theme/Color.kt）",
    "F2B8A2": "旧主题色板·蜜桃",
    "9FD8C7": "旧主题色板·薄荷",
    "9DB8E8": "旧主题色板·晴空",
    "C9A9E9": "旧主题色板·薰衣草",
}
# 同时命中带 alpha 前缀（0xFFE39E97）与不带前缀（0xE39E97）两种写法。
RETIRED_COLOR_RE = re.compile(
    r"\b0x[0-9A-Fa-f]{0,2}(" + "|".join(RETIRED_COLORS) + r")\b",
    re.IGNORECASE,
)
LEGACY_MARK = "legacy-color"   # 行内豁免标记（仅黑名单规则；主源码禁用）

# ── 允许集（带来源审批记录；新增值必须注明理由） ──────────────────────────
# Spacing 阶梯（theme/MmaDesign.kt SpacingScale，DESIGN_LANGUAGE §4 紧凑档）
SPACING_LADDER = {3, 6, 9, 12, 18, 24, 36}
# 组件规范尺寸（非间距语义，均为 M3 / 图标规范值）
COMPONENT_SIZES = {
    0,    # 无间距
    1,    # 磨砂容器描边宽度（MmaTranslucentSurface / DESIGN_LANGUAGE §7）
    2,    # 准星竖线宽度（ProjectTypeSelector / project-editor-design.md §9.1）
    16,   # 圆角组件尺寸 radius-lg（design-tokens.json radius.lg=16；RoundedCornerShape 内 dp 已由圆角规则豁免，此处覆盖独立 .dp 用途）
    20,   # 圆角组件尺寸 radius-xl（design-tokens.json radius.xl=20；bottomSheet 顶部圆角，经门禁白名单）
    24,   # 图标默认尺寸（M3 Icon / MmaIcons.size 默认）
    32,   # 权限弹窗圆底（64dp）内图标（PermissionSpec 视觉规格）
    36,   # 项目页空态图标（ProjectsScreen）
    40,   # 项目列表行图标（ProjectsScreen / ProjectIcon）
    48,   # 「我的」页信息卡图标（ProfileScreen）
    52,   # 顶栏规范高度（design-tokens.json chrome.top-bar-height=52；MmaTopBar.heightIn 下限经 LocalMmaDesign.chrome.topBarHeight）
    56,   # 底栏规范高度（design-tokens.json chrome.bottom-bar-height=56，覆盖 M3 NavigationBar 默认 80；兼 ProjectIcon 默认边长 §4.1）
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


def iter_blacklist_files():
    """黑名单扫描根下全部 Kotlin 文件（主源码 + 测试，theme/ 不豁免）。"""
    for root in BLACKLIST_ROOTS:
        if not root.is_dir():
            continue
        yield from sorted(root.rglob("*.kt"))


def check_retired_colors(path: Path) -> list[tuple[int, str, str]]:
    """废弃色黑名单检查（返回 [(行号, 命中说明, 原文)]）。

    行内 `// legacy-color <理由>` 豁免（专用于模拟历史数据的迁移测试）；
    注释文本中的废弃色（如"旧默认珊瑚粉 #E39E97 已废弃"）不拦。
    """
    violations = []
    for lineno, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if LEGACY_MARK in raw:
            continue
        code = strip_comment(raw)
        m = RETIRED_COLOR_RE.search(code)
        if m:
            key = m.group(1).upper()
            violations.append((lineno, f"废弃色 {key}（{RETIRED_COLORS[key]}）", raw.strip()))
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

    for path in iter_blacklist_files():
        for lineno, rule, snippet in check_retired_colors(path):
            total += 1
            rel = path.relative_to(REPO_ROOT)
            print(f"  x {rel}:{lineno} [废弃色黑名单] {snippet}")
            print(f"    ::error file={rel},line={lineno}::{rule}，禁止回潮"
                  f"（确需模拟历史数据须加行内豁免 // legacy-color <理由>，主源码禁用该豁免）")

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
