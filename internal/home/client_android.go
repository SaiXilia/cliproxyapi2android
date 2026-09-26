//go:build android

package home

import (
	"context"
	"errors"
	"net/http"
	"net/url"
	"time"

	"github.com/router-for-me/CLIProxyAPI/v7/internal/config"
	"github.com/router-for-me/CLIProxyAPI/v7/sdk/pluginstore"
)

var (
	ErrDisabled                  = errors.New("home client disabled")
	ErrNotConnected              = errors.New("home not connected")
	ErrEmptyResponse             = errors.New("home returned empty response")
	ErrAuthNotFound              = errors.New("home auth not found")
	ErrConfigNotFound            = errors.New("home config not found")
	ErrModelsNotFound            = errors.New("home models not found")
	ErrPluginSyncUnsupported     = errors.New("home plugin sync is unsupported")
	ErrDispatchFenced            = errors.New("home auth dispatch is fenced")
	ErrCompareAndSwapUnsupported = errors.New("home compare-and-swap is unsupported")
)

type DispatchError struct {
	Err       error
	Ambiguous bool
}

func (e *DispatchError) Error() string {
	if e == nil || e.Err == nil {
		return "home auth dispatch failed"
	}
	return e.Err.Error()
}

func (e *DispatchError) Unwrap() error {
	if e == nil {
		return nil
	}
	return e.Err
}

func NewAmbiguousDispatchError(err error) error {
	if err == nil {
		return nil
	}
	return &DispatchError{Err: err, Ambiguous: true}
}

type PluginTask struct {
	ID             uint      `json:"id"`
	Operation      string    `json:"operation"`
	PluginID       string    `json:"plugin_id"`
	TargetNodeType string    `json:"target_node_type,omitempty"`
	TargetNodeID   string    `json:"target_node_id,omitempty"`
	CreatedAt      time.Time `json:"created_at"`
	UpdatedAt      time.Time `json:"updated_at"`
}

type KVSetOptions struct {
	EX time.Duration
	PX time.Duration
	NX bool
	XX bool
}

type Client struct{}

func New(_ config.HomeConfig) *Client {
	return &Client{}
}

func (c *Client) Enabled() bool                                      { return false }
func (c *Client) HeartbeatOK() bool                                  { return false }
func (c *Client) Close()                                             {}
func (c *Client) NewLifetime() *Client                               { return c }
func (c *Client) SetManagedLifetime(_ bool)                          {}
func (c *Client) MembershipInstanceID() string                       { return "" }
func (c *Client) LegacyMembership() bool                             { return false }
func (c *Client) EnableLegacyMembership()                            {}
func (c *Client) AbortAmbiguousDispatch()                            {}
func (c *Client) AmbiguousDispatch() bool                            { return false }
func (c *Client) SuppressTakeover()                                  {}
func (c *Client) Ping(_ context.Context) error                       { return ErrDisabled }
func (c *Client) GetConfig(_ context.Context) ([]byte, error)        { return nil, ErrDisabled }
func (c *Client) GetModels(_ context.Context, _ http.Header, _ url.Values) ([]byte, error) {
	return nil, ErrDisabled
}
func (c *Client) KVGet(_ context.Context, _ string) ([]byte, bool, error) {
	return nil, false, ErrDisabled
}
func (c *Client) KVSet(_ context.Context, _ string, _ []byte, _ KVSetOptions) (bool, error) {
	return false, ErrDisabled
}
func (c *Client) KVSetNX(_ context.Context, _ string, _ []byte, _ time.Duration) (bool, error) {
	return false, ErrDisabled
}
func (c *Client) KVCompareAndSwap(_ context.Context, _ string, _ []byte, _ bool, _ []byte, _ time.Duration) (bool, error) {
	return false, ErrDisabled
}
func (c *Client) KVDel(_ context.Context, _ ...string) (int64, error) {
	return 0, ErrDisabled
}
func (c *Client) KVExpire(_ context.Context, _ string, _ time.Duration) (bool, error) {
	return false, ErrDisabled
}
func (c *Client) KVTTL(_ context.Context, _ string) (time.Duration, bool, error) {
	return 0, false, ErrDisabled
}
func (c *Client) KVIncrBy(_ context.Context, _ string, _ int64) (int64, error) {
	return 0, ErrDisabled
}
func (c *Client) KVMGet(_ context.Context, _ ...string) ([][]byte, []bool, error) {
	return nil, nil, ErrDisabled
}
func (c *Client) KVMSet(_ context.Context, _ map[string][]byte) error {
	return ErrDisabled
}
func (c *Client) RPopAuth(_ context.Context, _, _ string, _ http.Header, _ int) ([]byte, error) {
	return nil, ErrDisabled
}
func (c *Client) RPopAuthWithPolicy(_ context.Context, _, _ string, _ http.Header, _ int, _ string) ([]byte, error) {
	return nil, ErrDisabled
}
func (c *Client) RPopAuthWithConstraints(_ context.Context, _, _ string, _ http.Header, _ int, _ []string, _ string) ([]byte, error) {
	return nil, ErrDisabled
}
func (c *Client) RPopAuthWithPolicyAndConstraints(_ context.Context, _, _ string, _ http.Header, _ int, _ string, _ []string, _ string) ([]byte, error) {
	return nil, ErrDisabled
}
func (c *Client) RPopAuthWithRetryRoundConstraints(_ context.Context, _, _ string, _ http.Header, _ int, _ int, _ []string, _ string) ([]byte, error) {
	return nil, ErrDisabled
}
func (c *Client) RPopAuthWithPolicyAndRetryRoundConstraints(_ context.Context, _, _ string, _ http.Header, _ int, _ string, _ int, _ []string, _ string) ([]byte, error) {
	return nil, ErrDisabled
}
func (c *Client) RPopAuthWithSessionHierarchy(_ context.Context, _, _ string, _ string, _ http.Header, _ int, _ string, _ *int, _ []string, _ string) ([]byte, error) {
	return nil, ErrDisabled
}
func (c *Client) GetRefreshAuth(_ context.Context, _, _ string) ([]byte, error) {
	return nil, ErrDisabled
}
func (c *Client) LPushUsage(_ context.Context, _ []byte) error {
	return ErrDisabled
}
func (c *Client) LPushInFlightSnapshot(_ context.Context, _ []byte) error {
	return ErrDisabled
}
func (c *Client) PushConcurrencyRelease(_ context.Context, _ ConcurrencyReleaseFrame) error {
	return ErrDisabled
}
func (c *Client) RPushRequestLog(_ context.Context, _ []byte) error {
	return ErrDisabled
}
func (c *Client) RPushAppLog(_ context.Context, _ []byte) error {
	return ErrDisabled
}
func (c *Client) RPushPluginStatus(_ context.Context, _ []byte) error {
	return ErrDisabled
}
func (c *Client) GetPluginTasks(_ context.Context) ([]PluginTask, error) {
	return nil, ErrDisabled
}
func (c *Client) GetPluginSync(_ context.Context, _ pluginstore.PluginSyncRequest) (pluginstore.PluginSyncResponse, error) {
	return pluginstore.PluginSyncResponse{}, ErrDisabled
}
func (c *Client) SetLifecycleConfig(_ config.CredentialConcurrencyConfig) error {
	return ErrDisabled
}
func (c *Client) LimiterConfig() config.CredentialConcurrencyConfig {
	return config.CredentialConcurrencyConfig{}
}
func (c *Client) RunConfigSubscriberLifetime(_ context.Context, _ func([]byte) error, _ func()) error {
	return ErrDisabled
}
func (c *Client) StartConfigSubscriber(_ context.Context, _ func([]byte) error) {}
