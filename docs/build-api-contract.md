# 云端构建 API 契约（Phase 2 规格）

> 用途：当输入为 Vue 工程 / PHP 项目 / 需要"真构建"的复杂前端时，App 将源码上传到云端构建服务，服务在沙箱中编译并产出签名 APK。本 sprint 仅定义契约，实现留待 Phase 2。
> 安全：用户代码须沙箱隔离；签名密钥存于服务端 KMS，绝不落客户端。

## 流程

```
App ──POST /build──► Build Service ──(沙箱编译: npm build / php / gradle)──► APK
  ◄──202 {buildId}──┘                                                     │
  ──GET /build/{id}──► 轮询状态 (queued→building→ready→failed)            │
  ──GET /apk/{id}──► 下载 APK ────────────────────────────────────────────┘
```

## 1. 提交构建

`POST /build`

```json
{
  "sourceType": "url | html | vue_zip | php_zip",
  "payload": {
    "url": "https://example.com",
    "html": "<html>...</html>",
    "fileRef": "uploads/<uuid>.zip"
  },
  "options": {
    "appName": "我的应用",
    "packageId": "com.user.myapp",
    "minSdk": 24
  }
}
```

响应 `202 Accepted`：
```json
{ "buildId": "b_8f3a", "status": "queued", "pollUrl": "/build/b_8f3a" }
```

## 2. 轮询状态

`GET /build/{id}`

```json
{ "buildId": "b_8f3a", "status": "building", "progress": 0.42, "step": "gradle_assemble" }
```
状态机：`queued → building → ready | failed`。`failed` 须带 `error` 字段。

## 3. 下载 APK

`GET /apk/{id}` → `application/vnd.android.package-archive`（带 `Content-Disposition: attachment; filename="myapp.apk"`）。

## 错误码
- `400` 输入非法（未知 sourceType / 空 payload）
- `413` 上传超限
- `422` 构建失败（返回 `error` 详情，App 转译为友好文案）
- `429` 限流（免费额度用尽）

## App 侧处理
- 提交后进入 `BuildingPulse` 可爱进度（非转圈）。
- `ready` → 触发成果庆祝动效 → 引导安装（Intent `ACTION_INSTALL_PACKAGE`）。
- `failed` → 珊瑚粉错误提示 + 重试，避免技术日志外泄。
