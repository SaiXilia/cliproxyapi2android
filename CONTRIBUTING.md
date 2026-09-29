# 参与贡献

简体中文 | [English](#contributing)

感谢你帮助改进 CLIProxyAPI for Android。

## 提交 Issue 前

- 搜索现有 Issue，并确认问题可以在最新签名版本中复现。
- 反馈 Android 端问题时，请提供应用版本、Core 版本、Android 版本、设备架构和可复现步骤。
- 如果问题继承自 CLIProxyAPI，请先查看[上游项目](https://github.com/router-for-me/CLIProxyAPI)。
- 请勿公开 API Key、OAuth 凭据、`config.yaml`、`auths/` 中的文件、签名材料或未经脱敏的日志。

安全漏洞请按照 [SECURITY.md](SECURITY.md) 私下报告，不要提交公开 Issue。

## Pull Request

1. 请让 Android 端改动保持专注，并维持与上游的兼容性。
2. 代码注释和新增通用文档使用英文；涉及用户可见行为时，请同步更新中文 `README.md` 与英文 `README_EN.md`。
3. 运行相关测试以及必需的服务端编译检查：

   ```bash
   go build -o test-output ./cmd/server
   rm test-output
   ```

4. 修改 Android 端时，还应运行：

   ```bash
   cd android
   ./gradlew assembleDebug
   ```

5. 请勿提交生成的 APK、原生库、本地配置、认证文件或签名密钥。

来自 Fork 的 Pull Request 只会运行只读测试构建，不会获得发布签名 Secrets。

---

# Contributing

[简体中文](#参与贡献) | English

Thanks for helping improve CLIProxyAPI for Android.

## Before opening an issue

- Search existing issues and confirm the problem occurs on the latest signed release.
- For Android-specific bugs, include the app version, core version, Android version, device architecture, and reproducible steps.
- For behavior inherited from CLIProxyAPI, check the [upstream project](https://github.com/router-for-me/CLIProxyAPI) first.
- Never post API keys, OAuth credentials, `config.yaml`, files from `auths/`, signing material, or unredacted logs.

Security vulnerabilities must follow [SECURITY.md](SECURITY.md) instead of a public issue.

## Pull requests

1. Keep Android-specific changes focused and preserve upstream compatibility.
2. Use English for code comments and new general documentation. Keep the default Chinese `README.md` and English `README_EN.md` synchronized when user-facing behavior changes.
3. Run the relevant tests and the required server compile check:

   ```bash
   go build -o test-output ./cmd/server
   rm test-output
   ```

4. For Android changes, also run:

   ```bash
   cd android
   ./gradlew assembleDebug
   ```

5. Do not commit generated APKs, native libraries, local configuration, authentication files, or signing keys.

Pull requests from forks run only the read-only test build and never receive release signing secrets.
