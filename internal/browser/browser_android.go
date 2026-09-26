//go:build android

package browser

import (
	"fmt"
	"runtime"
	"sync"
)

var (
	urlHandlerMu sync.RWMutex
	urlHandler   func(string) error
)

// SetURLHandler registers a custom URL opening callback (e.g. Kotlin Custom Tabs via JNI).
func SetURLHandler(handler func(string) error) {
	urlHandlerMu.Lock()
	defer urlHandlerMu.Unlock()
	urlHandler = handler
}

// OpenURL handles URL opening on Android via dual-track:
// Track A: invokes registered host callback (e.g., Android Chrome Custom Tabs).
// Track B: prints prominent link to stdout for Termux / CLI users.
func OpenURL(url string) error {
	urlHandlerMu.RLock()
	handler := urlHandler
	urlHandlerMu.RUnlock()

	if handler != nil {
		if err := handler(url); err == nil {
			return nil
		}
	}

	// CLI / Termux fallback
	fmt.Printf("\n=======================================================\n")
	fmt.Printf(" [OAuth Authorization]\n")
	fmt.Printf(" Please open the following URL in your browser to authorize:\n")
	fmt.Printf(" %s\n", url)
	fmt.Printf("=======================================================\n\n")
	return nil
}

// IsAvailable indicates browser opening capability is supported.
func IsAvailable() bool {
	return true
}

// GetPlatformInfo returns Android browser support metadata.
func GetPlatformInfo() map[string]interface{} {
	return map[string]interface{}{
		"os":        runtime.GOOS,
		"arch":      runtime.GOARCH,
		"available": true,
	}
}
