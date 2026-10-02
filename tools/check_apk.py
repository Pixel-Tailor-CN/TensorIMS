"""离线核对 APK 身份/ABI/私有桥接入口，不代替真机验收。"""
import argparse
import re
import subprocess
import zipfile
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('apk', type=Path)
parser.add_argument('--aapt2', required=True, type=Path)
args = parser.parse_args()
metadata = subprocess.check_output([str(args.aapt2), 'dump', 'badging', str(args.apk)], text=True)
assert "package: name='app.mystery0.ims.tensor'" in metadata, 'Unexpected application identity'
assert re.search(r"(?:minSdkVersion|sdkVersion):'33'", metadata), 'Unexpected minimum SDK'
assert "targetSdkVersion:'37'" in metadata, 'Unexpected target SDK'
with zipfile.ZipFile(args.apk) as archive:
    names = archive.namelist()
    dex = b''.join(archive.read(n) for n in names if re.fullmatch(r'classes\d*\.dex', n))
    assert b'io/github/vvb2060/ims' not in dex, 'Legacy first-party runtime namespace remains'
    assert b'Lapp/mystery0/ims/tensor/embedded/EmbeddedServerMain;' in dex, 'Private app_process entry missing'
    assert b'Lapp/mystery0/ims/tensor/bridge/IEmbeddedServer;' in dex, 'Private AIDL descriptor missing'
    native = [n for n in names if n.startswith('lib/')]
    assert native and all(n.startswith('lib/arm64-v8a/') for n in native), 'Unexpected native ABI'
    assert 'lib/arm64-v8a/libadb.so' in native, 'Wireless pairing native implementation missing'
    assert not any('librish' in n or 'libshizuku' in n for n in native), 'Unrelated manager/shell native component present'
    licenses = [n for n in names if n.startswith('assets/licenses/')]
    assert licenses, 'Bundled license/source notices missing'
print('APK identity, entry points, ABI and license presence checks passed')
print(metadata.splitlines()[0])
print(f'APK bytes: {args.apk.stat().st_size}')
