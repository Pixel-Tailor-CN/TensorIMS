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
