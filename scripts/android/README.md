# Android build boundaries

`boundaries/main.go` defines the platform policy for browser integration, TUI,
remote storage, Home, mDNS discovery, and Codex Live media. Android stubs and
reviewed shared files must remain available on both arm64 and x86_64.

Browser, TUI, and remote storage implementations are disabled as entire packages;
new desktop helpers and tests in those packages receive `!android` automatically.
Mixed packages classify known desktop file families and list shared files
explicitly. An unfamiliar file stops synchronization for review instead of
silently removing potentially shared functionality.

```bash
go run ./scripts/android/boundaries          # Read-only policy check
go run ./scripts/android/boundaries -apply   # Apply reviewed desktop exclusions
bash scripts/android/validate_android.sh --fast
ANDROID_NDK_HOME=/path/to/ndk bash scripts/android/validate_android.sh --native
```

The normalizer combines existing build expressions with `!android`, preserving
desktop restrictions and legacy `+build` lines. An existing `linux` constraint
alone is insufficient because Android also satisfies the Linux build tag.
The conflict resolver can accept upstream content only when the local side
differs from the merge base by formatting or a managed desktop exclusion. Real
local code changes, shared files, and Android stubs retain conflict protection.
The policy tool and conflict resolver are preserved before the upstream merge.

The fast check validates the policy, runs tooling regressions and desktop tests
for the boundary packages, and compiles those packages for both Android
architectures without CGO. It runs on every synchronization, even when no new
release is available.

The native check uses the actual NDK/CGO configuration for each architecture. It
audits production dependencies for both `cmd/server` and `cmd/mobile`, audits
boundary test dependencies, compiles both entry points, and cross-compiles the
boundary test binaries. Android test binaries are not executed on the host.
Empty, malformed, incomplete, or forbidden dependency graphs fail validation.
The same checks run before source publication, in Android PR CI, and before
release signing. Existing APK builds still perform final native linking and
packaging.

When upstream adds a shared file to a mixed package, review its dependencies and
stub compatibility, then update the shared list in `boundaries/main.go`. Do not
resolve a failed check by deleting shared functionality or weakening the
dependency audit.
