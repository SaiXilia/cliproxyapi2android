#!/usr/bin/env bash
set -euo pipefail

# 1. 自动寻找或检查 Android NDK
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    if [ -d "C:/Android/Sdk/ndk/29.0.14206865" ]; then
        export ANDROID_NDK_HOME="C:/Android/Sdk/ndk/29.0.14206865"
    elif [ -d "$HOME/Android/Sdk/ndk" ]; then
        latest_ndk=$(ls -d "$HOME/Android/Sdk/ndk"/* 2>/dev/null | sort -V | tail -n 1 || true)
        export ANDROID_NDK_HOME="$latest_ndk"
    else
        echo "ERROR: ANDROID_NDK_HOME is not set!"
        echo "Please export ANDROID_NDK_HOME=/path/to/android-ndk"
        exit 1
    fi
fi

# 2. 自动检测宿主操作系统
HOST_OS="linux-x86_64"
EXE_SUFFIX=""
CMD_SUFFIX=""
if [[ "$OSTYPE" == "darwin"* ]]; then
    HOST_OS="darwin-x86_64"
elif [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" ]]; then
    HOST_OS="windows-x86_64"
    CMD_SUFFIX=".cmd"
fi

TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_OS"
API_LEVEL="26"

OUTPUT_DIR="./dist/android"
mkdir -p "$OUTPUT_DIR"

compile_target() {
    local ARCH="$1"        # arm64-v8a 或 x86_64
    local GOARCH="$2"      # arm64 或 amd64
    local TARGET_CC="$3"   # aarch64-linux-android 或 x86_64-linux-android

    echo "=========================================================="
    echo "  Compiling for Android $ARCH (GOARCH=$GOARCH, API=$API_LEVEL)..."
    echo "=========================================================="

    local CC="$TOOLCHAIN/bin/${TARGET_CC}${API_LEVEL}-clang${CMD_SUFFIX}"
    local CXX="$TOOLCHAIN/bin/${TARGET_CC}${API_LEVEL}-clang++${CMD_SUFFIX}"

    local TARGET_DIR="$OUTPUT_DIR/$ARCH"
    mkdir -p "$TARGET_DIR"

    # A. 编译独立 CLI 可执行二进制文件 (适合 Termux / ADB)
    echo "--> Building Standalone Executable (cli-proxy-api)..."
    CGO_ENABLED=1 \
    GOOS=android \
    GOARCH="$GOARCH" \
    CC="$CC" \
    CXX="$CXX" \
    go build \
        -buildmode=pie \
        -trimpath \
        -ldflags="-s -w -extldflags '-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384'" \
        -o "$TARGET_DIR/cli-proxy-api" \
        ./cmd/server

    # B. 编译 C-Shared 动态库 (适合内嵌 Android App)
    if [ -d "./cmd/mobile" ]; then
        echo "--> Building C-Shared Library (libcliproxy.so)..."
        CGO_ENABLED=1 \
        GOOS=android \
        GOARCH="$GOARCH" \
        CC="$CC" \
        CXX="$CXX" \
        go build -buildmode=c-shared \
            -trimpath \
            -ldflags="-s -w -extldflags '-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384'" \
            -o "$TARGET_DIR/libcliproxy.so" \
            ./cmd/mobile
        rm -f "$TARGET_DIR/libcliproxy.h"
    fi

    echo "Finished $ARCH successfully!"
    ls -lh "$TARGET_DIR"
}

# 默认构建真机主流架构 (arm64-v8a)
compile_target "arm64-v8a" "arm64" "aarch64-linux-android"

# 若设置了 BUILD_EMULATOR=1 则额外构建 PC 模拟器架构 (x86_64)
if [ "${BUILD_EMULATOR:-0}" == "1" ]; then
    compile_target "x86_64" "amd64" "x86_64-linux-android"
fi

echo "=========================================================="
echo "  All Android artifacts built successfully in $OUTPUT_DIR"
echo "=========================================================="
