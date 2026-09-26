//go:build android

package discovery

import (
	"context"
	"errors"
	"net"
)

var errDiscoveryDisabledOnAndroid = errors.New("mDNS lan discovery is disabled on android")

// extractInterfaceIPs collects non-loopback IP addresses from the selected interfaces.
func extractInterfaceIPs(ifaces []net.Interface) []string {
	var ips []string
	for _, iface := range ifaces {
		addrs, err := iface.Addrs()
		if err != nil {
			continue
		}
		for _, addr := range addrs {
			var ip net.IP
			switch v := addr.(type) {
			case *net.IPNet:
				ip = v.IP
			case *net.IPAddr:
				ip = v.IP
			}
			if ip == nil || ip.IsLoopback() || ip.IsUnspecified() {
				continue
			}
			ips = append(ips, ip.String())
		}
	}
	return ips
}

// ZeroconfAdvertiser stub for Android: silent no-op, does not broadcast mDNS packets.
type ZeroconfAdvertiser struct{}

func NewZeroconfAdvertiser() *ZeroconfAdvertiser {
	return &ZeroconfAdvertiser{}
}

func (a *ZeroconfAdvertiser) Start(_ context.Context, _ ServiceSpec) error {
	// Silent no-op on Android to avoid waking up Wi-Fi radio for multicast
	return nil
}

func (a *ZeroconfAdvertiser) Stop() error {
	return nil
}

// ZeroconfBrowser stub for Android
type ZeroconfBrowser struct{}

func NewZeroconfBrowser(_ ...net.Interface) *ZeroconfBrowser {
	return &ZeroconfBrowser{}
}

func (b *ZeroconfBrowser) Browse(_ context.Context, _, _ string) ([]DiscoveredService, error) {
	return nil, errDiscoveryDisabledOnAndroid
}

func (b *ZeroconfBrowser) BrowseWithFallback(_ context.Context) ([]DiscoveredService, error) {
	return nil, errDiscoveryDisabledOnAndroid
}

func (b *ZeroconfBrowser) BrowseWithFallbackServiceType(_ context.Context, _ string) ([]DiscoveredService, error) {
	return nil, errDiscoveryDisabledOnAndroid
}
