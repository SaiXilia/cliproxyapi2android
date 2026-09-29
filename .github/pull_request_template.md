## 变更摘要 / Summary

请说明 Android 端的改动及其必要性。/ Describe the Android-specific change and why it is needed.

## 验证 / Verification

- [ ] `go build -o test-output ./cmd/server` 成功，并已删除 `test-output`。/ Succeeds and `test-output` is removed.
- [ ] 相关 Go 测试通过。/ Relevant Go tests pass.
- [ ] Android 改动通过 `./gradlew assembleDebug`。/ Android changes pass the build.
- [ ] 面向用户的中英文文本保持一致。/ User-facing Chinese and English text stay aligned.
- [ ] 不包含生成的二进制文件、本地配置、凭据、日志或签名材料。/ No generated binaries, local configuration, credentials, logs, or signing material are included.

## 上游影响 / Upstream impact

- [ ] 此改动仅针对 Android，或已考虑相应的上游影响。/ This is Android-specific, or the corresponding upstream impact has been considered.
