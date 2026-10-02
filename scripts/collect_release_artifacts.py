#!/usr/bin/env python3
"""校验双包 release 产物，并为同一次 GitHub Release 收集 APK 和混淆映射。"""

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import sys
from dataclasses import dataclass


IDENTITIES = {
    "tensor": "app.mystery0.ims.tensor",
    "legacy": "io.github.vvb2060.ims",
}
SAFE_VERSION_NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9._+-]*")


@dataclass(frozen=True)
class ReleaseArtifact:
    flavor: str
    application_id: str
    version_code: int
    version_name: str
    apk: Path
    mapping: Path


def require_file(path: Path) -> None:
    if not path.is_file() or path.stat().st_size == 0:
        raise ValueError(f"Missing or empty artifact: {path}")


def read_artifact(build_dir: Path, flavor: str, application_id: str) -> ReleaseArtifact:
    apk_dir = build_dir / "outputs" / "apk" / flavor / "release"
    metadata_path = apk_dir / "output-metadata.json"
    with metadata_path.open(encoding="utf-8") as metadata_file:
        metadata = json.load(metadata_file)
    if not isinstance(metadata, dict):
        raise ValueError(f"Invalid metadata object: {metadata_path}")
    if metadata.get("applicationId") != application_id:
        raise ValueError(f"Unexpected applicationId for {flavor}: {metadata.get('applicationId')!r}")
    if metadata.get("variantName") != f"{flavor}Release":
        raise ValueError(f"Unexpected variantName for {flavor}: {metadata.get('variantName')!r}")
    elements = metadata.get("elements")
    if not isinstance(elements, list) or len(elements) != 1 or not isinstance(elements[0], dict):
        raise ValueError(f"Expected exactly one APK metadata element for {flavor}")
    element = elements[0]
    if element.get("type") != "SINGLE" or element.get("filters") != []:
        raise ValueError(f"Expected one unfiltered APK for {flavor}")
    version_code = element.get("versionCode")
    if type(version_code) is not int or version_code <= 0:
        raise ValueError(f"Invalid versionCode for {flavor}: {version_code!r}")
    version_name = element.get("versionName")
    # 版本号同时用于文件名、标签和 GitHub 输出，拒绝路径及换行等特殊字符。
    if (not isinstance(version_name, str) or not SAFE_VERSION_NAME.fullmatch(version_name)
            or ".." in version_name or version_name.endswith(".")):
        raise ValueError(f"Invalid versionName for {flavor}: {version_name!r}")
    output_file = element.get("outputFile")
    if (not isinstance(output_file, str) or not output_file.endswith(".apk")
            or Path(output_file).name != output_file or "\\" in output_file):
        raise ValueError(f"Invalid outputFile for {flavor}: {output_file!r}")
    apk = apk_dir / output_file
    require_file(apk)
    # 除了 metadata，还检查实际目录，避免把拆分包或残留包误当作单 APK 发布。
    if set(apk_dir.rglob("*.apk")) != {apk}:
        raise ValueError(f"Expected exactly one APK file for {flavor}")
    mapping = build_dir / "outputs" / "mapping" / f"{flavor}Release" / "mapping.txt"
    require_file(mapping)
    return ReleaseArtifact(flavor, application_id, version_code, version_name, apk, mapping)


def collect_artifacts(build_dir: Path, output_dir: Path) -> dict[str, str]:
    artifacts = [read_artifact(build_dir, flavor, app_id) for flavor, app_id in IDENTITIES.items()]
    if len({(artifact.version_code, artifact.version_name) for artifact in artifacts}) != 1:
        raise ValueError("Both package identities must have identical versionCode and versionName")
    # 不复用已有输出，保证上传目录严格只包含本次核验的两份 APK 及各自 mapping。
    if output_dir.exists() and (not output_dir.is_dir() or any(output_dir.iterdir())):
        raise ValueError(f"Artifact output directory must be empty: {output_dir}")
    output_dir.mkdir(parents=True, exist_ok=True)
    outputs = {
        "versionCode": str(artifacts[0].version_code),
        "versionName": artifacts[0].version_name,
    }
    expected_files = set()
    for artifact in artifacts:
        basename = f"TensorIMS-{artifact.application_id}-{artifact.version_name}"
        for suffix, source, output_key in (
            (".apk", artifact.apk, f"{artifact.flavor}Apk"),
            ("-mapping.txt", artifact.mapping, f"{artifact.flavor}Mapping"),
        ):
            destination = output_dir / f"{basename}{suffix}"
            shutil.copy2(source, destination)
            expected_files.add(destination)
            outputs[output_key] = str(destination)
    if set(output_dir.iterdir()) != expected_files or len(list(output_dir.glob("*.apk"))) != 2:
        raise ValueError("Expected exactly two APKs and their two mapping files")
    return outputs


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate and collect both signed release variants")
    parser.add_argument("--build-dir", type=Path, default=Path("app/build"))
    parser.add_argument("--output-dir", type=Path, default=Path("outputs"))
    parser.add_argument("--github-output", type=Path, default=os.environ.get("GITHUB_OUTPUT"))
    args = parser.parse_args()
    try:
        outputs = collect_artifacts(args.build_dir, args.output_dir)
        if args.github_output is not None:
            with args.github_output.open("a", encoding="utf-8") as output_file:
                for key, value in outputs.items():
                    output_file.write(f"{key}={value}\n")
        print(f"Collected two release APKs and two mappings for {outputs['versionName']} "
              f"(versionCode {outputs['versionCode']})")
    except (OSError, ValueError) as error:
        print(f"Artifact validation failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
