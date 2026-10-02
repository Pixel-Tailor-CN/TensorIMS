"""双包 release 产物收集的无 Android SDK 回归测试。"""

import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from collect_release_artifacts import IDENTITIES, collect_artifacts


class ReleaseArtifactTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.root = Path(self.temp_dir.name)
        self.build_dir = self.root / "app" / "build"
        self.output_dir = self.root / "outputs"
        self.metadata = {}
        for flavor, application_id in IDENTITIES.items():
            apk_dir = self.build_dir / "outputs" / "apk" / flavor / "release"
            apk_dir.mkdir(parents=True)
            self.metadata[flavor] = {
                "applicationId": application_id,
                "variantName": f"{flavor}Release",
                "elements": [{
                    "type": "SINGLE",
                    "filters": [],
                    "versionCode": 42,
                    "versionName": "4.0.r42.a1b2c3d4",
                    "outputFile": f"app-{flavor}-release.apk",
                }],
            }
            self.write_metadata(flavor)
            (apk_dir / f"app-{flavor}-release.apk").write_bytes(f"{flavor} APK".encode())
            mapping = self.build_dir / "outputs" / "mapping" / f"{flavor}Release" / "mapping.txt"
            mapping.parent.mkdir(parents=True)
            mapping.write_text(f"{flavor} mapping", encoding="utf-8")

    def write_metadata(self, flavor):
        metadata_path = self.build_dir / "outputs" / "apk" / flavor / "release" / "output-metadata.json"
        metadata_path.write_text(json.dumps(self.metadata[flavor]), encoding="utf-8")

    def collect(self):
        return collect_artifacts(self.build_dir, self.output_dir)

    def assert_rejected(self):
        with self.assertRaises((ValueError, OSError)):
            self.collect()
        self.assertFalse(self.output_dir.exists())

    def test_collects_two_distinct_apks_and_their_own_mappings(self):
        outputs = self.collect()
        self.assertEqual(outputs["versionCode"], "42")
        self.assertEqual(outputs["versionName"], "4.0.r42.a1b2c3d4")
        self.assertEqual(len(list(self.output_dir.iterdir())), 4)
        self.assertEqual(len(list(self.output_dir.glob("*.apk"))), 2)
        for flavor, application_id in IDENTITIES.items():
            apk = Path(outputs[f"{flavor}Apk"])
            mapping = Path(outputs[f"{flavor}Mapping"])
            self.assertEqual(apk.name, f"TensorIMS-{application_id}-4.0.r42.a1b2c3d4.apk")
            self.assertEqual(apk.read_text(), f"{flavor} APK")
            self.assertEqual(mapping.read_text(), f"{flavor} mapping")

    def test_rejects_wrong_application_id_or_variant(self):
        original = copy.deepcopy(self.metadata["legacy"])
        for key, value in (("applicationId", IDENTITIES["tensor"]), ("variantName", "legacyDebug")):
            with self.subTest(key=key):
                self.metadata["legacy"] = copy.deepcopy(original)
                self.metadata["legacy"][key] = value
                self.write_metadata("legacy")
                self.assert_rejected()

    def test_rejects_mismatched_versions(self):
        original = copy.deepcopy(self.metadata["legacy"])
        for key, value in (("versionCode", 43), ("versionName", "4.0.r43.a1b2c3d4")):
            with self.subTest(key=key):
                self.metadata["legacy"] = copy.deepcopy(original)
                self.metadata["legacy"]["elements"][0][key] = value
                self.write_metadata("legacy")
                self.assert_rejected()

    def test_rejects_malformed_versions(self):
        original = copy.deepcopy(self.metadata["legacy"])
        for key, values in (
            ("versionCode", [None, "42", True, 0, -1]),
            ("versionName", [None, "", 42, "../bad", "bad\nvalue", "bad/name", "bad..name", "bad."]),
        ):
            for value in values:
                with self.subTest(key=key, value=value):
                    self.metadata["legacy"] = copy.deepcopy(original)
                    self.metadata["legacy"]["elements"][0][key] = value
                    self.write_metadata("legacy")
                    self.assert_rejected()

    def test_rejects_missing_extra_or_malformed_metadata_elements(self):
        original = copy.deepcopy(self.metadata["legacy"]["elements"])
        for elements in (None, [], original * 2, [None], {}):
            with self.subTest(elements=elements):
                self.metadata["legacy"]["elements"] = elements
                self.write_metadata("legacy")
                self.assert_rejected()

    def test_rejects_filtered_apks(self):
        self.metadata["legacy"]["elements"][0]["filters"] = [{"filterType": "ABI", "value": "arm64-v8a"}]
        self.write_metadata("legacy")
        self.assert_rejected()

    def test_rejects_extra_apk_file(self):
        extra = self.build_dir / "outputs" / "apk" / "legacy" / "release" / "old.apk"
        extra.write_bytes(b"old APK")
        self.assert_rejected()

    def test_rejects_unsafe_output_filename(self):
        for output_file in (None, "../other.apk", "nested/file.apk", "..\\other.apk", "wrong.aab"):
            with self.subTest(output_file=output_file):
                self.metadata["legacy"]["elements"][0]["outputFile"] = output_file
                self.write_metadata("legacy")
                self.assert_rejected()

    def test_rejects_missing_apk_and_mapping(self):
        for relative in (
            "outputs/apk/legacy/release/app-legacy-release.apk",
            "outputs/mapping/legacyRelease/mapping.txt",
        ):
            with self.subTest(relative=relative):
                path = self.build_dir / relative
                contents = path.read_bytes()
                path.unlink()
                self.assert_rejected()
                path.write_bytes(contents)

    def test_rejects_empty_apk_and_mapping(self):
        for relative in (
            "outputs/apk/legacy/release/app-legacy-release.apk",
            "outputs/mapping/legacyRelease/mapping.txt",
        ):
            with self.subTest(relative=relative):
                path = self.build_dir / relative
                contents = path.read_bytes()
                path.write_bytes(b"")
                self.assert_rejected()
                path.write_bytes(contents)

    def test_does_not_mix_in_existing_output_files(self):
        self.output_dir.mkdir()
        stale_apk = self.output_dir / "old.apk"
        stale_apk.write_bytes(b"old APK")
        with self.assertRaisesRegex(ValueError, "must be empty"):
            self.collect()
        self.assertEqual(list(self.output_dir.iterdir()), [stale_apk])

    def test_cli_writes_outputs_for_one_release(self):
        github_output = self.root / "github-output"
        result = subprocess.run(
            [sys.executable, str(Path(__file__).with_name("collect_release_artifacts.py")),
             "--build-dir", str(self.build_dir), "--output-dir", str(self.output_dir),
             "--github-output", str(github_output)],
            capture_output=True, text=True, check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        outputs = dict(line.split("=", 1) for line in github_output.read_text().splitlines())
        self.assertEqual(set(outputs), {"versionCode", "versionName", "tensorApk", "legacyApk",
                                       "tensorMapping", "legacyMapping"})
        self.assertIn("Collected two release APKs and two mappings", result.stdout)
        for key in ("tensorApk", "legacyApk", "tensorMapping", "legacyMapping"):
            self.assertTrue(Path(outputs[key]).is_file())

    def test_cli_does_not_publish_outputs_after_validation_failure(self):
        self.metadata["legacy"]["applicationId"] = "wrong.package"
        self.write_metadata("legacy")
        github_output = self.root / "github-output"
        result = subprocess.run(
            [sys.executable, str(Path(__file__).with_name("collect_release_artifacts.py")),
             "--build-dir", str(self.build_dir), "--output-dir", str(self.output_dir),
             "--github-output", str(github_output)],
            capture_output=True, text=True, check=False,
        )
        self.assertEqual(result.returncode, 1)
        self.assertIn("Artifact validation failed", result.stderr)
        self.assertFalse(github_output.exists())
        self.assertFalse(self.output_dir.exists())


if __name__ == "__main__":
    unittest.main()
