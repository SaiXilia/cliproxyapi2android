# CLIProxyAPI for Android

English | [中文说明](README_CN.md)

CLIProxyAPI Android is a high-performance local AI proxy gateway ported from [CLIProxyAPI](https://github.com/router-for-me/CLIProxyAPI). Running locally on Android devices, it provides OpenAI, Claude, Gemini, and Codex compatible API translation, multi-account round-robin scheduling, and extended thinking support for local clients (such as Chatbox, etc.).

---

## 📱 Mobile Architecture Features

- **Dual-Process Isolation**: The Android UI process and background proxy service (`:proxy` standalone process) are physically separated, shielding the UI from any Go runtime panics.
- **Secure Local Loopback Binding & VPN Compatibility**: Listens strictly on `127.0.0.1:8317`, allowing on-device clients to connect directly without external network exposure risks, fully compatible with local VPN bypass routing.
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
- **OpenAI-Compatible Base URL**: `http://127.0.0.1:8317/v1` (for on-device apps, permanently fixed)
- **Claude-Compatible Base URL**: `http://127.0.0.1:8317` (for on-device apps, permanently fixed)
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
- **Manual App Releases**: Normal pushes only create test artifacts. To publish accumulated Android fixes, run **Release Signed Android Update** and enter the next app version, such as `1.1.2`.
- **Automatic Core Releases**: The daily stable-upstream workflow detects new official `router-for-me/CLIProxyAPI` releases, merges and validates them, increments the Android patch version, then publishes the app with the new core.
- **Release Versions**: App releases use a normal Android version such as `1.1.2` and the non-conflicting tag `android-v1.1.2`. The bundled core version is recorded in the release details and `update.json`. Each release includes the signed APK and SHA-256 checksums.

### Updates and security

- The app checks this repository's latest GitHub Release on startup and through a daily Android background job.
- Update APKs must pass package name, version code, SHA-256, and signing-certificate verification before Android's installer is opened.
- Android still requires the user to approve installation. The app does not silently load downloaded executable code.
- See [Security Policy](SECURITY.md) and [Privacy](PRIVACY.md).
