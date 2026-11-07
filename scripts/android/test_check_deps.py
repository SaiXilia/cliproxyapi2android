import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

from check_deps import check_file


class CheckDependenciesTest(unittest.TestCase):
    def check(self, content):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "deps.json"
            path.write_text(content, encoding="utf-8")
            with contextlib.redirect_stdout(io.StringIO()):
                return check_file(path)

    def test_shared_android_dependencies_are_allowed(self):
        content = "\n".join(json.dumps({"ImportPath": name}) for name in [
            "context", "github.com/router-for-me/CLIProxyAPI/v8/internal/home",
            "github.com/MoeForge/moego/ojson",
        ])
        self.assertTrue(self.check(content))

    def test_desktop_dependencies_and_new_module_versions_are_rejected(self):
        for path in [
            "github.com/go-git/go-git/v6", "github.com/go-git/go-billy/v6/osfs",
            "github.com/minio/minio-go/v8", "github.com/redis/go-redis/v9",
            "github.com/pion/webrtc/v4", "github.com/charmbracelet/bubbletea/v2",
            "github.com/libp2p/zeroconf",
        ]:
            with self.subTest(path=path):
                self.assertFalse(self.check(json.dumps({"ImportPath": path})))

    def test_empty_truncated_or_invalid_graphs_are_rejected(self):
        for content in ["", " \n", "{", "{}", "[]", '{"ImportPath":"context"}\n{']:
            with self.subTest(content=content):
                self.assertFalse(self.check(content))

    def test_package_loading_errors_cannot_pass_the_audit(self):
        for field, value in [
            ("Error", {"Err": "missing Android implementation"}),
            ("DepsErrors", [{"Err": "missing dependency"}]),
            ("Incomplete", True),
        ]:
            with self.subTest(field=field):
                self.assertFalse(self.check(json.dumps({"ImportPath": "fixture", field: value})))


if __name__ == "__main__":
    unittest.main()
