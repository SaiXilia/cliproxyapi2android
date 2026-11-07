import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


class ResolveGoFormatConflictTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tool_temp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.tool_temp.cleanup)
        cls.boundary_tool = str(Path(cls.tool_temp.name) / ("boundaries.exe" if os.name == "nt" else "boundaries"))
        root = Path(__file__).resolve().parents[2]
        subprocess.run(
            ["go", "build", "-o", cls.boundary_tool, "./scripts/android/boundaries"], cwd=root, check=True,
        )

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        self.source = self.repo / "fixture.go"
        self.resolver = Path(__file__).with_name("resolve_go_format_conflict.py").resolve()
        self.git("init", "--initial-branch=main")
        for key, value in (
            ("user.name", "Sync regression test"),
            ("user.email", "sync-test@example.invalid"),
            ("core.autocrlf", "false"),
            ("core.hooksPath", str(self.repo / "no-hooks")),
            ("commit.gpgsign", "false"),
        ):
            self.git("config", key, value)

    def git(self, *args, check=True):
        return subprocess.run(
            ["git", *args], cwd=self.repo, capture_output=True, text=True, check=check
        )

    def commit(self, message):
        self.git("add", ".")
        self.git("commit", "-m", message)

    def prepare_conflict(self, ours, base="package fixture\n\nfunc value()int{return 1}\n"):
        if base is None:
            (self.repo / "seed").write_text("seed\n", encoding="utf-8")
        else:
            self.source.write_text(base, encoding="utf-8")
        self.commit("base")
        self.git("checkout", "-b", "upstream")
        self.upstream = "package fixture\n\nfunc value() int { return 2 }\n"
        self.source.write_text(self.upstream, encoding="utf-8")
        self.commit("upstream behavior change")
        self.git("checkout", "main")
        self.source.write_text(ours, encoding="utf-8")
        self.commit("local change")
        self.assertEqual(self.git("merge", "upstream", check=False).returncode, 1)
        self.assertEqual(self.git("diff", "--name-only", "--diff-filter=U").stdout.strip(), self.relative_path())

    def relative_path(self):
        return self.source.relative_to(self.repo).as_posix()

    def run_resolver(self, with_boundaries=False):
        return subprocess.run(
            [sys.executable, str(self.resolver), self.relative_path()] +
            ([self.boundary_tool] if with_boundaries else []),
            cwd=self.repo,
            capture_output=True,
            text=True,
        )

    def assert_unresolved(self, with_boundaries=False):
        before = self.source.read_bytes()
        result = self.run_resolver(with_boundaries)
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertEqual(self.source.read_bytes(), before)
        self.assertEqual(self.git("diff", "--name-only", "--diff-filter=U").stdout.strip(), self.relative_path())

    def test_formatting_only_divergence_keeps_upstream_behavior(self):
        self.prepare_conflict("package fixture\n\nfunc value() int { return 1 }\n")
        result = self.run_resolver()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.source.read_text(encoding="utf-8"), self.upstream)
        self.assertEqual(self.git("diff", "--name-only", "--diff-filter=U").stdout, "")
        self.assertEqual(self.git("show", ":fixture.go").stdout, self.upstream)

    def test_local_behavior_change_is_not_overwritten(self):
        self.prepare_conflict("package fixture\n\nfunc value() int { return 3 }\n")
        self.assert_unresolved()

    def test_android_build_constraint_is_not_overwritten(self):
        self.prepare_conflict("//go:build !android\n\npackage fixture\n\nfunc value() int { return 1 }\n")
        self.assert_unresolved()

    def test_add_add_conflict_without_base_is_not_overwritten(self):
        self.prepare_conflict("package fixture\n\nfunc value() int { return 1 }\n", base=None)
        self.assert_unresolved()

    def desktop_path(self, name="new_backend.go"):
        self.source = self.repo / "internal" / "store" / name
        self.source.parent.mkdir(parents=True)

    def test_managed_android_exclusion_keeps_upstream_behavior(self):
        self.desktop_path()
        self.prepare_conflict("//go:build !android\n\npackage fixture\n\nfunc value() int { return 1 }\n")
        result = self.run_resolver(with_boundaries=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.source.read_text(encoding="utf-8"), self.upstream)
        self.assertEqual(self.git("diff", "--name-only", "--diff-filter=U").stdout, "")

    def test_managed_exclusion_cannot_hide_real_desktop_changes(self):
        self.desktop_path()
        self.prepare_conflict("//go:build !android\n\npackage fixture\n\nfunc value() int { return 3 }\n")
        self.assert_unresolved(with_boundaries=True)

    def test_android_stub_changes_cannot_be_normalized_away(self):
        self.desktop_path("store_android.go")
        self.prepare_conflict("//go:build !android\n\npackage fixture\n\nfunc value() int { return 1 }\n")
        self.assert_unresolved(with_boundaries=True)


if __name__ == "__main__":
    unittest.main()
