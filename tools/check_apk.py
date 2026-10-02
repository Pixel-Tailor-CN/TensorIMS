"""离线核对 APK 身份/Manifest/ABI/私有桥接入口，不代替真机验收。"""
import argparse
import re
import struct
import subprocess
import zipfile
from pathlib import Path

def class_definitions(dex: bytes) -> set[str]:
    """读取实际 class_defs，而不是把 DEX 中仅被引用的类名误判为已打包。"""
    assert dex.startswith(b'dex\n') and len(dex) >= 112, 'Invalid DEX header'
    assert struct.unpack_from('<I', dex, 40)[0] == 0x12345678, 'Unsupported DEX endian tag'
    strings_size, strings_offset, types_size, types_offset = struct.unpack_from('<4I', dex, 56)
    classes_size, classes_offset = struct.unpack_from('<2I', dex, 96)
    result = set()
    for index in range(classes_size):
        type_index = struct.unpack_from('<I', dex, classes_offset + index * 32)[0]
        assert type_index < types_size
        string_index = struct.unpack_from('<I', dex, types_offset + type_index * 4)[0]
        assert string_index < strings_size
        offset = struct.unpack_from('<I', dex, strings_offset + string_index * 4)[0]
        # string_data_item 前缀是 UTF-16 长度的 ULEB128，描述符本身使用 ASCII。
        for _ in range(5):
            value = dex[offset]
            offset += 1
            if not value & 0x80:
                break
        else:
            raise AssertionError('Invalid DEX string length')
        end = dex.index(b'\0', offset)
        result.add(dex[offset:end].decode('utf-8'))
    return result


parser = argparse.ArgumentParser()
parser.add_argument('apk', type=Path)
parser.add_argument('--aapt2', required=True, type=Path)
parser.add_argument('--application-id', choices=['app.mystery0.ims.tensor', 'io.github.vvb2060.ims'],
                    default='app.mystery0.ims.tensor')
args = parser.parse_args()
metadata = subprocess.check_output([str(args.aapt2), 'dump', 'badging', str(args.apk)], text=True)
assert f"package: name='{args.application_id}'" in metadata, 'Unexpected application identity'
assert re.search(r"(?:minSdkVersion|sdkVersion):'33'", metadata), 'Unexpected minimum SDK'
assert "targetSdkVersion:'37'" in metadata, 'Unexpected target SDK'

# aapt2 的结构化文本用于检查编译后的值，避免仅检查未替换的源 Manifest。
xml = subprocess.check_output([str(args.aapt2), 'dump', 'xmltree', str(args.apk),
                               '--file', 'AndroidManifest.xml'], text=True)
nodes = []
current = None
for line in xml.splitlines():
    element = re.match(r'\s*E: (\S+)', line)
    if element:
        current = {'element': element.group(1)}
        nodes.append(current)
    attribute = re.match(r'\s*A: ([^=(\s]+)(?:\([^)]*\))?="([^"]*)"', line)
    if attribute and current is not None:
        key = attribute.group(1).replace('http://schemas.android.com/apk/res/android:', 'android:')
        current[key] = attribute.group(2)
providers = [node for node in nodes if node['element'] == 'provider']
expected_authorities = {f'{args.application_id}.{suffix}' for suffix in
                        ['shizuku', 'embedded.bridge', 'logcat_fileprovider']}
# AndroidX 可能合入启动 Provider；三项业务 authority 必须各自绑定当前安装身份。
authorities = {node.get('android:authorities') for node in providers}
assert expected_authorities <= authorities, f'Wrong provider identities: {authorities}'
assert len(authorities) == len(providers), 'Duplicate provider authorities'
permissions = [node.get('android:name', '') for node in nodes if node['element'] == 'permission']
assert all(name.startswith(args.application_id + '.') for name in permissions), 'Unscoped declared permission'
assert all(authority and authority.startswith(args.application_id + '.') for authority in authorities)
instrumentations = [node for node in nodes if node['element'] == 'instrumentation']
assert len(instrumentations) == 8
assert all(node.get('android:targetPackage') == args.application_id for node in instrumentations)
assert all(node.get('android:name', '').startswith('app.mystery0.ims.tensor.privileged.')
           for node in instrumentations), 'Instrumentation class namespace must not follow legacy applicationId'
application = next(node for node in nodes if node['element'] == 'application')
assert application.get('android:name') == 'app.mystery0.ims.tensor.Application'
activity = next(node for node in nodes if node.get('android:name') == 'app.mystery0.ims.tensor.ui.MainActivity')
assert activity['element'] == 'activity'
with zipfile.ZipFile(args.apk) as archive:
    names = archive.namelist()
    dex_files = [archive.read(n) for n in names if re.fullmatch(r'classes\d*\.dex', n)]
    dex = b''.join(dex_files)
    classes = set().union(*(class_definitions(data) for data in dex_files))
    assert b'io/github/vvb2060/ims' not in dex, 'Legacy first-party runtime namespace remains'
    for node in instrumentations:
        descriptor = 'L' + node['android:name'].replace('.', '/') + ';'
        assert descriptor in classes, f"Missing instrumentation class: {node['android:name']}"
    assert 'Lapp/mystery0/ims/tensor/embedded/EmbeddedServerMain;' in classes, 'Private app_process entry missing'
    assert 'Lapp/mystery0/ims/tensor/bridge/IEmbeddedServer;' in classes, 'Private AIDL descriptor missing'
    assert (args.application_id + '.embedded.bridge').encode() in dex, 'Bridge authority not bound to APK identity'
    native = [n for n in names if n.startswith('lib/')]
    assert native and all(n.startswith('lib/arm64-v8a/') for n in native), 'Unexpected native ABI'
    assert 'lib/arm64-v8a/libadb.so' in native, 'Wireless pairing native implementation missing'
    assert not any('librish' in n or 'libshizuku' in n for n in native)
    assert any(n.startswith('assets/licenses/') for n in names), 'Bundled license/source notices missing'
print('PASS: APK identity, scoped providers/targets, shared class namespace, ABI and licenses')
print(metadata.splitlines()[0])
print('Providers:', ', '.join(sorted(authorities)))
print('Declared permissions:', ', '.join(sorted(permissions)))
print(f'APK bytes: {args.apk.stat().st_size}')
