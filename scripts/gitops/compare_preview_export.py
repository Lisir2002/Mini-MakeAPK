#!/usr/bin/env python3
"""预览 vs 导出 像素对比（方向 1 一致性可视化证据）。

读取 app 与 template 两模块各自 Paparazzi 快照：
  - app/src/test/snapshots/images/ShellHostExportParityTest_previewSideShell_light.png   (预览侧)
  - template/src/test/snapshots/images/TemplateShellSnapshotTest_exportedShell_light.png  (导出侧)

产出：
  - scripts/gitops/preview_vs_export_comparison.png  （左=预览 / 中=导出 / 右=差异热区 三栏并排）
  - 控制台打印：尺寸、是否同尺寸、平均/最大逐像素差（0~255/通道）

退出码：
  0 = 一切就绪且像素平均差 ≤ 阈值（同源渲染预期远低于此）；
  1 = 像素差超出阈值或尺寸不符；
  2 = 文件缺失 / 缺少 Pillow / 环境错误。
"""
import sys
from pathlib import Path

try:
    from PIL import Image, ImageChops, ImageDraw
except ImportError:
    sys.stderr.write("::error::缺少 Pillow，请先 `pip install pillow`\n")
    sys.exit(2)

REPO = Path(__file__).resolve().parent.parent.parent
PREVIEW = REPO / "app/src/test/snapshots/images/com.make.apk.ui.shell_ShellHostExportParityTest_previewSideShell_light.png"
EXPORT = REPO / "template/src/test/snapshots/images/com.make.apk_TemplateShellSnapshotTest_exportedShell_light.png"
OUT = REPO / "scripts/gitops/preview_vs_export_comparison.png"

# 每通道平均差阈值（0~255）；同源 ShellHost + 同源 ShellSpec 预期远低于此。
THRESHOLD_MEAN = 2.0


def main() -> int:
    for name, p in (("预览", PREVIEW), ("导出", EXPORT)):
        if not p.is_file():
            sys.stderr.write(f"::error::缺失快照：{p}\n")
            return 2

    a = Image.open(PREVIEW).convert("RGB")
    b = Image.open(EXPORT).convert("RGB")

    if a.size != b.size:
        sys.stderr.write(f"::error::尺寸不一致 预览{a.size} vs 导出{b.size}\n")
        produce_side_by_side(a, b, None)
        return 1

    diff = ImageChops.difference(a, b)
    # 各通道真实极值（(min,max) 三元组），直接给出最大逐像素差，避免直方图遍历误报。
    extrema = diff.getextrema()
    max_diff = max(e[1] for e in extrema)
    total_px = a.size[0] * a.size[1]
    # 平均差：逐像素逐通道绝对差之和 / (像素数 × 通道数)。
    hist = diff.histogram()
    sum_abs = 0
    for ch in range(3):
        for val, count in enumerate(hist[ch * 256:(ch + 1) * 256]):
            sum_abs += val * count
    mean_diff = sum_abs / (total_px * 3)

    produce_side_by_side(a, b, diff)

    print("=== 预览 vs 导出 像素对比 ===")
    print(f"尺寸: {a.size}  (一致)")
    print(f"平均逐像素差: {mean_diff:.4f} / 255 (每通道)")
    print(f"最大逐像素差: {max_diff} / 255")
    print(f"对比图: {OUT}")
    if mean_diff > THRESHOLD_MEAN:
        sys.stderr.write(f"::error::像素差 {mean_diff:.4f} 超出阈值 {THRESHOLD_MEAN}\n")
        return 1
    print("=== 通过：预览侧与导出侧像素一致（同源 ShellHost + 同源 ShellSpec） ===")
    return 0


def produce_side_by_side(a: Image.Image, b: Image.Image, diff_img) -> None:
    """三栏并排：预览 | 导出 | 差异；差异为 None 时以灰块占位。"""
    w, h = a.size
    pad = 16
    label_h = 28
    col_gap = pad
    canvas_w = w * 3 + col_gap * 2 + pad * 2
    canvas_h = h + pad * 2 + label_h
    canvas = Image.new("RGB", (canvas_w, canvas_h), (244, 244, 244))

    canvas.paste(a, (pad, pad + label_h))
    canvas.paste(b, (pad + w + col_gap, pad + label_h))
    if diff_img is not None:
        canvas.paste(diff_img, (pad + (w + col_gap) * 2, pad + label_h))
    else:
        canvas.paste(Image.new("RGB", (w, h), (200, 200, 200)),
                     (pad + (w + col_gap) * 2, pad + label_h))

    d = ImageDraw.Draw(canvas)
    d.text((pad, pad // 2), "预览 (app / ShellHostExportParityTest)", fill=(0, 0, 0))
    d.text((pad + w + col_gap, pad // 2), "导出 (template / TemplateShellSnapshotTest)", fill=(0, 0, 0))
    d.text((pad + (w + col_gap) * 2, pad // 2), "差异 (黑=一致)", fill=(0, 0, 0))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(OUT)


if __name__ == "__main__":
    sys.exit(main())
