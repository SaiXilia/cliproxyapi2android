#!/usr/bin/env python3
"""Accept upstream changes when local changes are formatting or managed exclusions."""

import subprocess
import sys


def resolve(path, boundary_tool=None):
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
        if not boundary_tool:
            return False
        try:
            normalized = [
                subprocess.run(
                    [boundary_tool, "-normalize-desktop", path],
                    input=source, capture_output=True, check=True,
                ).stdout
                for source in formatted
            ]
        except (OSError, subprocess.CalledProcessError):
            return False
        if normalized[0] != normalized[1]:
            return False
    subprocess.run(
        ["git", "restore", "--source=MERGE_HEAD", "--staged", "--worktree", "--", path],
        check=True,
    )
    print(f"Resolved formatting or managed Android boundary divergence using upstream: {path}")
    return True


if __name__ == "__main__":
    if len(sys.argv) not in (2, 3):
        sys.exit("Usage: resolve_go_format_conflict.py <conflicted.go> [boundary-tool]")
    sys.exit(0 if resolve(*sys.argv[1:]) else 1)
