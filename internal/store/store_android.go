//go:build android

package store

import (
	"context"
	"errors"

	cliproxyauth "github.com/router-for-me/CLIProxyAPI/v8/sdk/cliproxy/auth"
)

var errRemoteStoreDisabledOnAndroid = errors.New("enterprise remote storage is disabled on android")

// PostgresStoreConfig captures configuration required to initialize a Postgres-backed store.
type PostgresStoreConfig struct {
	DSN           string
	Schema        string
	ConfigTable   string
	AuthTable     string
	CooldownTable string
	SpoolDir      string
}

// PostgresStore stub for Android
type PostgresStore struct{}

func NewPostgresStore(_ context.Context, _ PostgresStoreConfig) (*PostgresStore, error) {
	return nil, errRemoteStoreDisabledOnAndroid
}

func (s *PostgresStore) Close() error                         { return nil }
func (s *PostgresStore) EnsureSchema(_ context.Context) error { return errRemoteStoreDisabledOnAndroid }
func (s *PostgresStore) Bootstrap(_ context.Context, _ string) error {
	return errRemoteStoreDisabledOnAndroid
}
func (s *PostgresStore) ConfigPath() string { return "" }
func (s *PostgresStore) AuthDir() string    { return "" }
func (s *PostgresStore) WorkDir() string    { return "" }
func (s *PostgresStore) SetBaseDir(string)  {}
func (s *PostgresStore) Save(_ context.Context, _ *cliproxyauth.Auth) (string, error) {
	return "", errRemoteStoreDisabledOnAndroid
}
func (s *PostgresStore) List(_ context.Context) ([]*cliproxyauth.Auth, error) {
	return nil, errRemoteStoreDisabledOnAndroid
}
func (s *PostgresStore) Delete(_ context.Context, _ string) error {
	return errRemoteStoreDisabledOnAndroid
}
func (s *PostgresStore) PersistAuthFiles(_ context.Context, _ string, _ ...string) error {
	return errRemoteStoreDisabledOnAndroid
}
func (s *PostgresStore) PersistConfig(_ context.Context) error {
	return errRemoteStoreDisabledOnAndroid
}

// ObjectStoreConfig captures configuration required to initialize an Object-backed store.
type ObjectStoreConfig struct {
	Endpoint  string
	Bucket    string
	AccessKey string
	SecretKey string
	LocalRoot string
	UseSSL    bool
	PathStyle bool
}

// ObjectTokenStore stub for Android
type ObjectTokenStore struct{}

func NewObjectTokenStore(_ ObjectStoreConfig) (*ObjectTokenStore, error) {
	return nil, errRemoteStoreDisabledOnAndroid
}

func (s *ObjectTokenStore) SetBaseDir(string)  {}
func (s *ObjectTokenStore) ConfigPath() string { return "" }
func (s *ObjectTokenStore) AuthDir() string    { return "" }
func (s *ObjectTokenStore) Bootstrap(_ context.Context, _ string) error {
	return errRemoteStoreDisabledOnAndroid
}
func (s *ObjectTokenStore) Save(_ context.Context, _ *cliproxyauth.Auth) (string, error) {
	return "", errRemoteStoreDisabledOnAndroid
}
func (s *ObjectTokenStore) List(_ context.Context) ([]*cliproxyauth.Auth, error) {
	return nil, errRemoteStoreDisabledOnAndroid
}
func (s *ObjectTokenStore) Delete(_ context.Context, _ string) error {
	return errRemoteStoreDisabledOnAndroid
}
func (s *ObjectTokenStore) PersistAuthFiles(_ context.Context, _ string, _ ...string) error {
	return errRemoteStoreDisabledOnAndroid
}
func (s *ObjectTokenStore) PersistConfig(_ context.Context) error {
	return errRemoteStoreDisabledOnAndroid
}

// GitTokenStore stub for Android
type GitTokenStore struct{}

func NewGitTokenStore(_, _, _, _ string) *GitTokenStore {
	return &GitTokenStore{}
}

func (s *GitTokenStore) SetBaseDir(_ string)     {}
func (s *GitTokenStore) AuthDir() string         { return "" }
func (s *GitTokenStore) ConfigPath() string      { return "" }
func (s *GitTokenStore) EnsureRepository() error { return errRemoteStoreDisabledOnAndroid }
func (s *GitTokenStore) Save(_ context.Context, _ *cliproxyauth.Auth) (string, error) {
	return "", errRemoteStoreDisabledOnAndroid
}
func (s *GitTokenStore) List(_ context.Context) ([]*cliproxyauth.Auth, error) {
	return nil, errRemoteStoreDisabledOnAndroid
}
func (s *GitTokenStore) Delete(_ context.Context, _ string) error {
	return errRemoteStoreDisabledOnAndroid
}
func (s *GitTokenStore) PersistAuthFiles(_ context.Context, _ string, _ ...string) error {
	return errRemoteStoreDisabledOnAndroid
}
func (s *GitTokenStore) PersistConfig(_ context.Context) error {
	return errRemoteStoreDisabledOnAndroid
}
