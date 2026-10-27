package discovery

import "strings"

const (
	maxDiscoveredMetadataItems = 32
	maxMetadataItemBytes       = 64
)

func appendUniqueStrings(dst, src []string) []string {
	for _, value := range src {
		value = strings.TrimSpace(value)
		if value == "" || len(value) > maxMetadataItemBytes || len(dst) >= maxDiscoveredMetadataItems {
			continue
		}
		duplicate := false
		for _, existing := range dst {
			if existing == value {
				duplicate = true
				break
			}
		}
		if !duplicate {
			dst = append(dst, value)
		}
	}
	return dst
}

func parseTXTList(value string) []string {
	var result []string
	for len(value) > 0 && len(result) < maxDiscoveredMetadataItems {
		item := value
		if before, after, ok := strings.Cut(value, ","); ok {
			item = before
			value = after
		} else {
			value = ""
		}
		item = strings.TrimSpace(item)
		if item == "" || len(item) > maxMetadataItemBytes {
			continue
		}
		result = appendUniqueStrings(result, []string{item})
	}
	return result
}
