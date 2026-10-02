from pathlib import Path
root=Path(__file__).resolve().parents[1]
expected='app.mystery0.ims.tensor'
assert f'val packageName = "{expected}"' in (root/'app/build.gradle.kts').read_text(), 'applicationId/namespace must use new identity'
for area in ['main','test']:
 for p in (root/'app/src'/area).rglob('*'):
  if p.suffix in ('.kt','.java','.aidl'):
   assert 'io.github.vvb2060.ims' not in p.read_text(), f'legacy runtime identity: {p}'
assert not (root/'app/src/main/java/io/github/vvb2060/ims').exists(), 'legacy source path'
print('Identity checks passed')
import xml.etree.ElementTree as ET
android = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
application = manifest.find('application')
assert application is not None
assert application.get(android+'allowBackup') == 'false', 'Private key/recovery data must not be backed up'
providers = {p.get(android+'authorities'): p for p in application.findall('provider')}
assert '${applicationId}.shizuku' in providers, 'Official client provider contract missing'
assert expected+'.embedded.bridge' in providers, 'Private authority missing'
assert len(providers) == len(application.findall('provider')), 'Duplicate authority'
assert not manifest.findall('permission'), 'Do not redeclare official manager permissions'
assert len(manifest.findall('instrumentation')) == 8
assert all(i.get(android+'targetPackage') == '${applicationId}' for i in manifest.findall('instrumentation'))
assert 'productFlavors' not in (root/'app/build.gradle.kts').read_text(), 'Single APK must not add mode flavors'
print('Source manifest isolation and single-APK checks passed')
for document in ['README.md', 'README_CN.md']:
    text = (root/document).read_text()
    assert '应用包名与签名配置保持不变' not in text, f'Stale in-place upgrade claim: {document}'
print('Migration documentation checks passed')
