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

	"github.com/router-for-me/CLIProxyAPI/v8/internal/browser"
	"github.com/router-for-me/CLIProxyAPI/v8/internal/config"
	"github.com/router-for-me/CLIProxyAPI/v8/internal/managementasset"
	"github.com/router-for-me/CLIProxyAPI/v8/internal/misc"
	"github.com/router-for-me/CLIProxyAPI/v8/internal/registry"
	"github.com/router-for-me/CLIProxyAPI/v8/sdk/cliproxy"
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

	// Install the mobile dual-path OAuth interceptor.
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

	generatedKey := "cpa-mgmt-" + generateRandomHex(16)
	_ = os.WriteFile(keyFile, []byte(generatedKey), 0600)
	return generatedKey
}

func ensureInitialConfigFile(cfgPath, host string, port int, authDir string, mgmtKey string) error {
	if _, err := os.Stat(cfgPath); err == nil {
		return nil // Preserve an existing configuration file.
	}

	initialConfig := map[string]any{
		"host":     host,
		"port":     port,
		"auth-dir": authDir,
		"api-keys": []string{}, // Default to keyless access for mobile browsers and clients.
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

// StartServer starts the mobile proxy. It returns 1 when accepted, 0 when already active, and -1 on invalid input or initialization failure.
//
//export StartServer
func StartServer(cConfigDir *C.char, cHost *C.char, port C.int) C.int {
	lifecycleMu.Lock()
	defer lifecycleMu.Unlock()

	current := serverStatus.Load()
	if current == StatusStarting || current == StatusRunning {
		return 0 // The server is already active.
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

	// Ensure the management key and expose it to the runtime configuration.
	mgmtKey := getOrInitManagementKey(configDir)
	_ = os.Setenv("MANAGEMENT_PASSWORD", mgmtKey)

	// 1. Ensure runtime directories and the initial configuration exist.
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

	// 2. Load the configuration and apply mobile runtime policy.
	cfg, errLoad := config.LoadConfigOptional(cfgPath, false)
	if errLoad != nil || cfg == nil {
		cfg = &config.Config{
			CredentialInFlight: config.DefaultCredentialInFlightConfig(),
		}
	}

	cfg.Host = host
	cfg.Port = targetPort
	cfg.AuthDir = authDir
	cfg.Home = config.HomeConfig{} // Disable distributed Home configuration for local standalone mode.
	cfg.NormalizePluginsConfig()

	// 3. Build the core service.
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

	// 4. Start background updaters under the current session context.
	managementasset.StartAutoUpdater(sessionCtx, cfgPath)
	misc.StartAntigravityVersionUpdater(sessionCtx)
	registry.StartModelsUpdater(sessionCtx)
	registry.StartCodexClientModelsUpdater(sessionCtx)
	registry.StartDevinModelsUpdater(sessionCtx)

	// 5. Start the main service goroutine.
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

// StopServer stops the service and waits for resource cleanup.
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

// GetServerStatus returns the current service status code.
//
//export GetServerStatus
func GetServerStatus() C.int {
	return C.int(serverStatus.Load())
}

// PollOAuthURL lets the Android coroutine poll OAuth authorization URLs on a background thread.
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

// FreeCString releases heap memory owned by a CString.
//
//export FreeCString
func FreeCString(ptr *C.char) {
	if ptr != nil {
		C.free(unsafe.Pointer(ptr))
	}
}

func main() {}
