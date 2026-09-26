//go:build android

package live

import (
	"context"
	"errors"

	"github.com/router-for-me/CLIProxyAPI/v7/internal/config"
)

var errWebRTCDisabledOnAndroid = errors.New("codex live webrtc media relay is disabled on android")

type mediaRelaySession interface {
	AcceptUpstreamAnswer(context.Context, string) (string, error)
	SetCallID(string)
	SetCloseHandler(func(string))
	Close() error
	CloseWithReason(string) error
}

type mediaRelayFactory interface {
	NewSession(context.Context, string, mediaSessionRoute) (mediaRelaySession, string, error)
}

type mediaSessionRoute struct {
	proxyURL   string
	credential string
	authIndex  string
}

type mediaSessionLimiter struct{}

type androidMediaRelay struct{}

func (r *androidMediaRelay) NewSession(_ context.Context, _ string, _ mediaSessionRoute) (mediaRelaySession, string, error) {
	return nil, "", errWebRTCDisabledOnAndroid
}

type androidMediaSession struct{}

func (s *androidMediaSession) AcceptUpstreamAnswer(_ context.Context, _ string) (string, error) {
	return "", errWebRTCDisabledOnAndroid
}
func (s *androidMediaSession) SetCallID(_ string)             {}
func (s *androidMediaSession) SetCloseHandler(_ func(string)) {}
func (s *androidMediaSession) Close() error                   { return nil }
func (s *androidMediaSession) CloseWithReason(_ string) error { return nil }

func newPionMediaRelayWithLimiter(_ config.CodexLiveMediaRelayConfig, _ *mediaSessionLimiter) (*androidMediaRelay, error) {
	return &androidMediaRelay{}, nil
}
