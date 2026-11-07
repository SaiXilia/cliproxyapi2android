// Command boundaries maintains the Android exclusions around desktop implementations.
package main

import (
	"bytes"
	"errors"
	"flag"
	"fmt"
	"go/build"
	"go/build/constraint"
	"go/format"
	"io"
	"os"
	"path/filepath"
	"strings"
)

type boundary struct {
	directory string
	shared    string
	desktop   string
}

// A wildcard is safe only for packages whose implementation is entirely disabled.
// Mixed packages require review when upstream introduces an unfamiliar file family.
var boundaries = []boundary{
	{directory: "internal/browser", desktop: "*.go"},
	{directory: "internal/tui", desktop: "*.go"},
	{directory: "internal/store", desktop: "*.go"},
	{
		directory: "internal/home",
		shared: "certificate.go concurrency_release.go concurrency_release_test.go " +
			"in_flight_contract_test.go kv_helpers.go plugin_status.go plugin_status_test.go requests.go",
		desktop: "client*.go cluster*.go global.go kv_helpers_test.go",
	},
	{
		directory: "internal/discovery",
		shared: "id.go interfaces.go service.go service_review_test.go spec.go spec_review_test.go " +
			"txt_list.go txt_list_test.go types.go",
		desktop: "browse*.go zeroconf*.go discovery_test.go",
	},
	{
		directory: "internal/client/codex/live",
		shared: "capabilities.go capabilities_test.go client_secret.go client_secret_test.go " +
			"live.go live_test.go sideband.go websocket.go websocket_test.go",
		desktop: "media*.go tcp_proxy*.go",
	},
}

func main() {
	apply := flag.Bool("apply", false, "add Android exclusions to classified desktop files")
	packages := flag.Bool("packages", false, "list the packages covered by the boundary policy")
	normalize := flag.String("normalize-desktop", "", "normalize stdin for a classified desktop conflict path")
	root := flag.String("root", ".", "repository root")
	flag.Parse()
	if *normalize != "" {
		source, errRead := io.ReadAll(os.Stdin)
		if errRead == nil {
			var normalized []byte
			normalized, errRead = normalizeDesktop(*normalize, source)
			if errRead == nil {
				_, errRead = os.Stdout.Write(normalized)
			}
		}
		if errRead != nil {
			fmt.Fprintln(os.Stderr, errRead)
			os.Exit(1)
		}
		return
	}
	if *packages {
		for _, rule := range boundaries {
			fmt.Printf("./%s\n", rule.directory)
		}
		return
	}
	changed, errCheck := checkBoundaries(*root, *apply, boundaries)
	if errCheck != nil {
		fmt.Fprintln(os.Stderr, errCheck)
		os.Exit(1)
	}
	for _, path := range changed {
		fmt.Printf("Excluded desktop file from Android: %s\n", path)
	}
	fmt.Printf("Android source boundaries verified (%d packages)\n", len(boundaries))
}

func normalizeDesktop(path string, source []byte) ([]byte, error) {
	path = filepath.ToSlash(filepath.Clean(path))
	name := filepath.Base(path)
	for _, rule := range boundaries {
		_, android := androidFile(name)
		if filepath.ToSlash(filepath.Dir(path)) != rule.directory || android ||
			matches(name, rule.shared) || !matches(name, rule.desktop) {
			continue
		}
		updated, errExclude := excludeAndroid(source)
		if errExclude != nil {
			return nil, errExclude
		}
		return format.Source(updated)
	}
	return nil, fmt.Errorf("%s is not a classified desktop-only file", path)
}

func checkBoundaries(root string, apply bool, policy []boundary) ([]string, error) {
	patches := make(map[string][]byte)
	var changed []string
	var problems []error
	for _, rule := range policy {
		directory := filepath.Join(root, rule.directory)
		entries, errRead := os.ReadDir(directory)
		if errRead != nil {
			problems = append(problems, errRead)
			continue
		}
		shared := strings.Fields(rule.shared)
		for _, name := range shared {
			if _, errStat := os.Stat(filepath.Join(directory, name)); errStat != nil {
				problems = append(problems, fmt.Errorf("shared boundary file %s/%s: %w", rule.directory, name, errStat))
			}
		}
		for _, entry := range entries {
			name := entry.Name()
			if entry.IsDir() || !strings.HasSuffix(name, ".go") {
				continue
			}
			path := filepath.Join(directory, name)
			relative := filepath.ToSlash(filepath.Join(rule.directory, name))
			androidArch, android := androidFile(name)
			if android || matches(name, rule.shared) {
				for _, arch := range []string{"arm64", "amd64"} {
					if androidArch != "" && androidArch != arch {
						continue
					}
					ctx := build.Default
					ctx.GOOS, ctx.GOARCH, ctx.CgoEnabled = "android", arch, true
					included, errMatch := ctx.MatchFile(directory, name)
					if errMatch != nil || !included {
						problems = append(problems, fmt.Errorf("%s must remain available on android/%s (match error: %v)", relative, arch, errMatch))
					}
				}
				continue
			}
			if !matches(name, rule.desktop) {
				problems = append(problems, fmt.Errorf("unclassified Android boundary file %s: review whether it is shared or desktop-only", relative))
				continue
			}
			source, errSource := os.ReadFile(path)
			if errSource != nil {
				problems = append(problems, errSource)
				continue
			}
			updated, errExclude := excludeAndroid(source)
			if errExclude != nil {
				problems = append(problems, fmt.Errorf("%s: %w", relative, errExclude))
			} else if !bytes.Equal(source, updated) {
				if !apply {
					problems = append(problems, fmt.Errorf("desktop file %s lacks an Android exclusion; run go run ./scripts/android/boundaries -apply", relative))
				} else {
					patches[path] = updated
					changed = append(changed, relative)
				}
			}
		}
	}
	if errProblems := errors.Join(problems...); errProblems != nil {
		return nil, errProblems
	}
	for path, source := range patches {
		if errWrite := os.WriteFile(path, source, 0o644); errWrite != nil {
			return nil, fmt.Errorf("write Android boundary %s: %w", path, errWrite)
		}
	}
	return changed, nil
}

func androidFile(name string) (string, bool) {
	stem := strings.TrimSuffix(strings.TrimSuffix(name, ".go"), "_test")
	if strings.HasSuffix(stem, "_android") {
		return "", true
	}
	for _, arch := range []string{"arm64", "amd64", "arm", "386"} {
		if strings.HasSuffix(stem, "_android_"+arch) {
			return arch, true
		}
	}
	return "", false
}

func matches(name, patterns string) bool {
	for _, pattern := range strings.Fields(patterns) {
		if match, _ := filepath.Match(pattern, name); match {
			return true
		}
	}
	return false
}

func excludesAndroid(expr constraint.Expr) bool {
	switch value := expr.(type) {
	case *constraint.NotExpr:
		tag, ok := value.X.(*constraint.TagExpr)
		return ok && tag.Tag == "android"
	case *constraint.AndExpr:
		return excludesAndroid(value.X) || excludesAndroid(value.Y)
	case *constraint.OrExpr:
		return excludesAndroid(value.X) && excludesAndroid(value.Y)
	}
	return false
}

func excludeAndroid(source []byte) ([]byte, error) {
	// go/format promotes legacy +build lines and keeps both syntaxes consistent.
	formatted, errFormat := format.Source(source)
	if errFormat != nil {
		return nil, errFormat
	}
	lines := strings.Split(string(formatted), "\n")
	for index, line := range lines {
		if !constraint.IsGoBuild(line) {
			continue
		}
		expr, errParse := constraint.Parse(line)
		if errParse != nil {
			return nil, errParse
		}
		if excludesAndroid(expr) {
			return source, nil
		}
		expr = &constraint.AndExpr{X: &constraint.NotExpr{X: &constraint.TagExpr{Tag: "android"}}, Y: expr}
		lines[index] = "//go:build " + expr.String()
		return format.Source([]byte(strings.Join(lines, "\n")))
	}
	return format.Source(append([]byte("//go:build !android\n\n"), formatted...))
}
