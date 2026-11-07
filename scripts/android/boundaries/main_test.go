package main

import (
	"bytes"
	"go/build"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestExcludeAndroidPreservesDesktopConstraints(t *testing.T) {
	for _, header := range []string{
		"",
		"//go:build linux\n\n",
		"//go:build linux || darwin\n\n",
		"//go:build android || linux\n\n",
		"//go:build !android || linux\n\n",
		"// +build linux darwin\n\n",
		"//go:build linux && cgo\n// +build linux,cgo\n\n",
	} {
		t.Run(strings.TrimSpace(header), func(t *testing.T) {
			source := []byte(header + "package fixture\n\nfunc value() int { return 42 }\n")
			updated, errExclude := excludeAndroid(source)
			if errExclude != nil {
				t.Fatal(errExclude)
			}
			root := t.TempDir()
			path := filepath.Join(root, "fixture.go")
			for _, goos := range []string{"android", "linux", "darwin", "windows"} {
				for _, cgo := range []bool{false, true} {
					ctx := build.Default
					ctx.GOOS, ctx.GOARCH, ctx.CgoEnabled = goos, "arm64", cgo
					writeFixture(t, path, source)
					before, errBefore := ctx.MatchFile(root, "fixture.go")
					writeFixture(t, path, updated)
					after, errAfter := ctx.MatchFile(root, "fixture.go")
					if errBefore != nil || errAfter != nil || (goos == "android" && after) || (goos != "android" && before != after) {
						t.Fatalf("%s cgo=%v: before=%v after=%v errors=(%v, %v)", goos, cgo, before, after, errBefore, errAfter)
					}
				}
			}
			again, errAgain := excludeAndroid(updated)
			if errAgain != nil || !bytes.Equal(updated, again) {
				t.Fatalf("exclusion is not idempotent: %v", errAgain)
			}
		})
	}
}

func TestBoundaryPolicyHandlesNewDesktopHelpersAndTests(t *testing.T) {
	root := t.TempDir()
	policy := []boundary{{directory: "backend", desktop: "*.go"}}
	for _, name := range []string{"backend_android.go", "backend_android_arm64.go", "new_journal.go", "new_journal_test.go"} {
		writeFixture(t, filepath.Join(root, "backend", name), []byte("package backend\n"))
	}
	if _, errCheck := checkBoundaries(root, false, policy); errCheck == nil {
		t.Fatal("check accepted unguarded desktop helpers")
	}
	changed, errApply := checkBoundaries(root, true, policy)
	if errApply != nil || len(changed) != 2 {
		t.Fatalf("apply: changed=%v error=%v", changed, errApply)
	}
	if _, errCheck := checkBoundaries(root, false, policy); errCheck != nil {
		t.Fatal(errCheck)
	}
	stub, errRead := os.ReadFile(filepath.Join(root, "backend", "backend_android.go"))
	if errRead != nil || strings.Contains(string(stub), "!android") {
		t.Fatalf("Android stub was excluded: %v", errRead)
	}
}

func TestMixedBoundaryRequiresReviewBeforeWriting(t *testing.T) {
	root := t.TempDir()
	policy := []boundary{{directory: "mixed", shared: "shared.go", desktop: "desktop*.go"}}
	for _, name := range []string{"shared.go", "desktop.go", "unfamiliar.go"} {
		writeFixture(t, filepath.Join(root, "mixed", name), []byte("package mixed\n"))
	}
	if _, errApply := checkBoundaries(root, true, policy); errApply == nil || !strings.Contains(errApply.Error(), "unclassified") {
		t.Fatalf("unknown mixed file must stop automatic edits: %v", errApply)
	}
	unchanged, errRead := os.ReadFile(filepath.Join(root, "mixed", "desktop.go"))
	if errRead != nil || strings.Contains(string(unchanged), "!android") {
		t.Fatalf("failed review partially changed source: %v", errRead)
	}
}

func TestSharedBoundaryCannotDisappearFromAndroid(t *testing.T) {
	for _, source := range []string{"//go:build !android\n\npackage shared\n", "//go:build arm64\n\npackage shared\n"} {
		root := t.TempDir()
		policy := []boundary{{directory: "shared", shared: "shared.go"}}
		writeFixture(t, filepath.Join(root, "shared", "shared.go"), []byte(source))
		if _, errCheck := checkBoundaries(root, true, policy); errCheck == nil {
			t.Fatal("shared source was allowed to disappear from an Android architecture")
		}
	}
}

func TestConflictNormalizationOnlyAcceptsDesktopFiles(t *testing.T) {
	source := []byte("package fixture\n")
	for _, path := range []string{
		"internal/store/new_backend.go", "internal/home/client_new.go", "internal/discovery/browse_new.go",
	} {
		if _, errNormalize := normalizeDesktop(path, source); errNormalize != nil {
			t.Fatalf("desktop path %s: %v", path, errNormalize)
		}
	}
	for _, path := range []string{
		"internal/store/store_android.go", "internal/store/store_android_arm64_test.go",
		"internal/home/kv_helpers.go", "internal/home/unfamiliar.go",
		"internal/discovery/txt_list.go", "internal/other/new_backend.go",
	} {
		if _, errNormalize := normalizeDesktop(path, source); errNormalize == nil {
			t.Fatalf("unsafe conflict normalization accepted %s", path)
		}
	}
}

func writeFixture(t *testing.T, path string, source []byte) {
	t.Helper()
	if errMkdir := os.MkdirAll(filepath.Dir(path), 0o755); errMkdir != nil {
		t.Fatal(errMkdir)
	}
	if errWrite := os.WriteFile(path, source, 0o644); errWrite != nil {
		t.Fatal(errWrite)
	}
}
