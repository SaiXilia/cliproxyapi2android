# Contributing

Thanks for helping improve CLIProxyAPI for Android.

## Before opening an issue

- Search existing issues and confirm the problem occurs on the latest signed release.
- For Android-specific bugs, include the app version, core version, Android version, device architecture, and reproducible steps.
- For behavior inherited from CLIProxyAPI, check the [upstream project](https://github.com/router-for-me/CLIProxyAPI) first.
- Never post API keys, OAuth credentials, `config.yaml`, files from `auths/`, signing material, or unredacted logs.

Security vulnerabilities must follow [SECURITY.md](SECURITY.md) instead of a public issue.

## Pull requests

1. Keep Android-specific changes focused and preserve upstream compatibility.
2. Use English for code comments and new general documentation. Update `README_CN.md` when user-facing behavior changes.
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
