# Android 客户端开发文档

本项目是一个基于 Kotlin 编写的现代 Android 客户端应用，其核心业务逻辑由 H5 承载。原生端提供了强大的 JSBridge 桥接能力、完整的 Firebase 服务集成以及深度的系统级交互支持。

## 1. 架构与技术栈

- **开发语言**: Kotlin
- **核心架构**: 基于类似于 MVVM 的流式架构。原生层不使用传统的 Handler 通信，而是采用 `Kotlin Coroutines`（协程）结合 `Flow`（数据流，如 `MutableSharedFlow`）来处理 JS 到 Native 跨线程的异步消息分发，从根本上降低了内存泄漏风险。
- **UI 结构**: 核心视图为一个承载主业务逻辑的 `WebView`，冷启动阶段会有另一个覆在其上方的开屏页 `WebView` 承载动画缓冲，之后渐隐销毁。

## 2. 源码包结构

代码主要位于 `com.ganha.test` 包下，模块划分如下：

- `.` (Root Package)
  - `MainActivity.kt`: 核心的宿主 Activity，负责初始化 WebView 引擎、注册生命周期与返回事件处理、拦截权限、挂载 JSBridge。
  - `SplashActivity.kt` / `MessageActivity.kt`: （视具体业务承载开屏、系统消息等二级页面）。
- `.bean`: 存放实体类（Data Class）。
  - `JsBean.kt`: **桥接核心枢纽**，定义了向 H5 暴露的 `@JavascriptInterface` 入口及路由常量配置。
  - `AppinfoBean.kt`, `ShareBean.kt`, `AppUpdateBean.kt` 等：用于 Gson 反序列化 H5 传入的 JSON 参数对象。
- `.viewmodel`: 
  - `MainViewModel.kt`: 接收从 WebView JSBridge 传来的 String 消息对象，并使用 `messageFlow` 进行解耦发射，交由 `MainActivity` 在生命周期协程域内消费与执行。
- `.utils`: 工具类集合。
  - `DeviceIdUtil.kt` & `DeviceInfoHelper.kt`: 获取设备唯一指纹、网络状态、SIM卡信息，并集成底层风险识别（检测模拟器、反编译/Root 环境、VPN/代理）。
  - `PermissionHelper.kt`: 封装 `XXPermissions` 的高可用动态权限申请工具库。
  - `WebViewLocalCache.kt`: WebView 本地资源拦截器，预加载离线缓存资源提升首屏速度。
  - `FlowEventBus.kt`: 基于 `SharedFlow` 实现的轻量级无锁事件总线。
  - `MyCustomTipsDialog.kt`: 通用自定义原生提示对话框实现。
- `.noticemessage` & `.inappmessaging`:
  - Firebase Cloud Messaging 及其应用内消息的接收、分发及相关 Worker 实现。

## 3. 核心功能与实现机制

### 3.1 JSBridge 桥接机制
- **H5 调用 Native**:
  `WebView.addJavascriptInterface(JsBean(viewModel), "App")` 注入了交互对象。所有的 H5 调用将被 Gson 解析为 `JsBeanRequest` 并发射到 ViewModel。`MainActivity` 的 `messageFlow.collect` 根据 `methods` 字段完成路由。
- **Native 回调 H5**:
  实现了安全的 `sendJsNative` 封装，内部基于 `org.json.JSONObject.quote` 转义参数，最终使用 `webView.evaluateJavascript` 在主线程派发事件给 H5 的 Callback。

### 3.2 WebView 高级定制
- **资源离线拦截**: 重写 `shouldInterceptRequest` 接入 `WebViewLocalCache` 库，从本地 Assets 替代网络加载固定框架的 CSS/JS/Font 资源。
- **深度意图拦截**: `shouldOverrideUrlLoading` 处理 `intent://` 协议，动态检测应用是否安装以进行唤醒分发或回退至 `browser_fallback_url` 触发下载。
- **输入法与文件选择**: 实现 `WebChromeClient.onShowFileChooser`，支持网页拉起原生系统文件选择器，结合 `Coroutine` 在 IO 线程进行大于 500KB 的大图智能递归压缩。
- **生命周期穿透**: 将 Activity 级 `onResume`/`onPause` 状态通过 `onAppLifecycle` 广播给 H5，用于控制后台轮播图与音视频释放。

### 3.3 图片与存储能力
- **长按抓图**: 通过 `WebView.HitTestResult` 嗅探长按事件，拦截 SRC 为图像的标签，唤起 `SaveImageDialog`。
- **公共目录适配**: 适配 Android 10+ 的 Scoped Storage（分区存储）。无论是网络图片下载还是 Base64 解码，最终通过 `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` 插入系统图库。

### 3.4 动态更新部署
- **文件下载器**: 集成系统底层 `DownloadManager` 结合 URLConnection 自实现流写入机制处理 APK 下载。
- **动态安装流**: 下载完成后自动唤起安装包解析。适配 Android 8.0+ 的 `REQUEST_INSTALL_PACKAGES` 权限动态授权，并针对 Android 14+ 规避后台静默拉起安装被拦截的限制。

### 3.5 平台分发与设备风控
- **社交隔离分享**: 提供了多维度的图文分享能力，能够指定拉起 (WhatsApp, Facebook, Instagram 等)。针对 Meta 系列对图文混合的支持限制，采用 **剪贴板暂存 + UI 提示延时** 的防封/兼容兜底策略。
- **渠道追踪**: 集成腾讯 VasDolly 渠道包解析（v2 签名块提取渠道号），以及系统剪贴板备用提取，用于精准识别归因来源。
- **设备风险阻断**: 暴露 `getDeviceRiskInfo` API 供 H5 上报风控指纹，涵盖 Root 嗅探、ADB 调试状态、SIM卡归属地等。

### 3.6 Firebase 基础设施
- **Remote Config**: 启动时异步拉取云控变量（如主网域名动态容灾），并使用 `ConfigUpdateListener` 监听实时变更。
- **Analytics**: 对系统底层权限状态（如 `push_permission_status`）进行多维度指标监控埋点。
- **FCM Push Notification**: 负责向系统注册 Token 换取标识，建立前台自定义 Notification Channel 弹窗。收到离线消息时透过 `FlowEventBus` 转接透传给 H5 执行应用内唤醒路由。
