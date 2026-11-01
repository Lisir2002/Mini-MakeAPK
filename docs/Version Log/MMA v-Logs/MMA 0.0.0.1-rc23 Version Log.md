# MMA 0.0.0.1-rc23 Version Log

发布日期：2026-11-01
版本代号：DOM 结构取证（白屏分叉点裁定）

## 背景

rc22 三联证据实锤：小米登录页表面纯白（白色占比 95%、仅 2 种颜色）、页面缩放正常
（page_scale=3.0，缩放假设出局）、DOM 第一个文本是站点的 `<noscript>` 提示。
经拉取该站源码确认：登录页是 React SPA 空壳——`<noscript>提示</noscript>` +
空 `#root` 容器 + 全屏 `__page_loading` 白色遮罩，全部内容靠 React bundle 挂载。

关键分叉未定：探针测得 107 个 DOM 节点，而静态壳只有 ~8 个——React 到底挂没挂载？
全屏 loading 遮罩是不是卡在页面上没撤？

## 变更内容

### 升级

- **布局度量脚本 v2（DOM 结构取证）**：`preview_render_metrics` 的 `layout` 字段
  扩充为 14 段——readyState / visibilityState / 深色模式 / root 容器三级度量
  （子元素数/后代数/innerHTML 长度，React 是否挂载的铁证）/ loading 遮罩的
  display、rect 与 spinner 颜色（白色 spinner 叠白底 = 隐形遮罩现形）/ mask display /
  script 总数-外链数-module 数 / 前两个外链 src / body 各子树节点分布
  （107 个节点住在哪）/ root innerHTML 片段 / innerText 长度。

### 判读对照表（下一份日志使用）

| root 后代数 | 遮罩 display | 结论 |
|---|---|---|
| ~0 | block | React 未挂载 + 白遮罩卡住：脚本执行链路问题 |
| 大量（几十~百） | block | React 挂了但被遮罩盖住：遮罩撤销链路问题 |
| 大量 | none | 内容在 DOM 且无遮罩：纯样式/合成问题 |

## 兼容性

- 探针只读 + try/catch 兜底，无行为改动。
