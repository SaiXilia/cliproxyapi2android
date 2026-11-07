#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."
mode="${1:---fast}"
if [[ "$mode" != "--fast" && "$mode" != "--native" ]]; then
    echo "Usage: $0 [--fast|--native]" >&2
    exit 1
fi

go run ./scripts/android/boundaries
package_list="$(go run ./scripts/android/boundaries -packages)"
mapfile -t boundary_packages <<< "$package_list"

if [[ "$mode" == "--fast" ]]; then
    go test ./scripts/android/boundaries
    python3 -m unittest discover -s scripts/android -p 'test_*.py'
    go test "${boundary_packages[@]}"
    for arch in arm64 amd64; do
        CGO_ENABLED=0 GOOS=android GOARCH="$arch" go build "${boundary_packages[@]}"
    done
    exit 0
fi

: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME before native Android validation}"
host="linux-x86_64"
suffix=""
case "$OSTYPE" in
    darwin*) host="darwin-x86_64" ;;
    msys*|cygwin*) host="windows-x86_64"; suffix=".cmd" ;;
esac
toolchain="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$host/bin"
validation_root="$(cd "${RUNNER_TEMP:-${TMPDIR:-/tmp}}" && pwd -P)"
work="$(mktemp -d "$validation_root/android-boundaries.XXXXXX")"
cleanup() {
    case "$work" in
        "$validation_root"/android-boundaries.*) rm -rf -- "$work" ;;
        *) echo "Refusing to remove unexpected validation path: $work" >&2 ;;
    esac
}
trap cleanup EXIT

for arch in arm64 amd64; do
    target="aarch64-linux-android"
    [[ "$arch" != "amd64" ]] || target="x86_64-linux-android"
    (
        export GOOS=android GOARCH="$arch" CGO_ENABLED=1
        export CC="$toolchain/${target}26-clang$suffix"
        export CXX="$toolchain/${target}26-clang++$suffix"
        echo "Validating real CGO build and test boundaries for android/$arch"
        go list -deps -json ./cmd/server ./cmd/mobile > "$work/production-$arch.json"
        go list -deps -test -json "${boundary_packages[@]}" > "$work/tests-$arch.json"
        python3 scripts/android/check_deps.py "$work/production-$arch.json" "$work/tests-$arch.json"
        go build ./cmd/server ./cmd/mobile
        mkdir -p "$work/tests-$arch"
        # Cross-compile test binaries without executing Android code on the host.
        go test -c -o "$work/tests-$arch/" "${boundary_packages[@]}"
    )
done
