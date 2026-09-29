# CLIProxyAPI for Android

[![Latest release](https://img.shields.io/github/v/release/SaiXilia/cliproxyapi2android?display_name=release&sort=semver)](https://github.com/SaiXilia/cliproxyapi2android/releases/latest)
[![Android build](https://github.com/SaiXilia/cliproxyapi2android/actions/workflows/android-build.yml/badge.svg)](https://github.com/SaiXilia/cliproxyapi2android/actions/workflows/android-build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

[简体中文](README.md) | English

> [!IMPORTANT]
> This is an unofficial community Android port of [router-for-me/CLIProxyAPI](https://github.com/router-for-me/CLIProxyAPI). It is not maintained or endorsed by the upstream project.
>
> The Android implementation was created entirely through maintainer-directed AI collaboration. Product decisions, verification, and releases remain under maintainer control, while the proxy core comes from the upstream project.

CLIProxyAPI Android runs a local AI proxy gateway directly on Android devices. It provides OpenAI-, Anthropic-, Gemini-, and Codex-compatible APIs, multi-account round-robin scheduling, and extended thinking support for on-device clients.

[Download the latest signed APK](https://github.com/SaiXilia/cliproxyapi2android/releases/latest)

---

## 📱 Mobile Architecture Features

- **Dual-Process Isolation**: The Android UI and background proxy service run in separate processes, reducing the impact of a proxy runtime failure on the UI.
- **Secure Local Loopback Binding & VPN Compatibility**: Listens on `127.0.0.1:8317` by default, with optional LAN access and guidance for VPN bypass routing.
- **Background Reliability**: Uses a foreground service, `PARTIAL_WAKE_LOCK`, and `START_STICKY` recovery to keep the local proxy available while enabled.
- **Persistent Lightweight Notification**: Clean status bar notification indicating service running status while preventing system suspension.
- **16KB Memory Page Alignment**: The native C-Shared library (`libcliproxy.so`) is built with 16KB page alignment for modern Android devices.
- **Automated Cloud CI/CD**: Built-in GitHub Actions workflows automatically sync with upstream core changes daily and build release-ready APK packages.

---

## 🚀 Core Model Capabilities

- **Bidirectional Protocol Translation**: Transparent conversion between OpenAI ⇄ Anthropic ⇄ Gemini ⇄ Codex protocols.
- **Extended Thinking**: Preserves Anthropic Extended Thinking (`budget_tokens`), Gemini thinking configurations, and OpenAI `reasoning_effort`.
- **Multi-Account Load Balancing**: Supports multiple upstream accounts locally with automatic round-robin scheduling.
- **Intelligent 429 Cooldown**: Automatically tags rate-limited credentials with cooldowns and performs seamless failover to backup accounts.
- **Automatic Token Refresh**: Background maintenance and timely renewal of OAuth access tokens.
- **Offline-Ready Web Management**: Built-in visual dashboard ready for offline use, with automatic asset updates when connected.

---

## 🛠️ Quick Start

### 1. Install the APK

Download the APK matching your device architecture from the [latest Release](https://github.com/SaiXilia/cliproxyapi2android/releases/latest). Most Android phones use `arm64-v8a`; `x86_64` is mainly for emulators and Intel-based devices. Android may ask you to allow installation from your browser or file manager. Release APKs are signed, and in-app updates automatically select the matching architecture and verify the package name, version code, SHA-256 digest, and signing certificate.

### 2. Start Service
Open the app and tap **"启动服务" (Start Service)**. A persistent notification will appear in the status bar indicating service status.

### 3. Client Configuration
In any AI client supporting custom endpoints, enter:
- **OpenAI-Compatible Base URL**: `http://127.0.0.1:8317/v1` (for on-device apps, permanently fixed)
- **Anthropic-Compatible Base URL**: `http://127.0.0.1:8317` (for on-device apps, permanently fixed)
- **API Key**: Configure based on app settings (supports keyless open access or the generated API Key)

> [!WARNING]
> Keep LAN access disabled unless another trusted device must connect. Before enabling it, configure an API key and use only a trusted network; LAN mode listens on all device network interfaces.

### 4. Web Management Console
Access from a browser on the Android device:
```text
http://127.0.0.1:8317/management.html
```
When LAN access is enabled, another trusted device can use the LAN address displayed by the app. The console lets you monitor account quota pools, manage model aliases, and inspect operational metrics.

### Android permissions

| Permission | Purpose |
| --- | --- |
| Internet and network state | Connect to configured AI providers, OAuth endpoints, management assets, and signed update metadata |
| Foreground service and wake lock | Keep the local proxy running only while the service is enabled |
| Notifications | Show the required foreground-service status and update availability |
| Install unknown apps | Open Android's installer for a user-approved signed APK update |

The app does not request contacts, location, camera, microphone, or storage access.

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

### Cloud Builds & Signed Releases
- **Continuous Integration**: Pushing code or opening a pull request triggers GitHub Actions to cross-compile the Go core and package architecture-specific test APKs, available under the Actions tab.
- **Manual App Releases**: Normal pushes only create test artifacts. To publish accumulated Android fixes, run **Release Signed Android Update** and enter the next app version, such as `1.1.2`.
- **Automatic Core Releases**: The daily stable-upstream workflow detects new official `router-for-me/CLIProxyAPI` releases, merges and validates them, then publishes the new core without changing the manually selected Android app version.
- **Release Versions**: App and core versions remain independent. A release is displayed as `CLIProxyAPI Android 1.1.2 · Core 8.0.4` and uses the concise tag `1.1.2-core.8.0.4`. Android's internal version code still increases for every release. Each release includes separate signed `arm64-v8a` and `x86_64` APKs, `update.json`, and SHA-256 checksums.

### Updates and security

- The app checks this repository's latest GitHub Release on startup and through a daily Android background job.
- Update APKs must pass package name, version code, SHA-256, and signing-certificate verification before Android's installer is opened.
- Android still requires the user to approve installation. The app does not silently load downloaded executable code.
- See [Security Policy](SECURITY.md) and [Privacy](PRIVACY.md).

## Contributing and upstream

- This project was originally created for the maintainer's personal use. Feature decisions prioritize real-world needs and long-term maintainability.
- Android-specific issues and pull requests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) first, and please understand that not every feature request can be accepted.
- Never attach API keys, OAuth files, `config.yaml`, or the app's private data to a public issue.
- Core behavior originates from [router-for-me/CLIProxyAPI](https://github.com/router-for-me/CLIProxyAPI). Direct core-related issues and changes to the upstream project first, and see [NOTICE.md](NOTICE.md) for attribution.
