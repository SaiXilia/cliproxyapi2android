#!/usr/bin/env python3
"""Accept upstream Go changes only when the local side changed formatting alone."""

import subprocess
import sys


def resolve(path):
    if not path.endswith(".go"):
        return False
    try:
        versions = [
            subprocess.run(
                ["git", "show", f":{stage}:{path}"],
                capture_output=True,
                check=True,
            ).stdout
            for stage in (1, 2, 3)
        ]
        formatted = [
            subprocess.run(["gofmt"], input=source, capture_output=True, check=True).stdout
            for source in versions[:2]
        ]
    except (OSError, subprocess.CalledProcessError):
        return False
    if formatted[0] != formatted[1]:
        return False
    subprocess.run(
        ["git", "restore", "--source=MERGE_HEAD", "--staged", "--worktree", "--", path],
        check=True,
    )
    print(f"Resolved formatting-only Android divergence using upstream: {path}")
    return True


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("Usage: resolve_go_format_conflict.py <conflicted.go>")
    sys.exit(0 if resolve(sys.argv[1]) else 1)
