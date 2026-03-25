# JSBridge 接口说明文档

本文档描述了 H5 页面如何与 Android Native 客户端进行交互的 JSBridge 接口。

## 1. 基础调用方式

H5 页面通过调用注入到 `window` 对象中的 `App.JSToNative(jsonString)` 方法向原生发送消息。

请求数据的标准 JSON 格式如下：

```javascript
{
  "methods": "方法名",           // 必需：要调用的原生方法名
  "callback": "回调函数名",     // 可选：原生执行完毕后需要调用的全局作用域下的 JS 回调函数名
  "paramObj": {                  // 可选：传递给原生的参数对象，有些接口可能直接接收字符串或基本类型
     // ... 具体的参数字段
  }
}
```

示例封装函数：

```javascript
function callNative(method, paramObj, callbackName) {
    const request = {
        methods: method,
        callback: callbackName || `${method}_callback`,
        paramObj: paramObj
    };

    const jsonStr = JSON.stringify(request);
    if (window.App && window.App.JSToNative) {
        // 如果后端使用 Gson 把 paramObj 解析为 String 而非 Object，如果报错可尝试传 JSON.stringify(paramObj)
        window.App.JSToNative(jsonStr);
    } else {
        console.error("Native桥接方法不存在");
    }
}
```

---

## 2. 接口列表与功能说明

### 2.1 修改状态栏文字颜色
* **接口名 (`methods`)**: `statusBarLight`
* **功能**: 修改系统状态栏的文字/图标颜色（深色/浅色）。
* **参数 (`paramObj`)**: 
  * `isLight` (Boolean): `true` 为浅色背景（状态栏文字为深色），`false` 为深色背景（状态栏文字为浅色）。
  * `color` (String): 状态栏背景颜色，如 `#FFFFFF`。
* **回调**: 无

### 2.2 保存图片到相册
* **接口名 (`methods`)**: `saveImageToGallery`
* **功能**: 将网络图片或 Base64 格式的图片保存到设备相册。
* **参数 (`paramObj`)**: 
  * `url` (String): 图片的网络地址或 Base64 字符串。
* **回调**: 无（原生端会自动处理权限申请和成功/失败提示）。
> **注**: WebView 默认拦截了长按图片动作，会自动弹窗提示保存，H5 无需额外处理长按逻辑。

### 2.3 获取设备信息
* **接口名 (`methods`)**: `deviceInfo`
* **功能**: 获取设备的详细信息（是否模拟器、VPN、Root、国家代码、应用版本号等）。
* **参数 (`paramObj`)**: 无
* **回调数据 (`deviceInfo_callback`)**: JSON 字符串，包含设备指纹、安全环境和网络信息。

### 2.4 获取已安装的社交软件列表
* **接口名 (`methods`)**: `installedSocialApps`
* **功能**: 获取本机是否安装了指定的几款社交软件。
* **参数 (`paramObj`)**: 无
* **回调数据 (`installedSocialApps_callback`)**: JSON 字符串，表示安装状态：
  ```json
  {
    "whatsapp": true,
    "tiktok": false,
    "facebook": true,
    "kwai": false,
    "instagram": true
  }
  ```

### 2.5 震动控制
* **接口名 (`methods`)**: `vibrate`
* **功能**: 触发设备震动。
* **参数 (`paramObj`)**: 
  * `amplitude` (Integer): 震动幅度 (1-255)。
  * `duration` (Integer): 震动时长，单位为毫秒 (ms)。
* **回调**: 无

### 2.6 复制文本到剪贴板
* **接口名 (`methods`)**: `copyToClipboard`
* **功能**: 将指定的文本复制到系统剪贴板。
* **参数 (`paramObj`)**: 
  * `text` (String): 需要复制的文本内容。
* **回调**: 无

### 2.7 唤起原生分享
* **接口名 (`methods`)**: `shareTo`
* **功能**: 唤起系统原生的分享面板或定向分享到指定 App。
* **参数 (`paramObj`)**: 
  * `title` (String): 分享的标题。
  * `content` (String): 分享的正文内容。
  * `imageUrl` (String): 分享的图片链接（可选，有此 URL 则分享图片+文字）。
  * `targetApp` (String): 目标应用包名或标识（可选，如 `whatsapp`, `facebook` 等，为空则唤起系统分享）。
* **回调**: 无

### 2.8 动态权限申请
* **接口名 (`methods`)**: `requestPermission`
* **功能**: 主动向原生申请指定的系统权限。
* **参数 (`paramObj`)**: 
  * `permissions` (Array<String>): 需要申请的权限标识符数组。支持的标识：`camera`, `storage`, `manage_storage`, `audio`, `location`, `notification`, `contacts`。
  * `explainReason` (String): 向用户解释为何需要该权限的文案。
  * `forwardtoSettingReason` (String): 引导用户去设置页开启权限的文案。
* **回调数据 (`自定义回调名或默认`)**: JSON 字符串，表示申请结果。

### 2.9 获取权限状态
* **接口名 (`methods`)**: `getPermissionStatus`
* **功能**: 检查指定权限当前的授权状态（不触发申请弹窗）。
* **参数 (`paramObj`)**: 包含 `permissions` (Array<String>)。
* **回调数据 (`自定义回调名或默认`)**: JSON 字符串，表示当前状态。

### 2.10 WebView 导航控制
* **接口名 (`methods`)**: `goBack`
* **功能**: 触发原生 WebView 的返回上一页操作。如果 WebView 无法返回，则会退出页面。
* **参数 (`paramObj`)**: 无
* **回调**: 无

### 2.11 版本更新通知
* **接口名 (`methods`)**: `appUpdate`
* **功能**: 通知原生端进行版本更新下载。
* **参数 (`paramObj`)**: 
  * `needUpdate` (Boolean): 是否需要更新。
  * `updateUrl` (String): APK 下载链接。
  * `versionCode` (Integer): 新版本号。
  * `versionName` (String): 新版本名。
  * `apkName` (String): 下载保存的 APK 文件名。
  * `isBackGround` (Boolean): 是否后台更新。
  * `isForceUpdate` (Boolean): 是否强制更新。
* **回调**: 无

### 2.12 获取渠道号
* **接口名 (`methods`)**: `channelInfo`
* **功能**: 从 Native 获取当前包的渠道号。
* **参数 (`paramObj`)**: 无
* **回调数据 (`channelInfo_callback`)**: JSON 字符串，包含渠道信息。

### 2.13 Remote Config 获取
* **接口名 (`methods`)**: `getBaseUrlInfo`
* **功能**: 获取 Remote Config 上面配置的参数（如 H5 真实域名）。
* **参数 (`paramObj`)**: 无
* **回调数据 (`getBaseUrlInfo_callback`)**: JSON 字符串，包含配置信息。

### 2.14 获取 FCM Token
* **接口名 (`methods`)**: `getPushToken`
* **功能**: 获取 Firebase Cloud Messaging (FCM) Token。
* **参数 (`paramObj`)**: 无
* **回调数据 (`getPushToken_callback`)**: JSON 字符串，包含 FCM Token：
  ```json
  {
    "FCMRegistrationToken": "XXX"
  }
  ```

### 2.15 模拟点击消息通知栏 (测试用)
* **接口名 (`methods`)**: `clickNotificationBar`
* **功能**: 模拟发送通知并点击，用于测试通知回传数据。
* **参数 (`paramObj`)**: 无
* **回调数据 (`clickNotificationBar_callback`)**: JSON 字符串，包含通知数据：
  ```json
  {
    "NotificationTitle": "XXX",
    "NotificationContent": "XXX"
  }
  ```

---

## 3. 原生主动调用的 JS 方法 (事件监听)

### 3.1 Deep Link 接收
* **方法名**: `window.JSBridge.onDeepLink(url)`
* **功能**: 原生接收到 DeepLink 唤醒后，会将 URL 传递给 H5。

### 3.2 前后台状态变更通知
* **方法名**: `onAppLifecycle(jsonStr)` (需在 H5 定义该全局函数)
* **功能**: 原生在应用切后台/切前台时，会主动调用该方法。
* **数据格式**:
  ```json
  { "status": "foreground" } // 切到前台
  // 或
  { "status": "background" } // 切到后台
  ```

---

## 4. 特殊机制说明

1. **相册/文件选择**: 
   原生实现了 `onShowFileChooser`，在 H5 使用 `<input type="file" accept="image/*">` 时，会自动唤起原生相册，选定图片后并经过处理再返回给 H5。
2. **H5 权限请求拦截**: 
   对于 H5 中使用 `navigator.mediaDevices.getUserMedia` 等请求媒体权限的行为，原生会拦截并自动映射为系统的相机/麦克风权限申请。
3. **Deep Link 处理**:
   原生重写了 `shouldOverrideUrlLoading`。对于 `intent://` 格式的 URI 和匹配 Activity `<intent-filter>` 的普通 URL，会进行拦截跳转或应用内唤醒处理。
