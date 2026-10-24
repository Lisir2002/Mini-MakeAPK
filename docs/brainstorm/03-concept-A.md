# Concept A — 设备端 WebView 即时封装（MVP 候选）

**描述**：App 内置一个预置 Android 模板工程（WebView 宿主）。用户输入 URL 或粘贴 HTML，App 将其注入模板的 `assets`/远程加载，经本地打包流程生成可安装的 `.apk`（debug 签名）。

- **Pros**：零后端、零成本；可离线；最快装到手机演示；对 HTML/URL 用户完全够用；隐私好（代码不出设备）。
- **Cons**：无法真正编译 PHP/Vue（仅当静态资源或 URL 时可用）；生成的是 WebView 壳，不是"原生重写"；debug 签名无法上架商店。
- **预计工作量**：中（Compose UI + WebView 封装 + 本地打包脚本/Intent）。
- **适合作为 sprint-1 交付**。
