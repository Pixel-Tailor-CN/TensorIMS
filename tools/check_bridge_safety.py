#!/usr/bin/env python3
"""私有桥接的静态回归门；不替代设备上的 Binder/权限验收。"""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
base = root / 'app/src/main/java/app/mystery0/ims/tensor'
errors = []
for path in (base / 'privileged').glob('*.kt'):
    source = path.read_text()
    if re.search(r'ShizukuBinderWrapper|Shizuku\.|import rikka\.shizuku|ServiceManager', source):
        errors.append(f'{path.name}: privileged business uses static service access')
    if re.search(r'Log\.\w\([^\n]*(\$values|\$arguments|\$extras|\$request)', source):
        errors.append(f'{path.name}: raw argument logging')
    if ': Instrumentation()' in source and path.name != 'SessionInstrumentation.kt':
        errors.append(f'{path.name}: bypasses explicit session base')
for path in [* (base / 'bridge').glob('*.kt'), * (base / 'embedded').glob('EmbeddedServer*.kt')]:
    source = path.read_text()
    if re.search(r'\.transact\(|Runtime\.getRuntime|ProcessBuilder|killProcess|forceStopPackage', source):
        errors.append(f'{path.name}: generic execution or process cleanup surface')
aidl = root / 'app/src/main/aidl/app/mystery0/ims/tensor/bridge'
expected = {
    'IPrivilegeSession.aidl': {'beginDelegation', 'endDelegation', 'getSlotIndex', 'resetIms', 'isImsRegistered'},
    'IEmbeddedServer.aidl': {'handshake', 'execute', 'shutdownOwnedServer', 'getStatus'},
    'IEmbeddedResult.aidl': {'onResult'},
}
for name, methods in expected.items():
    actual = set(re.findall(r'\b(\w+)\s*\(', (aidl/name).read_text()))
    if actual != methods:
        errors.append(f'{name}: unexpected AIDL method set {actual}')
legacy = (base / 'privileged/LegacyConfigurationOperation.kt').read_text()
if 'val verifiedSnapshot = reader.snapshot(expected)' not in legacy or 'TargetConfigurationReader.encode(verifiedSnapshot)' not in legacy:
    errors.append('LegacyConfigurationOperation.kt: final observed snapshot must validate expected values')
if errors:
    print('\n'.join(errors))
    raise SystemExit(1)
print('PASS: explicit sessions, fixed AIDL, no generic execution or raw argument logging')
