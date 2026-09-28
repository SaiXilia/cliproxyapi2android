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

func getOrInitManagementKey(configDir string) string {
	keyFile := filepath.Join(configDir, "management_key.txt")
	if data, err := os.ReadFile(keyFile); err == nil {
		if k := strings.TrimSpace(string(data)); k != "" {
			return k
		}
	}

	defaultKey := "admin8317"
	_ = os.WriteFile(keyFile, []byte(defaultKey), 0600)
	return defaultKey
}

func ensureInitialConfigFile(cfgPath, host string, port int, authDir string, mgmtKey string) error {
	if _, err := os.Stat(cfgPath); err == nil {
		return nil // 配置文件已存在，不覆盖
	}

	initialConfig := map[string]any{
		"host":     "0.0.0.0",
		"port":     port,
		"auth-dir": authDir,
		"api-keys": []string{}, // 默认免密模式，便于手机端浏览器与各类客户端开箱即用
		"remote-management": map[string]any{
			"allow-remote": true,
			"secret-key":   mgmtKey,
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
	if err := managementasset.EnsureDefaultManagementAsset(staticDir); err != nil {
		log.WithError(err).Warn("mobile start: failed to ensure default management asset")
	}
}

// 启动移动端代理服务。返回值: 1 成功受理启动, 0 已经在运行中, -1 参数或初始化失败
//
//export StartServer
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

	host := "0.0.0.0"
	if cHost != nil {
		if h := strings.TrimSpace(C.GoString(cHost)); h != "" {
			host = h
		}
	}

	serverStatus.Store(StatusStarting)

	// 确保管理密钥并注入环境变量与选项
	mgmtKey := getOrInitManagementKey(configDir)
	_ = os.Setenv("MANAGEMENT_PASSWORD", mgmtKey)

	// 1. 确保运行目录与初始配置落盘
	authDir := filepath.Join(configDir, "auths")
	if errAuthDir := os.MkdirAll(authDir, 0700); errAuthDir != nil {
		log.Errorf("mobile start: failed to create auths directory: %v", errAuthDir)
		serverStatus.Store(StatusFailed)
		return -1
	}

	cfgPath := filepath.Join(configDir, "config.yaml")
	if errInitCfg := ensureInitialConfigFile(cfgPath, host, targetPort, authDir, mgmtKey); errInitCfg != nil {
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
		WithConfigPath(cfgPath).
		WithLocalManagementPassword(mgmtKey)

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

// 停止服务并等待其完成资源回收
//
//export StopServer
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

// 返回当前服务运行状态码
//
//export GetServerStatus
func GetServerStatus() C.int {
	return C.int(serverStatus.Load())
}

// 供 Android Kotlin 协程在后台工作线程轮询拉取 OAuth 授权 URL
//
//export PollOAuthURL
func PollOAuthURL(timeoutMs C.int) *C.char {
	timeout := time.Duration(timeoutMs) * time.Millisecond
	select {
	case url := <-oauthChan:
		return C.CString(url)
	case <-time.After(timeout):
		return nil
	}
}

// 释放 CString 堆内存
//
//export FreeCString
func FreeCString(ptr *C.char) {
	if ptr != nil {
		C.free(unsafe.Pointer(ptr))
	}
}

func main() {}
