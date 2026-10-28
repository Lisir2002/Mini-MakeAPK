#!/usr/bin/env python3
"""由整幅图标素材生成 Android 启动图标资源（可复现）。

素材：一张方形整幅图（含吉祥物主体 + 底部字标），白底即可，无需透明通道。
产出：
  app/src/main/res/mipmap-{m,h,x,xx,xxx}dpi/ic_launcher_foreground.png  # 108dp 画布，adaptive 前景
  app/src/main/res/mipmap-{m,h,x,xx,xxx}dpi/ic_launcher.png             # 48dp 基准，API 24/25 回退
说明：
  * 启动图标只取「主体」（字标在 48dp 下不可读，且会被自适应遮罩裁掉），字标仅保留在商店大图；
  * 主体按 `--scale` 占画布宽度，默认 0.80——圆形 / 方形遮罩下均不裁切关键元素；
  * 素材近白底归一为纯白，与 @color/ic_launcher_background 无缝；
  * 传统图标烘焙 22% 圆角与白底，避免在 API 24/25 启动器上出现硬边。

用法：
  python3 scripts/design/build_launcher_icon.py \
      --source docs/design/assets/app-icon/app-icon-source-1254.webp

依赖：Pillow + numpy（CI 与本机均已具备；纯设计期工具，不参与打包）。
"""

from __future__ import annotations

import argparse
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
ADAPTIVE_DP = 108   # adaptive icon 画布
LEGACY_DP = 48      # 传统图标基准
WHITE = (255, 255, 255)
NEAR_WHITE = np.array([254, 254, 254])


def find_body_bottom(arr: np.ndarray, threshold: int = 20, min_y_ratio: float = 0.55) -> int:
    """定位主体（吉祥物 + 卡片）与底部字标的分界线。

    判据：自 55% 高度起，第一行「完全空白」的扫描线，且其上方内容够宽（主体）、
    下方内容成块（字标）。返回该空白行上一行作为主体下边界——主体的投影/光晕
    因此被完整保留，不会被切出一道硬边。
    """
    ink = np.abs(arr - NEAR_WHITE).max(axis=2) > threshold
    rows = ink.sum(axis=1)
    height, width = ink.shape
    for y in range(int(height * min_y_ratio), height):
        if rows[y] > 0:
            continue
        above, below = ink[:y], ink[y:]
        if above.sum() == 0 or below.sum() == 0:
            continue
        above_span = np.where(above.any(axis=0))[0]
        below_y, below_x = np.where(below)
        if len(above_span) < width * 0.5:
            continue
        if (below_x.max() - below_x.min()) < width * 0.35:
            continue
        if (below_y.max() - below_y.min()) < 30:
            continue
        return y - 1
    return height - 1


def load_mark(path: str, threshold: int = 20, pad: int = 10, body_bottom: int | None = None) -> Image.Image:
    """裁出主体区域（去掉底部字标），并把近白底归一为纯白。"""
    src = Image.open(path).convert("RGB")
    arr = np.asarray(src).astype(int)
    ink = np.abs(arr - NEAR_WHITE).max(axis=2) > threshold

    if body_bottom is None:
        body_bottom = find_body_bottom(arr)

    ys, xs = np.where(ink[: body_bottom + 1])
    if len(xs) == 0:
        raise SystemExit("素材中未检测到主体内容，请检查 --source")
    box = (
        max(0, xs.min() - pad),
        max(0, ys.min() - pad),
        min(src.width, xs.max() + pad),
        # 下边界不越过主体分界线，避免把字标顶部带进来
        min(src.height, ys.max() + pad, body_bottom + 1),
    )
    mark = src.crop(box)

    mark_arr = np.asarray(mark).astype(int)
    near = np.abs(mark_arr - NEAR_WHITE).max(axis=2) <= 8
    mark_arr[near] = WHITE
    return Image.fromarray(mark_arr.astype(np.uint8), "RGB")


def render(mark: Image.Image, size: int, scale: float, radius_ratio: float = 0.0) -> Image.Image:
    """白底（可选圆角）画布 + 居中主体；4x 超采样后降采样，保证边缘干净。"""
    ss = 4
    s = size * ss
    canvas = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    if radius_ratio > 0:
        draw.rounded_rectangle((0, 0, s - 1, s - 1), radius=int(s * radius_ratio), fill=WHITE + (255,))
    else:
        draw.rectangle((0, 0, s - 1, s - 1), fill=WHITE + (255,))
    w = int(round(s * scale))
    h = int(round(w * mark.height / mark.width))
    canvas.paste(mark.resize((w, h), Image.LANCZOS), ((s - w) // 2, (s - h) // 2))
    return canvas.resize((size, size), Image.LANCZOS)


def write_preview(mark: Image.Image, scale: float, radius_ratio: float, out: str) -> None:
    """输出效果预览：圆形遮罩 / 方形遮罩 / 48dp 实际尺寸。"""
    master = render(mark, 432, scale)
    circle = Image.new("L", (432, 432), 0)
    ImageDraw.Draw(circle).ellipse((0, 0, 431, 431), fill=255)
    squircle = Image.new("L", (432, 432), 0)
    ImageDraw.Draw(squircle).rounded_rectangle((0, 0, 431, 431), radius=int(432 * radius_ratio), fill=255)

    def masked(icon, mask):
        layer = Image.new("RGBA", icon.size, (0, 0, 0, 0))
        layer.paste(icon, (0, 0), mask)
        return layer

    tiles = [
        ("adaptive / circle", masked(master, circle), (58, 63, 74)),
        ("adaptive / squircle", masked(master, squircle), WHITE),
        ("legacy 48dp", render(mark, LEGACY_DP, scale, radius_ratio), WHITE),
    ]
    sheet = Image.new("RGB", (640, 360), (240, 242, 246))
    draw = ImageDraw.Draw(sheet)
    x = 20
    for label, icon, bg in tiles:
        tile = Image.new("RGB", (200, 200), bg)
        prev = icon.resize((192, 192), Image.LANCZOS)
        tile.paste(prev, (4, 4), prev)
        sheet.paste(tile, (x, 20))
        draw.text((x, 232), label, fill=(30, 30, 30))
        if icon.size[0] == LEGACY_DP:
            sheet.paste(icon.resize((96, 96), Image.NEAREST), (x, 248))
        x += 200
    sheet.save(out, optimize=True)


def main() -> int:
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    ap = argparse.ArgumentParser(description="生成 Android 启动图标（adaptive + legacy）")
    ap.add_argument("--source", default=os.path.join(
        root, "docs/design/assets/app-icon/app-icon-source-1254.webp"))
    ap.add_argument("--res", default=os.path.join(root, "app/src/main/res"))
    ap.add_argument("--scale", type=float, default=0.80, help="主体宽度占画布比例，默认 0.80")
    ap.add_argument("--radius", type=float, default=0.22, help="传统图标圆角比例，默认 0.22")
    ap.add_argument("--store-out", default=None, help="额外输出 512 商店整幅图（含字标）")
    ap.add_argument("--preview-out", default=None, help="额外输出效果预览图（遮罩 / 48dp）")
    ap.add_argument("--body-bottom", type=int, default=None,
                    help="手动指定主体下边界像素（自动检测不准时使用）")
    args = ap.parse_args()

    if not os.path.exists(args.source):
        raise SystemExit(f"素材不存在：{args.source}")
    mark = load_mark(args.source, body_bottom=args.body_bottom)
    print(f"主体裁切：{mark.width}x{mark.height}，缩放 {args.scale:.0%}（{args.source}）")

    for dpi, k in DENSITIES.items():
        out_dir = os.path.join(args.res, f"mipmap-{dpi}")
        os.makedirs(out_dir, exist_ok=True)
        render(mark, int(round(ADAPTIVE_DP * k)), args.scale).save(
            os.path.join(out_dir, "ic_launcher_foreground.png"), optimize=True)
        render(mark, int(round(LEGACY_DP * k)), args.scale, args.radius).save(
            os.path.join(out_dir, "ic_launcher.png"), optimize=True)
        print(f"  mipmap-{dpi}: foreground {int(round(ADAPTIVE_DP * k))}px / legacy {int(round(LEGACY_DP * k))}px")

    if args.store_out:
        Image.open(args.source).convert("RGB").resize((512, 512), Image.LANCZOS).save(
            args.store_out, optimize=True)
        print(f"商店整幅图：{args.store_out}")

    if args.preview_out:
        write_preview(mark, args.scale, args.radius, args.preview_out)
        print(f"效果预览：{args.preview_out}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
