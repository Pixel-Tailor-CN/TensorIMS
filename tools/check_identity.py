"""核对双包身份与公共代码 namespace；不代替实际 APK/设备验收。"""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
namespace = 'app.mystery0.ims.tensor'
legacy = 'io.github.vvb2060.ims'
gradle = (root / 'app/build.gradle.kts').read_text()
assert f'val packageName = "{namespace}"' in gradle
assert 'namespace = packageName' in gradle
assert 'flavorDimensions += "identity"' in gradle
assert 'create("tensor")' in gradle and 'create("legacy")' in gradle
assert f'applicationId = "{legacy}"' in gradle
assert 'applicationIdSuffix' not in gradle, 'Both release identities must remain exact'
for area in ['main', 'test']:
    for path in (root / 'app/src' / area).rglob('*'):
        if path.suffix in ('.kt', '.java', '.aidl'):
            source = path.read_text(encoding='utf-8-sig')
            assert not any(line.startswith(('package ' + legacy, 'import ' + legacy))
                           for line in source.splitlines()), f'Legacy code namespace: {path}'
assert not (root / 'app/src/main/java/io/github/vvb2060/ims').exists()
base = root / 'app/src/main/java/app/mystery0/ims/tensor'
protocol = (base / 'bridge/BridgeProtocol.kt').read_text()
assert 'const val PACKAGE = BuildConfig.APPLICATION_ID' in protocol
assert 'const val AUTHORITY = "$PACKAGE.embedded.bridge"' in protocol
assert 'getPackageInfo(BridgeProtocol.PACKAGE,' in (base / 'bridge/InstalledIdentity.kt').read_text()
assert 'setOf(BridgeProtocol.PACKAGE)' in (base / 'bridge/InstalledIdentity.kt').read_text()
assert 'ComponentName(BridgeProtocol.PACKAGE, operation.componentClassName)' in (base / 'embedded/EmbeddedServer.kt').read_text()
assert 'val authority = BridgeProtocol.AUTHORITY' in (base / 'embedded/EmbeddedServerMain.kt').read_text()
assert 'check(context.packageName == BridgeProtocol.PACKAGE)' in (base / 'embedded/EmbeddedLauncher.kt').read_text()

android = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
application = manifest.find('application')
assert application is not None
assert application.get(android + 'allowBackup') == 'false'
providers = {p.get(android + 'authorities'): p for p in application.findall('provider')}
assert set(providers) == {'${applicationId}.shizuku', '${applicationId}.embedded.bridge',
                          '${applicationId}.logcat_fileprovider'}
assert len(providers) == len(application.findall('provider'))
assert not manifest.findall('permission')
assert len(manifest.findall('instrumentation')) == 8
assert all(i.get(android + 'name', '').startswith(namespace + '.privileged.')
           for i in manifest.findall('instrumentation')), 'Instrumentation class must be fully qualified'
assert all(i.get(android + 'targetPackage') == '${applicationId}' for i in manifest.findall('instrumentation'))
assert '/(?:validateSigning|package|sign).*Release(?:Bundle)?/' in (root / 'signing.gradle').read_text()
print('PASS: two installation identities, shared namespace, flavor-scoped bridge and signing guard')
