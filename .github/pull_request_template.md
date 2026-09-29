## Summary

Describe the Android-specific change and why it is needed.

## Verification

- [ ] `go build -o test-output ./cmd/server` succeeds and `test-output` is removed.
- [ ] Relevant Go tests pass.
- [ ] `./gradlew assembleDebug` passes for Android changes.
- [ ] User-facing English and Chinese text stay aligned.
- [ ] No generated binaries, local configuration, credentials, logs, or signing material are included.

## Upstream impact

- [ ] This is Android-specific, or the corresponding upstream impact has been considered.
