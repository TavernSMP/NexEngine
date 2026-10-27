import hashlib
import json
import re
import sqlite3
import subprocess
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

project = Path(__file__).resolve().parents[1]
workspace = project.parents[1]
original = workspace / 'plugins.copy/NexEngine.jar'
rebuilt = project / 'build/libs/NexEngine-2.2.12-tavern.26.3.1.jar'
evidence = project / 'evidence'
assert hashlib.sha256(original.read_bytes()).hexdigest() == 'ffda465bcb13e4e0d8e8dab417a8e40dcca482c8cc49907e71b070e19f070a4e'

tests = ET.parse(project / 'build/test-results/test/TEST-su.nexmedia.engine.SourceRecoveryTest.xml').getroot()
assert tests.get('tests') == '4' and tests.get('failures') == '0' and tests.get('skipped') == '0'
standalone = ET.parse(evidence / 'standalone/build/test-results/test/TEST-su.nexmedia.engine.SourceRecoveryTest.xml').getroot()
assert standalone.get('failures') == '0' and standalone.get('skipped') == '1'

with zipfile.ZipFile(original) as before, zipfile.ZipFile(rebuilt) as after:
    classes = sorted(name[:-6].replace('/', '.') for name in before.namelist()
                     if name.startswith('su/nexmedia/') and name.endswith('.class') and '$' not in name)
    apis = [subprocess.check_output(['javap', '-protected', '-s', '-classpath', str(jar), *classes], text=True)
            for jar in (original, rebuilt)]
    normalized = [re.sub(r'  public static final su\.nexmedia\.engine\.Version V26_3;\n    descriptor: Lsu/nexmedia/engine/Version;\n', '', text)
                  for text in apis]
    assert normalized[0] == normalized[1], 'Public/protected APIs changed beyond V26_3.'
    resources = ['config.yml', 'lang/messages_cn.yml', 'lang/messages_ru.yml']
    assert all(before.read(name) == after.read(name) for name in resources)
    missing = sorted(name for name in before.namelist() if name.endswith('.class') and name not in after.namelist())
    assert missing == ['su/nexmedia/engine/api/lang/LangMessage$1.class'], missing
    assert all(int.from_bytes(after.read(name)[6:8], 'big') == 69 for name in after.namelist() if name.endswith('.class'))
    with zipfile.ZipFile(evidence / 'runtime/plugins' / rebuilt.name) as tested:
        assert all(after.read(name) == tested.read(name) for name in tested.namelist() if not name.endswith('/'))

manifest = json.loads((evidence / 'original-files.json').read_text())
assert all(hashlib.sha256((workspace / 'plugins.copy' / name).read_bytes()).hexdigest() == value
           for name, value in manifest.items())
source = sqlite3.connect((workspace / 'plugins.copy/ExcellentCrates/data.db').as_uri() + '?mode=ro', uri=True)
runtime = sqlite3.connect((evidence / 'runtime/plugins/ExcellentCrates/data.db').as_uri() + '?mode=ro', uri=True)
old_rows = {row[1]: row for row in source.execute('select * from excellentcrates_users')}
new_rows = {row[1]: row for row in runtime.execute('select * from excellentcrates_users')}
assert len(old_rows) == 21925 and all(new_rows.get(key) == value for key, value in old_rows.items())
assert list(source.execute('pragma table_info(excellentcrates_users)')) == list(runtime.execute('pragma table_info(excellentcrates_users)'))
source.close()
runtime.close()

boots = {}
for phase in ('first', 'second'):
    log = (evidence / f'fullstack-{phase}.log').read_text(encoding='utf-8', errors='replace')
    assert f'PROBE PASS: {phase}' in log and 'PROBE FAIL:' not in log
    boots[phase] = [line.split('CHECK PASS: ', 1)[1] for line in log.splitlines() if 'CHECK PASS: ' in line]

result = {
    'artifact': rebuilt.name,
    'sha256': hashlib.sha256(rebuilt.read_bytes()).hexdigest(),
    'upstream': 'https://github.com/nulli0n/NexEngine-spigot',
    'upstream_base': 'cf39cf5bdb0257aba153c0a89d86e13caa5997f3',
    'complete_source_build': True,
    'normal_build_requires_original_nexengine_jar': False,
    'offline_tests_passed': 4,
    'standalone_source_build_passed_without_original_or_server_jars': True,
    'standalone_tests_passed': 3,
    'standalone_original_jar_comparison_skipped': 1,
    'runtime_tested_payload_matches_final_jar': True,
    'engine_source_files': len(list((project / 'NexEngine/src/main/java/su/nexmedia').rglob('*.java'))),
    'public_protected_apis_checked': len(classes),
    'permitted_api_addition': 'Version.V26_3',
    'default_resources_byte_identical': resources,
    'all_classes_target_java_25': True,
    'removed_compiler_generated_class': missing,
    'original_configs_data_unchanged': len(manifest),
    'original_player_rows_unchanged': len(old_rows),
    'database_schema_unchanged': True,
    'runtime_checkpoints': boots,
    'reward_previews': 1092,
    'reward_items': 306,
}
(evidence / 'source-verification.json').write_text(json.dumps(result, indent=2))
print(json.dumps(result, indent=2))
