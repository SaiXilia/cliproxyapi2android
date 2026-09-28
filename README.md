# CLIProxyAPI for Android

English | [中文说明](README_CN.md)

CLIProxyAPI Android is a high-performance local AI proxy gateway ported from [CLIProxyAPI](https://github.com/router-for-me/CLIProxyAPI). Running locally on Android devices, it provides OpenAI, Claude, Gemini, and Codex compatible API translation, multi-account round-robin scheduling, and extended thinking support for local clients (such as MoonEdit, Chatbox, etc.).

---

## 📱 Mobile Architecture Features

- **Dual-Process Isolation**: The Android UI process and background proxy service (`:proxy` standalone process) are physically separated, shielding the UI from any Go runtime panics.
- **Full Interface Binding & VPN Compatibility**: Listens on `0.0.0.0`, allowing local clients to connect directly via `127.0.0.1:8317` without VPN loopback interception, or share across devices via Wi-Fi LAN IP.
- **Anti-Freeze & Anti-Sleep Resilience**: Integrates hardware-level `PARTIAL_WAKE_LOCK` and `START_STICKY` service recovery to prevent system cgroup freezer (tombstoning) mechanisms from freezing network proxy sockets.
- **Persistent Lightweight Notification**: Clean status bar notification indicating service running status while preventing system suspension.
- **16KB Memory Page Alignment**: Both the native C-Shared library (`libcliproxy.so`) and standalone binaries strictly adhere to 16KB page alignment, ensuring full compatibility with Android 14 / 15+ kernels.
- **Automated Cloud CI/CD**: Built-in GitHub Actions workflows automatically sync with upstream core changes daily and build release-ready APK packages.

---

## 🚀 Core Model Capabilities

- **Bidirectional Protocol Translation**: Transparent bidirectional conversion between OpenAI ⇄ Claude ⇄ Gemini ⇄ Codex protocols.
- **Extended Thinking**: Preserves Claude Extended Thinking (`budget_tokens`), Gemini thinking configurations, and OpenAI `reasoning_effort`.
- **Multi-Account Load Balancing**: Supports multiple upstream accounts locally with automatic round-robin scheduling.
- **Intelligent 429 Cooldown**: Automatically tags rate-limited credentials with cooldowns and performs seamless failover to backup accounts.
- **Automatic Token Refresh**: Background maintenance and timely renewal of OAuth access tokens.
- **Offline-Ready Web Management**: Built-in visual dashboard ready for offline use, with automatic asset updates when connected.

---

## 🛠️ Quick Start

### 1. Start Service
Open the app and tap **"启动服务" (Start Service)**. A persistent notification will appear in the status bar indicating service status.

### 2. Client Configuration
In any AI client supporting custom endpoints, enter:
- **Local Endpoint**: `http://127.0.0.1:8317/v1` (for on-device apps, permanently fixed)
- **LAN Endpoint**: `http://<Device_LAN_IP>:8317/v1` (for cross-device access on the same Wi-Fi)
- **API Key**: Configure based on app settings (supports keyless open access or the generated API Key)

### 3. Web Management Console
Access from a mobile or desktop browser:
```text
http://127.0.0.1:8317/management.html
```
Monitor account quota pools, manage model aliases, and inspect operational metrics.

---

## 🏗️ Repository Structure

```text
├── android/            # Android host project (Kotlin, Material3 UI, Foreground Service)
├── cmd/
│   ├── mobile/         # C-Shared library & JNI bridge entry (libcliproxy.so)
│   └── server/         # Standalone CLIProxyAPI executable (cli-proxy-api)
├── internal/           # Core proxy runtime, protocol translation, and thinking pipelines
├── scripts/android/    # Cross-compilation and dependency audit scripts
└── .github/workflows/  # GitHub Actions workflows for building and upstream syncing
```

---

## ⚙️ Build Instructions

### Local Build
- **Prerequisites**: Go 1.26+, Android NDK r27c+, JDK 17, Android SDK (API 34)
- **Build Native Libraries & CLI**:
  ```bash
  bash scripts/android/build_android.sh
  ```
- **Build Android APK**:
  ```bash
  cd android && ./gradlew assembleDebug
  ```

### Cloud Automated Build & Official Releases
- **Continuous Integration**: Pushing code triggers GitHub Actions to cross-compile the Go core and package the APK automatically, available under the Actions tab.
- **Automated Releases**: Pushing a version tag (e.g. `git tag v1.0.0 && git push origin v1.0.0`) or manually triggering the **Release Android APK and CLI** workflow automatically publishes an official GitHub Release with standalone APK packages (`CLIProxyAPI-Android-v*.apk`) and SHA-256 checksums.
