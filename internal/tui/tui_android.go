//go:build android

package tui

import (
	"errors"
	"io"

	log "github.com/sirupsen/logrus"
)

var errTUIDisabledOnAndroid = errors.New("terminal TUI mode is not supported on Android, please access via web or native UI")

// LogHook stub for Android logging
type LogHook struct{}

func NewLogHook(_ int) *LogHook {
	return &LogHook{}
}

func (h *LogHook) SetFormatter(_ log.Formatter) {}
func (h *LogHook) Levels() []log.Level          { return log.AllLevels }
func (h *LogHook) Fire(_ *log.Entry) error      { return nil }
func (h *LogHook) Chan() <-chan string          { return nil }

// Client stub for Android
type Client struct{}

func NewClient(_ int, _ string) *Client {
	return &Client{}
}

func (c *Client) SetSecretKey(_ string) {}
func (c *Client) GetConfig() (map[string]any, error) {
	return nil, errTUIDisabledOnAndroid
}
func (c *Client) GetConfigYAML() (string, error) {
	return "", errTUIDisabledOnAndroid
}

// Run stub for Android
func Run(_ int, _ string, _ *LogHook, _ io.Writer) error {
	return errTUIDisabledOnAndroid
}

// RunWithBaseURL stub for Android
func RunWithBaseURL(_ string, _ string, _ *LogHook, _ io.Writer) error {
	return errTUIDisabledOnAndroid
}
