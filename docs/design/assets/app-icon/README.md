# 启动图标资产（Launcher Icon）

MiniMe-MMA 的应用启动图标资产与生成流水线。所有图标均由此目录的素材
`app-icon-source-1254.webp` 经 `scripts/design/build_launcher_icon.py` 生成，**不要手改产物**。

## 素材说明

| 文件 | 用途 |
|------|------|
| `app-icon-source-1254.webp` | 设计方提供的整幅图标（吉祥物 + 网页卡 → APK 卡 + 光环 + 底部字标），1254×1254，白底 |
| `app-icon-512.png` | 商店整幅图（保留字标，用于 Play 商店 / 分发页大图） |
| `preview.png` | 本地预览：圆形遮罩 / 方形遮罩 / 48dp 实际尺寸 |

> 字标 `"MiniMe-MMA  /  前端应用转 APK"` 仅出现在整幅素材与商店大图中；
> **启动图标不含字标**——48dp 下字标不可读，且自适应遮罩会裁掉它，保留只会显脏。

## 启动图标（落地到 `app/src/main/res/mipmap-*`）

- **自适应前景** `ic_launcher_foreground.png`：108dp 画布，主体宽占 **61%**（落在 66dp 安全区内；
  系统只显示画布中央 72dp 视窗再套遮罩，超 66% 会被圆形/方形遮罩裁边），白底与
  `@color/ic_launcher_background`（`#FFFFFF`）无缝拼接。
- **传统回退** `ic_launcher.png`：48dp 基准，白底 22% 圆角方，同款主体按 70% 放大（API 24/25 用，
  无自适应遮罩可适当放大）。
- 已删除旧的矢量珊瑚粉图标（`ic_launcher_background.xml` / `ic_launcher_foreground.xml`
  与 `mipmap-anydpi/ic_launcher.xml`），统一改为位图，避免 API 24/25 继续用旧矢量覆盖新位图。

### 关于 monochrome（Android 13 主题图标）

**不提供 `<monochrome>` 层。** 素材是多彩位图，按深色阈值取剪影会把卡片投影一并带进来，
形成脏块；宁可不支持主题图标，也不交付劣质剪影。主题图标（Pixel 等）将回退为彩色图标。

## 重新生成

```bash
python3 scripts/design/build_launcher_icon.py \
  --source docs/design/assets/app-icon/app-icon-source-1254.webp \
  --store-out docs/design/assets/app-icon/app-icon-512.png \
  --preview-out docs/design/assets/app-icon/preview.png
```

参数：

- `--scale`：自适应前景主体宽度占画布比例，默认 `0.61`（66dp 安全区；>0.66 会被遮罩裁边）。
- `--legacy-scale`：传统图标主体宽度占画布比例，默认 `0.70`（无自适应遮罩，可适当放大）。
- `--radius`：传统图标圆角比例，默认 `0.22`。
- `--body-bottom`：手动指定主体下边界像素，自动检测不准时兜底。

## 变更记录

| 日期 | 变更 |
|------|------|
| 2026-10-28 | 修复图标被遮罩裁边：自适应前景 0.80 → 0.61（66dp 安全区内），传统图标单独 0.70；预览改为按真实 72dp 视窗取景 |
| 2026-10-28 | 由珊瑚粉矢量图标更换为设计方提供的彩色位图图标（吉祥物 + 网页卡 → APK 卡 + 光环） |
