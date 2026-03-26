# H5 与 Android Native 对接文档

本文档详细说明了 H5 页面如何与 Android Native 端进行交互，包括 JS 桥接的初始化、调用方式、支持的接口协议以及 Native 主动调用的全局方法。

## 1. 交互基础机制

Native 会在 WebView 中注入一个名为 `App` 的全局对象。H5 统一通过调用 `window.App.JSToNative(jsonStr)` 向 Native 发送消息。
Native 处理完成后，会通过执行 `javascript:callbackName('jsonResult')` 的方式将结果回调给 H5。

### 请求数据格式

H5 向 Native 发送的 `jsonStr` 必须是满足以下结构的 JSON 字符串：

```json
{
  "methods": "调用的方法名（如 getDeviceRiskInfo）",
  "callback": "回调的全局函数名（可选）",
  "paramObj": {
    // 具体的参数对象
  }
}
```

### 通用调用示例

```javascript
function callNative(method, params, callbackName) {
    const request = {
        methods: method,
        callback: callbackName,
        paramObj: params || {}
    };
    if (window.App && window.App.JSToNative) {
        window.App.JSToNative(JSON.stringify(request));
    } else {
        console.error("Native环境未准备好");
    }
}
```

---

## 2. API 接口列表

H5 请求的 `methods` 字段必须与下方列表保持一致。

### 2.1 设备与环境信息
**获取设备风险及基础信息 (`getDeviceRiskInfo`)**
获取设备的详细信息，包括是否是模拟器、是否 root、网络状态、版本号等。
- **methods**: `getDeviceRiskInfo`
- **返回结果**: 包含 `isEmulator`, `isRooted`, `deviceId`, `versionName` 等字段的 JSON 字符串。

**获取渠道号 (`channelInfo`)**
- **methods**: `channelInfo`
- **返回结果**: `{"channel": "渠道名称"}`

**获取 Firebase 推送 Token (`getPushToken`)**
- **methods**: `getPushToken`
- **返回结果**: `{"FCMRegistrationToken": "token_string"}`

**获取 Remote Config 配置 (`getBaseUrlInfo`)**
- **methods**: `getBaseUrlInfo`
- **返回结果**: 包含 `mainUrlText` 和 `mainUrlGanha` 的 JSON 字符串。

### 2.2 社交与分享
**检查社交软件安装状态 (`installedSocialApps`)**
- **methods**: `installedSocialApps`
- **返回结果**: `{"whatsapp": true, "facebook": false, "messenger": true, "instagram": false, "tiktok": true, "kwai": false}`

**触发原生分享 (`shareTo`)**
支持指定平台分享图文或纯文本，附带不同 App 的专属防封策略（如 Facebook 系列纯文本拦截处理）。
- **methods**: `shareTo`
- **paramObj**:
  ```json
  {
    "text": "分享的文本",
    "image_url": "分享的图片URL(网络或Base64)",
    "platform": "指定平台(whatsapp/facebook/messenger/instagram/tiktok/kwai/sms)或为空调起系统面板"
  }
  ```

### 2.3 权限与系统交互
**动态申请权限 (`requestPermission`)**
- **methods**: `requestPermission`
- **paramObj**:
  ```json
  {
    "permissions": ["camera", "storage", "manage_storage", "audio", "location", "notification", "contacts"], 
    "explainReason": "申请权限的弹窗解释文案",
    "forwardtoSettingReason": "前往系统设置开启权限的文案"
  }
  ```
- **返回结果**: `{"status": "granted" | "denied" | "error"}`

**获取权限状态 (`getPermissionStatus`)**
- **methods**: `getPermissionStatus`
- **paramObj**: 结构同 `requestPermission`
- **返回结果**: `{"status": "granted" | "denied" | "error"}`

**复制文本到系统剪贴板 (`copyToClipboard`)**
- **methods**: `copyToClipboard`
- **paramObj**: `{"text": "需要复制的文本"}`

### 2.4 媒体与 UI 交互
**保存图片到系统相册 (`saveImageToGallery`)**
- **methods**: `saveImageToGallery`
- **paramObj**: `{"url": "图片网络地址或Base64"}`

**设置状态栏颜色 (`setStatusBarColor`)**
- **methods**: `setStatusBarColor`
- **paramObj**: `{"isLight": true, "color": "#FFFFFF"}`

**触发设备震动 (`vibrate`)**
- **methods**: `vibrate`
- **paramObj**: `{"amplitude": 255, "duration": 50}` (amplitude幅度，duration时长毫秒)

**原生页面后退 (`goBack`)**
触发原生 WebView 返回上一页，当不能再后退时关闭 Activity。
- **methods**: `goBack`

**刷新页面 (`refreshPage`)**
- **methods**: `refreshPage`

**移除原生开屏动画 (`removeSplashScreen`)**
主动通知原生层关闭覆盖在 H5 顶部的启动闪屏 WebView。
- **methods**: `removeSplashScreen` 

**使用外部浏览器打开链接 (`openUrlExternally`)**
- **methods**: `openUrlExternally`
- **paramObj**: `{"url": "https://..."}`

### 2.5 版本管理
**触发版本更新 (`appUpdate`)**
通知原生弹出下载弹窗或在后台静默下载并拉起安装。
- **methods**: `appUpdate`
- **paramObj**:
  ```json
  {
    "needUpdate": true,
    "updateUrl": "apk下载地址",
    "versionCode": 2,
    "versionName": "1.1.0",
    "apkName": "app-release",
    "isBackGround": true, 
    "isForceUpdate": false 
  }
  ```

---

## 3. Native 主动调用 H5 (全局回调)

H5 需要在全局作用域（`window`）下注册以下方法，以便接收 Native 层的事件通知：

### 3.1 前后台生命周期 (`onAppLifecycle`)
当 App 切换到后台或返回前台时触发。
```javascript
window.onAppLifecycle = function(jsonStr) {
    const data = JSON.parse(jsonStr);
    console.log("当前状态: ", data.status); // 'foreground' 或 'background'
};
```

### 3.2 Deep Link 唤醒 (`JSBridge.onDeepLink`)
当通过系统外部 Scheme (如 `intent://`) 唤醒 App 时，将接收的 URL 传递给 H5。
```javascript
window.JSBridge = window.JSBridge || {};
window.JSBridge.onDeepLink = function(url) {
    console.log("Deep Link 唤醒 URL:", url);
};
```

### 3.3 Firebase 推送数据透传 (`getFCMData_callback`)
当 App 收到 Firebase 下发的通知消息透传数据时触发。
```javascript
window.getFCMData_callback = function(jsonStr) {
    const data = JSON.parse(jsonStr);
    console.log(data.NotificationTitle, data.NotificationContent, data.MsgData);
};
```

### 3.4 通知栏点击事件 (`clickNotificationBar_callback`)
当用户在系统通知栏点击 App 下发的通知时触发。
```javascript
window.clickNotificationBar_callback = function(jsonStr) {
    const data = JSON.parse(jsonStr);
    console.log("用户点击了通知:", data);
};
```

### 3.5 开屏动画结束兜底 (`finishAnimationFast`)
原生自动移除开屏动画时调用的回调，H5 可用此判断原生是否准备完毕。
```javascript
window.finishAnimationFast = function() {
    console.log("原生开屏页已被移除");
};
```

---

## 4. WebView 隐式支持原生特性

除上述桥接 API 之外，原生 WebView 还隐式拦截并支持了以下特性，无需专门调用 `callNative`：

1. **选择文件唤起相册 (`<input type="file" accept="image/*">`)**: Native 实现了 `onShowFileChooser`，点击 input 会唤起系统相册，并能在后台线程对大于 500KB 的图片进行质量压缩后返回给 H5。
2. **长按图片保存**: Native 拦截了网页的长按事件，长按任意图片会自动弹出保存到系统相册的提示对话框。
3. **音视频权限映射**: 当 H5 调用 `navigator.mediaDevices.getUserMedia` 时，Native 会自动拦截并拉起 Android 系统相机/麦克风权限请求弹窗。
4. **Intent 协议拦截**: 对于页面中触发的 `intent://` 链接，Native 会尝试在外部拉起对应应用；如果设备未安装，则回退执行 Intent 中指定的 `browser_fallback_url`。
