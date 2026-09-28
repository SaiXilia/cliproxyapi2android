# CLIProxyAPI for Android (安卓移动端)

[English](README.md) | 中文说明

CLIProxyAPI Android 是基于 [CLIProxyAPI](https://github.com/router-for-me/CLIProxyAPI) 移植的高性能移动端 AI 本地代理网关。它在 Android 设备上本地运行，为手机内部客户端（如 Chatbox 等）提供兼容 OpenAI、Claude、Gemini、Codex 的全协议转换、多账号轮询与思维链（Thinking）处理服务。

---

## 📱 核心移动端特性

- **双进程隔离架构**：UI 界面与后台代理服务（`:proxy` 独立服务进程）物理隔离，彻底隔离 Go 运行时崩溃对主界面的影响，保障系统稳定性。
- **全网卡绑定与 VPN 兼容**：服务监听 `0.0.0.0`，客户端可通过永久固定的 `127.0.0.1:8317` 本地直连，或通过 Wi-Fi 局域网 IP 跨设备共享使用。
- **后台防冻结与休眠抗性**：集成硬件级 `PARTIAL_WAKE_LOCK` 唤醒锁与 `START_STICKY` 粘性自愈机制，防止 HyperOS 等系统的 Cgroup 墓碑机制冻结代理网络。
- **状态栏精简常驻**：纯净无扰的状态栏前台常驻通知，轻量显示服务运行状态并保障后台长效保活。
- **16KB 内存页对齐**：原生 C-Shared 动态库（`libcliproxy.so`）与可执行文件严格执行 16KB 页面对齐，完美支持 Android 14 / 15+ 现代内核。
- **云端全自动持续构建**：内置 GitHub Actions 流水线，每日自动同步上游核心更新并编译出最新 APK 安装包。

---

## 🚀 核心大模型转译能力

- **多协议双向转换**：OpenAI ⇄ Claude ⇄ Gemini ⇄ Codex 全协议互相兼容转译。
- **深度思考（Thinking）**：完整保留 Claude Extended Thinking (`budget_tokens`)、Gemini 思考配置以及 OpenAI `reasoning_effort`。
- **多账号负载均衡**：本地支持配置多个上游账号，请求自动执行轮询调度。
- **429 智能冷却**：上游触发速率限制时自动标记冷却，并无缝故障转移至备选可用凭据。
- **Token 自动刷新**：运行时自动维护与续期 OAuth 访问令牌。
- **离线就绪 Web 控制台**：内置可视化管理面板，离线即可查看服务状态，联网时自动同步最新管理资产。

---

## 🛠️ 快速开始

### 1. 启动服务
安装并打开应用，点击主界面的【启动服务】按钮，通知栏将常驻显示服务运行状态。

### 2. 客户端配置
在任何支持自定义 API 的 AI 客户端中填写：
- **API 接口地址**：`http://127.0.0.1:8317/v1`（本机使用，永久固定无需变动）
- **局域网共享地址**：`http://<手机局域网IP>:8317/v1`（同一 Wi-Fi 下供电脑/平板等跨设备使用）
- **API Key**：根据主界面设置填写（支持免密模式或使用主界面生成的 Key）

### 3. Web 管理控制台
在手机浏览器或电脑中访问：
```text
http://127.0.0.1:8317/management.html
```
可实时查看账号池配额、模型映射别名及运行监控。

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

### 云端自动化编译与正式发布
- **持续集成构建**：每次推送代码至仓库，GitHub Actions 将自动在云端完成 Go 核心交叉编译并打包出测试版 APK，可在 Actions 页面直接下载。
- **自动化发布 Release**：向仓库推送版本标签（例如 `git tag v1.0.0 && git push origin v1.0.0`），或在 GitHub Actions 页面手动运行 **Release Android APK and CLI** 工作流，系统将自动打包并发布包含独立安装包（`CLIProxyAPI-Android-v*.apk`）与 SHA256 校验文件的正式 Release。
