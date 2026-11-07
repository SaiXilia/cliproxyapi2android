#!/usr/bin/env python3
"""
check_deps.py - Automated Android Dependency Blacklist Checker
Parses stream of JSON objects from 'go list -deps -json' and asserts
that none of the pruned/forbidden packages exist in the Android compilation graph.
"""

import sys
import json

FORBIDDEN_PREFIXES = [
    "github.com/pion/",
    "github.com/redis/go-redis/",
    "github.com/jackc/pgx/",
    "github.com/go-git/",
    "github.com/minio/minio-go/",
    "github.com/libp2p/zeroconf/",
    "github.com/charmbracelet/bubbletea",
    "github.com/charmbracelet/lipgloss",
    "github.com/charmbracelet/bubbles",
    "github.com/atotto/clipboard",
    "github.com/skratchdot/open-golang",
]

def check_file(file_path):
    print(f"--> Auditing dependencies from: {file_path}")
    violations = []
    total_packages = 0

    with open(file_path, "r", encoding="utf-8-sig") as f:
        decoder = json.JSONDecoder()
        buffer = f.read()
        idx = 0
        length = len(buffer)

        while idx < length:
            # Skip whitespace
            while idx < length and buffer[idx].isspace():
                idx += 1
            if idx >= length:
                break

            try:
                obj, end = decoder.raw_decode(buffer, idx)
            except ValueError as error:
                print(f"[FAIL] Invalid dependency graph: {error}")
                return False
            idx = end
            total_packages += 1

            if not isinstance(obj, dict) or not isinstance(obj.get("ImportPath"), str) or not obj["ImportPath"]:
                print("[FAIL] Dependency graph contains a package without an ImportPath")
                return False
            pkg_path = obj.get("ImportPath", "")
            if obj.get("Error") or obj.get("DepsErrors") or obj.get("Incomplete"):
                print(f"[FAIL] Incomplete or failed package in dependency graph: {pkg_path}")
                return False
            for forbidden in FORBIDDEN_PREFIXES:
                prefix = forbidden.rstrip("/")
                if pkg_path == prefix or pkg_path.startswith(prefix + "/"):
                    violations.append((pkg_path, forbidden))

    print(f"Total analyzed packages in build graph: {total_packages}")
    if total_packages == 0:
        print("[FAIL] Dependency graph is empty")
        return False
    if violations:
        print(f"\n[FATAL] Found {len(violations)} forbidden dependencies in Android compilation graph:")
        for pkg, forbidden in violations:
            print(f"  - Package: {pkg} (matches blacklisted prefix: '{forbidden}')")
        return False

    print("[PASS] All forbidden dependencies successfully pruned (0 found)!")
    return True

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python3 check_deps.py <deps.json> [deps2.json ...]")
        sys.exit(1)

    all_passed = True
    for arg in sys.argv[1:]:
        if not check_file(arg):
            all_passed = False

    if not all_passed:
        sys.exit(1)
