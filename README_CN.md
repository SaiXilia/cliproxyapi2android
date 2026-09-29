# CLIProxyAPI for Android (安卓移动端)

[![最新版本](https://img.shields.io/github/v/release/SaiXilia/cliproxyapi2android?display_name=release&sort=semver)](https://github.com/SaiXilia/cliproxyapi2android/releases/latest)
[![Android 构建](https://github.com/SaiXilia/cliproxyapi2android/actions/workflows/android-build.yml/badge.svg)](https://github.com/SaiXilia/cliproxyapi2android/actions/workflows/android-build.yml)
[![MIT 许可证](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

[English](README.md) | 中文说明

> [!IMPORTANT]
> 这是 [router-for-me/CLIProxyAPI](https://github.com/router-for-me/CLIProxyAPI) 的非官方社区 Android 移植版，并非上游项目官方维护或认可的产品。

CLIProxyAPI Android 在 Android 设备上本地运行 AI 代理网关，为本机客户端提供兼容 OpenAI、Anthropic、Gemini、Codex 的 API、多账号轮询以及深度思考能力。

[下载最新签名 APK](https://github.com/SaiXilia/cliproxyapi2android/releases/latest)

---

## 📱 核心移动端特性

- **双进程隔离架构**：UI 与后台代理服务运行在独立进程中，降低代理运行时异常对界面的影响。
- **本地回环监听与 VPN 兼容**：默认监听 `127.0.0.1:8317`，也可选择局域网访问，并提供 VPN 绕过规则说明。
- **后台运行可靠性**：使用前台服务、`PARTIAL_WAKE_LOCK` 与 `START_STICKY` 恢复机制，在服务开启期间维持本地代理运行。
- **状态栏精简常驻**：纯净无扰的状态栏前台常驻通知，轻量显示服务运行状态并保障后台长效保活。
- **16KB 内存页对齐**：原生 C-Shared 动态库（`libcliproxy.so`）按 16KB 页面要求构建，适配现代 Android 设备。
- **云端全自动持续构建**：内置 GitHub Actions 流水线，每日自动同步上游核心更新并编译出最新 APK 安装包。

---

## 🚀 核心大模型转译能力

- **多协议双向转换**：OpenAI ⇄ Anthropic ⇄ Gemini ⇄ Codex 协议兼容转译。
- **深度思考（Thinking）**：保留 Anthropic Extended Thinking (`budget_tokens`)、Gemini 思考配置以及 OpenAI `reasoning_effort`。
- **多账号负载均衡**：本地支持配置多个上游账号，请求自动执行轮询调度。
- **429 智能冷却**：上游触发速率限制时自动标记冷却，并无缝故障转移至备选可用凭据。
- **Token 自动刷新**：运行时自动维护与续期 OAuth 访问令牌。
- **离线就绪 Web 控制台**：内置可视化管理面板，离线即可查看服务状态，联网时自动同步最新管理资产。

---

## 🛠️ 快速开始

### 1. 安装 APK

从[最新 Release](https://github.com/SaiXilia/cliproxyapi2android/releases/latest)下载 APK。Android 可能要求允许浏览器或文件管理器安装未知来源应用。正式 APK 使用固定证书签名；应用内更新还会校验包名、版本号、SHA-256 与签名证书。

### 2. 启动服务
安装并打开应用，点击主界面的【启动服务】按钮，通知栏将常驻显示服务运行状态。

### 3. 客户端配置
在任何支持自定义 API 的 AI 客户端中填写：
- **OpenAI 兼容 Base URL**：`http://127.0.0.1:8317/v1`（本机使用，永久固定无需变动）
- **Anthropic 兼容 Base URL**：`http://127.0.0.1:8317`（本机使用，永久固定无需变动）
- **API Key**：根据主界面设置填写（支持免密模式或使用主界面生成的 Key）

> [!WARNING]
> 除非确实需要让其他可信设备连接，否则请保持局域网访问关闭。启用前应先设置 API Key，并仅在可信网络中使用；局域网模式会监听设备的所有网络接口。

### 4. Web 管理控制台
在 Android 设备的浏览器中访问：
```text
http://127.0.0.1:8317/management.html
```
启用局域网访问后，其他可信设备可使用应用显示的局域网地址访问。控制台可查看账号池配额、模型映射别名及运行状态。

### Android 权限说明

| 权限 | 用途 |
| --- | --- |
| 网络与网络状态 | 连接已配置的 AI 服务、OAuth 端点、管理资源与签名更新信息 |
| 前台服务与唤醒锁 | 仅在用户启用服务时维持本地代理运行 |
| 通知 | 显示前台服务必要的运行状态以及可用更新 |
| 安装未知应用 | 打开 Android 安装界面，由用户确认安装签名 APK 更新 |

应用不会申请通讯录、位置、相机、麦克风或存储权限。

---

## 🏗️ 目录结构说明

```text
├── android/            # Android 宿主工程 (Kotlin, Material3 UI, 前台服务)
├── cmd/
│   ├── mobile/         # C-Shared 动态库与 JNI 桥接入口 (libcliproxy.so)
│   └── server/         # CLIProxyAPI 独立二进制入口 (cli-proxy-api)
├── internal/           # 代理核心、协议转译、Thinking 预算与运行环境
├── scripts/android/    # 交叉编译与依赖合规审计脚本
└── .github/workflows/  # GitHub Actions 自动化构建与上游同步流水线
```

---

## ⚙️ 编译构建

### 本地编译
- **环境要求**：Go 1.26+、Android NDK r27c+、JDK 17、Android SDK (API 34)
- **编译原生库与 CLI**：
  ```bash
  bash scripts/android/build_android.sh
  ```
- **编译 Android APK**：
  ```bash
  cd android && ./gradlew assembleDebug
  ```

### 云端构建与签名发布
- **持续集成构建**：推送代码或创建 Pull Request 后，GitHub Actions 会自动完成 Go 核心交叉编译并打包测试版 APK，可在 Actions 页面下载。
- **手动发布应用版本**：普通代码推送只生成测试构建，不会创建 Release。需要发布累积的 Android 修复时，手动运行 **Release Signed Android Update**，填写下一个应用版本号，例如 `1.1.2`。
- **自动发布核心版本**：每日稳定版同步流程会检测 `router-for-me/CLIProxyAPI` 的最新正式版本；发现新版后会自动合并、验证并发布，但不会改变由你手动指定的 Android 应用版本。
- **独立版本格式**：应用版本与核心版本相互独立。Release 显示为 `CLIProxyAPI Android 1.1.2 · Core 8.0.4`，使用简洁 Tag `1.1.2-core.8.0.4`；每次发布仍会提高 Android 内部 `versionCode`。每个版本附带签名 APK、`update.json` 与 SHA-256 校验文件。

### 更新与安全

- App 启动时及 Android 每日后台任务会检查本仓库的最新 GitHub Release。
- 更新 APK 必须通过包名、版本号、SHA-256 与签名证书验证，随后才会打开 Android 安装界面。
- Android 仍会要求用户确认安装；App 不会静默加载远程可执行代码。
- 参见 [安全策略](SECURITY.md) 与 [隐私说明](PRIVACY.md)。

## 贡献与上游

- 欢迎反馈 Android 端问题或提交改进；参与前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。
- 请勿在公开 Issue 中上传 API Key、OAuth 文件、`config.yaml` 或应用私有数据。
- 代理核心来自 [router-for-me/CLIProxyAPI](https://github.com/router-for-me/CLIProxyAPI)，归属说明见 [NOTICE.md](NOTICE.md)。
