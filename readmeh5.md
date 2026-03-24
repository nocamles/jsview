# JSBridge 接口说明文档

本文档描述了 H5 页面如何与 Android Native 客户端进行交互的 JSBridge 接口。

## 1. 基础调用方式

H5 页面通过调用注入到 `window` 对象中的 `android.JSToNative(jsonString)` 方法向原生发送消息。

请求数据的标准 JSON 格式如下：

```javascript
{
  "methods": "方法名",           // 必需：要调用的原生方法名
  "callback": "回调函数名",     // 可选：原生执行完毕后需要调用的全局作用域下的 JS 回调函数名
  "paramObj": {                  // 可选：传递给原生的参数对象
     // ... 具体的参数字段
  }
}
```

示例封装函数：

```javascript
function callNativeWithCustomCallback(method, paramObj, callbackName) {
    const request = {
        methods: method,
        callback: callbackName,
        paramObj: paramObj
    };

    const jsonStr = JSON.stringify(request);
    if (window.android && window.android.JSToNative) {
        window.android.JSToNative(jsonStr);
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
* **回调**: 无

### 2.2 保存图片到相册
* **接口名 (`methods`)**: `saveImageToGallery`
* **功能**: 将网络图片或 Base64 格式的图片保存到设备相册。
* **参数 (`paramObj`)**: 
  * `url` (String): 图片的网络地址或 Base64 字符串（以 `data:image...` 开头）。
* **回调**: 无（原生端会自动处理权限申请和成功/失败的 Toast 提示）。
> **注**: WebView 默认拦截了长按图片动作，会自动弹窗提示保存，H5 无需额外处理长按逻辑。

### 2.3 获取设备信息
* **接口名 (`methods`)**: `deviceInfo`
* **功能**: 获取设备的详细信息（是否模拟器、VPN、Root、国家代码、应用版本号等）。
* **参数 (`paramObj`)**: 无
* **回调数据**: JSON 字符串，格式包含如下字段：
  ```json
  {
    "isEmulator": false,
    "isVpnOrProxy": false,
    "isRooted": false,
    "isUsbDebuggingOrDevMode": false,
    "hasSimCard": true,
    "sim_country": "cn",
    "simOperator": "46000",
    "deviceId": "xxxxx",
    "versionCode": 1,
    "versionName": "1.0",
    "packageName": "com.ganha.test",
    "statusBarHeight": 24.5
  }
  ```

### 2.4 获取已安装的社交软件列表
* **接口名 (`methods`)**: `installedSocialApps`
* **功能**: 获取本机是否安装了指定的几款社交软件。
* **参数 (`paramObj`)**: 无
* **回调数据**: JSON 字符串，表示安装状态：
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
  * `amplitude` (Integer): 震动幅度 (1-255，部分旧设备可能不支持设置幅度)。
  * `duration` (Integer): 震动时长，单位为毫秒 (ms)。
* **回调**: 无

### 2.6 复制文本到剪贴板
* **接口名 (`methods`)**: `copyToClipboard`
* **功能**: 将指定的文本复制到系统剪贴板。
* **参数 (`paramObj`)**: 
  * `text` (String): 需要复制的文本内容。
* **回调**: 无（原生端会弹出复制成功的 Toast）。

### 2.7 唤起原生分享
* **接口名 (`methods`)**: `shareTo`
* **功能**: 唤起系统原生的分享面板。
* **参数 (`paramObj`)**: 
  * `title` (String): 分享的标题（可选）。
  * `content` (String): 分享的正文内容（可选）。
  * `url` (String): 分享的链接（可选）。
* **回调**: 无

### 2.8 动态权限申请
* **接口名 (`methods`)**: `requestPermission`
* **功能**: 主动向原生申请指定的系统权限。
* **参数 (`paramObj`)**: 
  * `permissions` (Array<String>): 需要申请的权限标识符数组。支持的标识：`camera` (相机), `storage` (存储), `manage_storage` (所有文件访问), `audio` (麦克风), `location` (定位), `notification` (通知), `contacts` (通讯录)。
  * `explainReason` (String): 向用户解释为何需要该权限的文案。
  * `forwardtoSettingReason` (String): 引导用户去设置页开启权限的文案。
* **回调数据**: JSON 字符串，表示申请结果：
  ```json
  { "status": "granted" } // 或 "denied" / "error"
  ```

### 2.9 获取权限状态
* **接口名 (`methods`)**: `getPermissionStatus`
* **功能**: 检查指定权限当前的授权状态（不触发申请弹窗）。
* **参数 (`paramObj`)**: 同 `requestPermission` 中的 `permissions` 参数。
* **回调数据**: JSON 字符串：
  ```json
  { "status": "granted" } // 或 "denied"
  ```

### 2.10 WebView 导航控制
* **接口名 (`methods`)**: `goBack`
* **功能**: 触发原生 WebView 的返回上一页操作。如果 WebView 无法返回，则会退出当前 Activity。
* **参数 (`paramObj`)**: 无

* **接口名 (`methods`)**: `refreshPage`
* **功能**: 刷新当前 WebView 页面。如果是网络错误页面，会尝试重新加载失败的 URL。
* **参数 (`paramObj`)**: 无

### 2.11 版本更新检查与通知
* **接口名 (`methods`)**: `appUpdate`
* **功能**: H5 检查到新版本后，将更新信息传递给原生进行 APK 下载与安装逻辑。
* **参数 (`paramObj`)**: 
  * `needUpdate` (Boolean): 是否需要更新。
  * `updateUrl` (String): APK 下载链接。
  * `versionCode` (Integer): 新版本号。
  * `versionName` (String): 新版本名。
  * `apkName` (String): 下载保存的 APK 文件名。
  * `isBackGround` (Boolean): 是否静默后台下载。
  * `isForceUpdate` (Boolean): 是否为强制更新（不可取消）。
* **回调**: 无

### 2.12 外部浏览器打开链接
* **接口名 (`methods`)**: `openUrlExternally`
* **功能**: 调用系统外部浏览器或其他应用打开指定的 URL。
* **参数 (`paramObj`)**: 
  * `url` (String): 需要打开的网络地址。
* **回调**: 无

### 2.13 移除开屏页
* **接口名 (`methods`)**: `removeSplashScreen`
* **功能**: H5 主页加载完毕或准备就绪后，主动通知原生移除原生的开屏遮罩页。
* **参数 (`paramObj`)**: 无
* **回调**: 无
> **注**: 原生端会在主页 `onPageFinished` 时调用开屏页的 JS 方法 `finishAnimationFast()` 尝试移除，此接口可作为 H5 主动控制的手段。

### 2.14 获取FCM Token
* **接口名 (`methods`)**: `getPushToken`
* **功能**: 获取FCM Token，手机需连接VPN
* **参数 (`paramObj`)**: 无
* **回调数据**: JSON 字符串，格式包含如下字段：
  ```json
  {
    "FCMRegistrationToken": "XXX"
  }
  ```

### 2.15 点击消息通知栏
* **接口名 (`methods`)**: `clickNotificationBar`
* **功能**: 收到FCM发送过来的消息后，设备显示通知栏，点击
* **参数 (`paramObj`)**: 无
* **回调数据**: JSON 字符串，格式包含如下字段：暂时定义了这2个字段
  ```json
  {
    "NotificationTitle": "XXX",
    "NotificationContent": "XXX"
  }
  ```

---

## 3. 原生主动调用的 JS 方法 (事件监听)

### 3.1 前后台状态变更通知
* **方法名**: 原生在应用切后台/切前台时，会主动向传入过 `js_onAppLifecycle` 作为回调的事件发送状态。
* **数据格式**:
  ```json
  { "status": "foreground" } // 切到前台
  // 或
  { "status": "background" } // 切到后台
  ```

### 3.2 开屏页动画结束通知 (针对开屏 H5)
* **方法名**: `finishAnimationFast()`
* **功能**: 原生端准备移除开屏 WebView 时，会调用该全局方法通知开屏 H5 可以加速或结束当前动画。

---

## 4. 特殊机制说明

1. **相册/文件选择**: 
   原生实现了 `onShowFileChooser`，在 H5 使用 `<input type="file" accept="image/*">` 时，会自动唤起原生相册，选定图片后并经过压缩处理再返回给 H5。
2. **H5 权限请求拦截**: 
   对于 H5 中使用 `navigator.mediaDevices.getUserMedia` 等请求媒体权限的行为，原生会拦截 `onPermissionRequest` 并自动映射为系统的相机/麦克风权限申请。
3. **Deep Link 处理**:
   原生重写了 `shouldOverrideUrlLoading`。对于 `intent://` 格式的 URI 和匹配 Activity `<intent-filter>` (如 `jsview://`) 的普通 URL，会进行拦截跳转或应用内唤醒处理。