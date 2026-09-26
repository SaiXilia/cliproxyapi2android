//go:build android

package main

/*
#include <stdlib.h>
#include "jni_bridge.h"
*/
import "C"

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"sync/atomic"
	"time"
	"unsafe"

	"github.com/router-for-me/CLIProxyAPI/v7/internal/browser"
	"github.com/router-for-me/CLIProxyAPI/v7/internal/config"
	"github.com/router-for-me/CLIProxyAPI/v7/internal/managementasset"
	"github.com/router-for-me/CLIProxyAPI/v7/internal/misc"
	"github.com/router-for-me/CLIProxyAPI/v7/internal/registry"
	"github.com/router-for-me/CLIProxyAPI/v7/sdk/cliproxy"
	log "github.com/sirupsen/logrus"
	"gopkg.in/yaml.v3"
)

// Server status constants
const (
	StatusStopped  = 0
	StatusStarting = 1
	StatusRunning  = 2
	StatusStopping = 3
	StatusFailed   = 4
)

var (
	lifecycleMu   sync.Mutex
	serverStatus  atomic.Int32
	sessionCancel context.CancelFunc
	sessionDoneCh chan struct{}

	oauthChan = make(chan string, 32)
)

func init() {
	serverStatus.Store(StatusStopped)

	// 挂载移动端双轨 OAuth 拦截器
	browser.SetURLHandler(func(url string) error {
		select {
		case oauthChan <- url:
		default:
			select {
			case <-oauthChan:
			default:
			}
			oauthChan <- url
		}
		return nil
	})
}

func generateRandomHex(length int) string {
	b := make([]byte, length)
	if _, err := rand.Read(b); err != nil {
		return fmt.Sprintf("fallback-%d", time.Now().UnixNano())
	}
	return hex.EncodeToString(b)
}

func ensureInitialConfigFile(cfgPath, host string, port int, authDir string) error {
	if _, err := os.Stat(cfgPath); err == nil {
		return nil // 配置文件已存在，不覆盖
	}

	initialConfig := map[string]any{
		"host":     host,
		"port":     port,
		"auth-dir": authDir,
		"api-keys": []string{
			"cpa-" + generateRandomHex(16),
		},
		"management": map[string]any{
			"secret": "mgmt-" + generateRandomHex(16),
		},
	}

	data, err := yaml.Marshal(initialConfig)
	if err != nil {
		return fmt.Errorf("failed to marshal initial config: %w", err)
	}

	dir := filepath.Dir(cfgPath)
	if errDir := os.MkdirAll(dir, 0700); errDir != nil {
		return fmt.Errorf("failed to create config directory: %w", errDir)
	}

	tmpPath := cfgPath + ".tmp"
	if errWrite := os.WriteFile(tmpPath, data, 0600); errWrite != nil {
		return fmt.Errorf("failed to write initial config: %w", errWrite)
	}

	return os.Rename(tmpPath, cfgPath)
}

func ensureInitialManagementAsset(staticDir string) {
	mgmtPath := filepath.Join(staticDir, "management.html")
	if _, err := os.Stat(mgmtPath); err == nil {
		return // 静态页面已存在（无论是之前内置的还是远端热拉取的）
	}
	_ = os.MkdirAll(staticDir, 0700)
	fallbackHTML := `<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>CLIProxy API 控制台 (离线就绪)</title>
    <style>
        body { font-family: system-ui, -apple-system, sans-serif; background: #0f172a; color: #f8fafc; margin: 0; padding: 24px; }
        .card { background: #1e293b; border-radius: 12px; padding: 20px; border: 1px solid #334155; max-width: 600px; margin: 0 auto; }
        h1 { font-size: 20px; margin-top: 0; color: #38bdf8; }
        p { color: #94a3b8; font-size: 14px; line-height: 1.6; }
        .badge { display: inline-block; padding: 4px 10px; border-radius: 6px; font-size: 12px; font-weight: bold; background: #065f46; color: #34d399; }
        .endpoint { background: #0f172a; padding: 12px; border-radius: 8px; font-family: monospace; font-size: 13px; color: #e2e8f0; margin: 12px 0; word-break: break-all; }
        button { background: #2563eb; color: white; border: none; padding: 10px 18px; border-radius: 8px; font-size: 14px; font-weight: bold; cursor: pointer; }
    </style>
</head>
<body>
    <div class="card">
        <h1>CLIProxy API 控制台</h1>
        <p><span class="badge">本地代理运行正常</span></p>
        <p>服务已成功启动并就绪。当手机接入互联网时，后台更新器将自动从官方远端同步最新版完整管理控制台单页。</p>
        <div class="endpoint">健康检查端点: <a href="/healthz" style="color:#38bdf8;">/healthz</a></div>
        <div class="endpoint">聚合模型列表: <a href="/v1/models" style="color:#38bdf8;">/v1/models</a></div>
        <button onclick="location.reload()">刷新页面</button>
    </div>
</body>
</html>`
	_ = os.WriteFile(mgmtPath, []byte(fallbackHTML), 0644)
}

//export StartServer
// 启动移动端代理服务。返回值: 1 成功受理启动, 0 已经在运行中, -1 参数或初始化失败
func StartServer(cConfigDir *C.char, cHost *C.char, port C.int) C.int {
	lifecycleMu.Lock()
	defer lifecycleMu.Unlock()

	current := serverStatus.Load()
	if current == StatusStarting || current == StatusRunning {
		return 0 // 已处于活跃状态
	}

	if cConfigDir == nil {
		log.Error("mobile start: config directory cannot be null")
		return -1
	}

	configDir := strings.TrimSpace(C.GoString(cConfigDir))
	if configDir == "" {
		log.Error("mobile start: config directory is empty")
		return -1
	}

	targetPort := int(port)
	if targetPort <= 0 || targetPort > 65535 {
		targetPort = 8317
	}

	host := "127.0.0.1"
	if cHost != nil {
		if h := strings.TrimSpace(C.GoString(cHost)); h != "" {
			host = h
		}
	}

	serverStatus.Store(StatusStarting)

	// 1. 确保运行目录与初始配置落盘
	authDir := filepath.Join(configDir, "auths")
	if errAuthDir := os.MkdirAll(authDir, 0700); errAuthDir != nil {
		log.Errorf("mobile start: failed to create auths directory: %v", errAuthDir)
		serverStatus.Store(StatusFailed)
		return -1
	}

	cfgPath := filepath.Join(configDir, "config.yaml")
	if errInitCfg := ensureInitialConfigFile(cfgPath, host, targetPort, authDir); errInitCfg != nil {
		log.Errorf("mobile start: failed to ensure initial config: %v", errInitCfg)
		serverStatus.Store(StatusFailed)
		return -1
	}
	ensureInitialManagementAsset(filepath.Join(configDir, "static"))

	// 2. 加载配置对象并强制移动端策略
	cfg, errLoad := config.LoadConfigOptional(cfgPath, false)
	if errLoad != nil || cfg == nil {
		cfg = &config.Config{
			CredentialInFlight: config.DefaultCredentialInFlightConfig(),
		}
	}

	cfg.Host = host
	cfg.Port = targetPort
	cfg.AuthDir = authDir
	cfg.Home = config.HomeConfig{} // 强制清空分布式 Home 配置，确保纯本地单机模式
	cfg.NormalizePluginsConfig()

	// 3. 构建核心服务
	builder := cliproxy.NewBuilder().
		WithConfig(cfg).
		WithConfigPath(cfgPath)

	svc, errBuild := builder.Build()
	if errBuild != nil {
		log.Errorf("mobile start: failed to build cliproxy service: %v", errBuild)
		serverStatus.Store(StatusFailed)
		return -1
	}

	sessionCtx, cancel := context.WithCancel(context.Background())
	doneCh := make(chan struct{})

	sessionCancel = cancel
	sessionDoneCh = doneCh

	// 4. 显式启动后台自更新器 (由本运行会话 context 托管)
	managementasset.StartAutoUpdater(sessionCtx, cfgPath)
	misc.StartAntigravityVersionUpdater(sessionCtx)
	registry.StartModelsUpdater(sessionCtx)
	registry.StartCodexClientModelsUpdater(sessionCtx)
	registry.StartDevinModelsUpdater(sessionCtx)

	// 5. 启动服务主运行协程
	go func() {
		defer close(doneCh)
		serverStatus.Store(StatusRunning)
		log.Infof("CLIProxyAPI mobile service running at http://%s:%d", host, targetPort)

		if errRun := svc.Run(sessionCtx); errRun != nil && !errors.Is(errRun, context.Canceled) {
			log.Errorf("CLIProxyAPI mobile service run exited with error: %v", errRun)
		}
		serverStatus.Store(StatusStopped)
		log.Info("CLIProxyAPI mobile service stopped")
	}()

	return 1
}

//export StopServer
// 停止服务并等待其完成资源回收
func StopServer() {
	lifecycleMu.Lock()
	cancel := sessionCancel
	doneCh := sessionDoneCh
	sessionCancel = nil
	sessionDoneCh = nil
	current := serverStatus.Load()
	if current == StatusRunning || current == StatusStarting {
		serverStatus.Store(StatusStopping)
	}
	lifecycleMu.Unlock()

	if cancel != nil {
		cancel()
	}

	if doneCh != nil {
		select {
		case <-doneCh:
		case <-time.After(5 * time.Second):
			log.Warn("mobile stop: service shutdown wait timed out after 5s")
		}
	}

	serverStatus.Store(StatusStopped)
}

//export GetServerStatus
// 返回当前服务运行状态码
func GetServerStatus() C.int {
	return C.int(serverStatus.Load())
}

//export PollOAuthURL
// 供 Android Kotlin 协程在后台工作线程轮询拉取 OAuth 授权 URL
func PollOAuthURL(timeoutMs C.int) *C.char {
	timeout := time.Duration(timeoutMs) * time.Millisecond
	select {
	case url := <-oauthChan:
		return C.CString(url)
	case <-time.After(timeout):
		return nil
	}
}

//export FreeCString
// 释放 CString 堆内存
func FreeCString(ptr *C.char) {
	if ptr != nil {
		C.free(unsafe.Pointer(ptr))
	}
}

func main() {}
