package discovery

import (
	"fmt"
	"reflect"
	"strings"
	"testing"
)

func TestParseTXTListNormalizesMetadata(t *testing.T) {
	input := " api_key, ,oauth,api_key," + strings.Repeat("x", maxMetadataItemBytes+1) + ", custom "
	want := []string{"api_key", "oauth", "custom"}
	if got := parseTXTList(input); !reflect.DeepEqual(got, want) {
		t.Fatalf("parseTXTList() = %v, want %v", got, want)
	}
}

func TestParseTXTListBoundsMetadata(t *testing.T) {
	values := make([]string, maxDiscoveredMetadataItems+1)
	for i := range values {
		values[i] = fmt.Sprintf("method-%02d", i)
	}
	if got := parseTXTList(strings.Join(values, ",")); !reflect.DeepEqual(got, values[:maxDiscoveredMetadataItems]) {
		t.Fatalf("parseTXTList() = %v, want first %d methods", got, maxDiscoveredMetadataItems)
	}
}
