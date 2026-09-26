//go:build android

package home

// SetCurrent on Android is a no-op to guarantee pure local memory fallback.
func SetCurrent(_ *Client) {}

// Current on Android always returns nil so CurrentKVClient() cleanly returns (nil, false, nil).
func Current() *Client {
	return nil
}

// ClearCurrent on Android is a safe no-op.
func ClearCurrent() {}

// ClearCurrentIf on Android is a safe no-op.
func ClearCurrentIf(_ *Client) {}
