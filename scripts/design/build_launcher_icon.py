#!/usr/bin/env python3
"""由整幅图标素材生成 Android 启动图标资源（可复现）。

素材：一张方形整幅图（含吉祥物主体 + 底部字标），白底即可，无需透明通道。
产出：
  app/src/main/res/mipmap-{m,h,x,xx,xxx}dpi/ic_launcher_foreground.png  # 108dp 画布，adaptive 前景
  app/src/main/res/mipmap-{m,h,x,xx,xxx}dpi/ic_launcher.png             # 48dp 基准，API 24/25 回退
说明：
  * 启动图标只取「主体」（字标在 48dp 下不可读，且会被自适应遮罩裁掉），字标仅保留在商店大图；
  * 自适应前景按 `--scale` 占画布宽度，默认 0.61——必须落在 108dp 画布的**安全区**内
    （可视区 72dp ≈ 66.7%，安全区 66dp ≈ 61%）。超过 66% 时圆形 / 方形遮罩会切掉光环与卡片边缘，
    表现为「图标被裁、不完整」（0.80 即为此前的事故值）；
  * 传统图标（API 24/25 无自适应遮罩）按 `--legacy-scale` 单独放大到 0.70，避免在启动器上显小；
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


def find_name_bottom(arr: np.ndarray, threshold: int = 20, gap_min: int = 8) -> int:
    """定位「MiniMe-MMA」名称块的下边界（排除其下的副标行，如「前端应用转 APK」）。

    自底向上：副标块（自最低墨行向上至第一个空白间隙）之下、再越过同一间隙，
    就是名称块；返回名称块最下一行。检测不出间隙则返回全图底（兜底不裁）。
    """
    ink = np.abs(arr - NEAR_WHITE).max(axis=2) > threshold
    rows = ink.sum(axis=1)
    height = len(rows)
    y = height - 1
    while y >= 0 and rows[y] == 0:
        y -= 1
    if y < 0:
        return height - 1
    # 穿过副标块
    while y >= 0 and rows[y] > 0:
        y -= 1
    # 越过副标与名称之间的空白间隙
    gap = 0
    while y >= 0 and rows[y] == 0:
        gap += 1
        y -= 1
    if gap < gap_min or y < 0:
        return height - 1
    # 此处 y 落在名称块的最下一行
    return y + 1


def load_mark(
    path: str,
    threshold: int = 20,
    pad: int = 10,
    body_bottom: int | None = None,
    include_wordmark: bool = True,
) -> Image.Image:
    """裁出图标主体并归一近白底为纯白。

    rc9 需求变更（用户决策）：**默认保留底部字标**——应用名「MiniMe-MMA」是象征性
    资产，裁掉等于切掉品牌（此前"48dp 下字标不可读"的设计决策被否，用户明确要求
    名字必须在图标里）。传 `include_wordmark=False` 才回到旧行为（只裁主体）。
    """
    src = Image.open(path).convert("RGB")
    arr = np.asarray(src).astype(int)
    ink = np.abs(arr - NEAR_WHITE).max(axis=2) > threshold

    if include_wordmark:
        # 只保留「MiniMe-MMA」名称行：副标（"前端应用转 APK"）行宽几乎等于全幅，
        # 圆形遮罩下两端必被切——砍掉副标，名称行窄、位置更高，遮罩内完整可见。
        name_bottom = find_name_bottom(arr)
        ys, xs = np.where(ink[: name_bottom + 1])
        bottom_limit = name_bottom + 1
    else:
        if body_bottom is None:
            body_bottom = find_body_bottom(arr)
        ys, xs = np.where(ink[: body_bottom + 1])
        bottom_limit = body_bottom + 1

    if len(xs) == 0:
        raise SystemExit("素材中未检测到主体内容，请检查 --source")
    box = (
        max(0, xs.min() - pad),
        max(0, ys.min() - pad),
        min(src.width, xs.max() + pad),
        # 下边界：含字标时到底；不含时不越过主体分界线，避免把字标顶部带进来
        min(src.height, ys.max() + pad, bottom_limit),
    )
    mark = src.crop(box)

    mark_arr = np.asarray(mark).astype(int)
    near = np.abs(mark_arr - NEAR_WHITE).max(axis=2) <= 8
    mark_arr[near] = WHITE
    return Image.fromarray(mark_arr.astype(np.uint8), "RGB")


def render(mark: Image.Image, size: int, scale: float, radius_ratio: float = 0.0) -> Image.Image:
    """白底（可选圆角）画布 + 居中主体；4x 超采样后降采样，保证边缘干净。

    含字标后素材比例接近全幅（高≥宽），故按 **max(宽,高)** 适配：[scale] 语义是
    "较长边占画布比例"，保证任意素材比例都不越界（宽度换算在含字标时会越界）。
    """
    ss = 4
    s = size * ss
    canvas = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    if radius_ratio > 0:
        draw.rounded_rectangle((0, 0, s - 1, s - 1), radius=int(s * radius_ratio), fill=WHITE + (255,))
    else:
        draw.rectangle((0, 0, s - 1, s - 1), fill=WHITE + (255,))
    longest = max(mark.width, mark.height)
    w = int(round(s * scale * mark.width / longest))
    h = int(round(s * scale * mark.height / longest))
    canvas.paste(mark.resize((w, h), Image.LANCZOS), ((s - w) // 2, (s - h) // 2))
    return canvas.resize((size, size), Image.LANCZOS)


def write_preview(
    mark: Image.Image,
    scale: float,
    legacy_scale: float,
    radius_ratio: float,
    out: str,
) -> None:
    """输出效果预览：圆形遮罩 / 方形遮罩 / 48dp 实际尺寸。

    自适应预览按**真实可视区**取景：系统只显示 108dp 画布中央 72dp 视窗再套遮罩，
    故先裁中央 72dp 再套 circle / squircle——用整幅 108dp 圆预览会让内容「显得过小」，误判缩放。
    """
    canvas = 432                    # 108dp @4x
    view = canvas * 72 // 108       # 72dp 可视视窗 @4x = 288
    master = render(mark, canvas, scale)
    off = (canvas - view) // 2
    viewbox = master.crop((off, off, off + view, off + view))

    circle = Image.new("L", (view, view), 0)
    ImageDraw.Draw(circle).ellipse((0, 0, view - 1, view - 1), fill=255)
    squircle = Image.new("L", (view, view), 0)
    ImageDraw.Draw(squircle).rounded_rectangle(
        (0, 0, view - 1, view - 1), radius=int(view * radius_ratio), fill=255)

    def masked(icon, mask):
        layer = Image.new("RGBA", icon.size, (0, 0, 0, 0))
        layer.paste(icon, (0, 0), mask)
        return layer

    tiles = [
        ("adaptive / circle", masked(viewbox, circle), (58, 63, 74)),
        ("adaptive / squircle", masked(viewbox, squircle), WHITE),
        ("legacy 48dp", render(mark, LEGACY_DP, legacy_scale, radius_ratio), WHITE),
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
    ap.add_argument("--scale", type=float, default=0.55,
                    help="自适应前景较长边占画布比例，默认 0.55（含字标素材，留足遮罩边距）")
    ap.add_argument("--legacy-scale", type=float, default=0.64,
                    help="传统图标较长边占画布比例，默认 0.64（含字标素材，48dp 下名称小但可辨）")
    ap.add_argument("--radius", type=float, default=0.22, help="传统图标圆角比例，默认 0.22")
    ap.add_argument("--store-out", default=None, help="额外输出 512 商店整幅图（含字标）")
    ap.add_argument("--preview-out", default=None, help="额外输出效果预览图（遮罩 / 48dp）")
    ap.add_argument("--body-bottom", type=int, default=None,
                    help="手动指定主体下边界像素（自动检测不准时使用）")
    ap.add_argument("--no-wordmark", action="store_true",
                    help="排除底部字标（rc9 起默认**含字标**，用户要求品牌名必须在图标内）")
    args = ap.parse_args()

    if not os.path.exists(args.source):
        raise SystemExit(f"素材不存在：{args.source}")
    mark = load_mark(args.source, body_bottom=args.body_bottom, include_wordmark=not args.no_wordmark)
    mode = "含字标" if not args.no_wordmark else "仅主体"
    print(f"主体裁切：{mark.width}x{mark.height}（{mode}），自适应 {args.scale:.0%} / 传统 {args.legacy_scale:.0%}"
          f"（{args.source}）")

    for dpi, k in DENSITIES.items():
        out_dir = os.path.join(args.res, f"mipmap-{dpi}")
        os.makedirs(out_dir, exist_ok=True)
        render(mark, int(round(ADAPTIVE_DP * k)), args.scale).save(
            os.path.join(out_dir, "ic_launcher_foreground.png"), optimize=True)
        render(mark, int(round(LEGACY_DP * k)), args.legacy_scale, args.radius).save(
            os.path.join(out_dir, "ic_launcher.png"), optimize=True)
        print(f"  mipmap-{dpi}: foreground {int(round(ADAPTIVE_DP * k))}px / legacy {int(round(LEGACY_DP * k))}px")

    if args.store_out:
        Image.open(args.source).convert("RGB").resize((512, 512), Image.LANCZOS).save(
            args.store_out, optimize=True)
        print(f"商店整幅图：{args.store_out}")

    if args.preview_out:
        write_preview(mark, args.scale, args.legacy_scale, args.radius, args.preview_out)
        print(f"效果预览：{args.preview_out}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
