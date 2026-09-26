# CLIProxyAPI Android 首版交付与发布说明

**版本**：v1.0.0-android  
**基线 Commit**：`ed980be34b9981735eaa16941956b1e8d9abfb7c`  
**编译日期**：2026-09-26  
**编译环境**：Windows 11 x64, Go 1.27.0, Android NDK r29 (29.0.14206865), Clang 21  

---

## 一、双交付形态与产物清单

所有产物均位于工程根目录下 `dist/android/`：

| 产物文件 | 适用架构 | 类型与定位 | 体积大小 | 16KB 页对齐状态 |
| :--- | :--- | :--- | :--- | :--- |
| `dist/android/app-debug.apk` | `arm64-v8a` + `x86_64` | **主要交付**：开箱即用的 Android 原生应用 | 157 MB (双架构包) | **100% 验证通过** (`zipalign -c -P 16`) |
| `dist/android/arm64-v8a/cli-proxy-api` | `arm64-v8a` | **辅助交付**：Termux / ADB Shell 独立二进制 | 49 MB (动态链接 Bionic) | **100% 验证通过** (`llvm-readelf Align 0x4000`) |
| `dist/android/arm64-v8a/libcliproxy.so` | `arm64-v8a` | JNI C-Shared 动态库，供原生宿主集成 | 48 MB | **100% 验证通过** (`llvm-readelf Align 0x4000`) |
| `dist/android/x86_64/cli-proxy-api` | `x86_64` | 模拟器独立可执行二进制 | 52 MB | **100% 验证通过** (`llvm-readelf Align 0x4000`) |
| `dist/android/x86_64/libcliproxy.so` | `x86_64` | 模拟器 JNI C-Shared 动态库 | 52 MB | **100% 验证通过** (`llvm-readelf Align 0x4000`) |

---

## 二、裁剪成果与依赖审计

通过严谨的 `//go:build !android` 原位打标与 `_android.go` 自包含打桩：
- **重型依赖 100% 剥离**：经 `scripts/android/check_deps.py` 自动化静态依赖图解析，`pion/webrtc`、`redis/go-redis`、`jackc/pgx`、`go-git`、`minio-go`、`charmbracelet/bubbletea`、`libp2p/zeroconf`、`open-golang` 在 Android 构建图中**完全为 0 存在**。
- **体积优化显著**：桌面完整版约为 89MB，裁剪后的单架构 ELF 可执行文件降为 49MB（减少约 45% 的符号与机器码冗余）。
- **上游核心代码 0 侵入**：协议翻译器、模型思考管道、各模型执行器（Claude / OpenAI / Gemini / xAI / Kimi / Antigravity / Devin）源码保持 100% 原汁原味。

---

## 三、核心自更新与移动端健壮性

1. **模型定义自更新**：后台运行会话启动时自动触发 `StartModelsUpdater`、`StartCodexClientModelsUpdater`、`StartDevinModelsUpdater`，支持免换包动态加载新模型。
2. **Web 管理后台自更新与离线降级**：
   - 联网状态下自动从官方 GitHub Release / CDN 下载覆盖最新的单页 `management.html`；
   - 离线全新安装时，服务自动内置轻量控制台页面，绝不因无网络而中断本地访问。
3. **单会话独立进程契约**：
   - Android App 采用独立的 `:proxy` 前台服务进程承载每次代理会话；
   - 用户停止服务后，`:proxy` 进程安全退出，彻底规避 Go Runtime 中各全局 `sync.Once` 单例无法二次初始化的潜在隐患。
4. **OAuth 双轨闭环**：
   - App 模式下通过轮询拉取机制将授权 URL 传导至主线程打开 Chrome Custom Tabs；
   - CLI 模式下在控制台显著打印格式化授权链接，方便用户点击或复制。

---

## 四、安装与使用说明

### 1. Android APK 图形化使用
1. 使用 ADB 或文件管理器将 `dist/android/app-debug.apk` 安装到 Android 手机：
   ```bash
   adb install -r dist/android/app-debug.apk
   ```
2. 打开 **CLIProxy API** 应用：
   - 点击 **“启动服务”** 按钮（初次启动将自动在应用私有存储区生成 `config.yaml`、API 密钥与管理口令）；
   - 服务启动后，常驻通知栏将显示运行状态，并附带便捷的“停止服务”操作；
   - 点击 **“打开管理控制台 (Web UI)”** 即可在浏览器中配置模型账号或导入现有凭据。

### 2. Termux / ADB 终端独立运行
1. 将 `dist/android/arm64-v8a/cli-proxy-api` 传输至手机 Termux 私有目录：
   ```bash
   chmod +x ./cli-proxy-api
   ./cli-proxy-api --config ./config.yaml
   ```
2. 验证本地运行：
   ```bash
   curl -s http://127.0.0.1:8317/healthz
   # 预期输出: {"status":"ok"}
   ```
