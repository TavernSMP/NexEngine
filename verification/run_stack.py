import hashlib
import json
import os
import shutil
import socket
import sqlite3
import subprocess
import sys
import time
from pathlib import Path

project = Path(__file__).resolve().parents[1]
workspace = project.parents[1]
originals = workspace / 'plugins.copy'
evidence = project / 'evidence'
runtime = evidence / 'runtime'
sys.path.insert(0, str(workspace / '.audit-deps'))
import yaml

def manifest():
    return {str(path.relative_to(originals)): hashlib.sha256(path.read_bytes()).hexdigest()
            for name in ('ExcellentCrates', 'NexEngine', 'TavernCrateBooster')
            for path in (originals / name).rglob('*') if path.is_file()}

if '--prepare' in sys.argv:
    if runtime.exists():
        raise SystemExit('Runtime already exists; preserve it and use a new evidence directory.')
    runtime.mkdir(parents=True)
    bootstrap = workspace / 'projects/TavernCrateBooster/evidence/runtime'
    shutil.copy2(bootstrap / 'paper.jar', runtime / 'paper.jar')
    for name in ('libraries', 'versions', 'cache'):
        shutil.copytree(bootstrap / name, runtime / name)
    plugins = runtime / 'plugins'
    plugins.mkdir()
    for name in ('ExcellentCrates', 'NexEngine', 'TavernCrateBooster'):
        shutil.copytree(originals / name, plugins / name, ignore=shutil.ignore_patterns('*.log', 'data.db', '*.db-wal', '*.db-shm'))
    source = sqlite3.connect((originals / 'ExcellentCrates/data.db').as_uri() + '?mode=ro', uri=True)
    target = sqlite3.connect(plugins / 'ExcellentCrates/data.db')
    source.backup(target)
    target.close()
    source.close()
    config_file = plugins / 'ExcellentCrates/config.yml'
    config = yaml.safe_load(config_file.read_text(encoding='utf-8'))
    config['Database']['Type'] = 'SQLITE'
    config['Database']['Purge']['Enabled'] = False
    config_file.write_text(yaml.safe_dump(config, allow_unicode=True, sort_keys=False), encoding='utf-8')
    (runtime / 'eula.txt').write_text('eula=true\n')
    (runtime / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25686\nonline-mode=false\n'
        'enforce-secure-profile=false\nmax-players=2\nview-distance=2\nsimulation-distance=2\n'
        'spawn-protection=0\nlevel-type=minecraft:flat\ngenerate-structures=false\npause-when-empty-seconds=-1\n'
        'white-list=false\nenforce-whitelist=false\n')
    boost_config = plugins / 'TavernCrateBooster/config.yml'
    boost_config.write_text(boost_config.read_text(encoding='utf-8').replace('enforce-interval-seconds: 10', 'enforce-interval-seconds: 1'), encoding='utf-8')
    (evidence / 'original-files.json').write_text(json.dumps(manifest(), indent=2))
    print('Prepared isolated copies; original files remain unchanged.', flush=True)

if '--run' in sys.argv:
    with socket.socket() as check:
        if check.connect_ex(('127.0.0.1', 25686)) == 0:
            raise SystemExit('Dedicated test port 25686 is occupied.')
    plugins = runtime / 'plugins'
    for previous in plugins.glob('NexEngine-*.jar'):
        previous.unlink()
    for jar in (project / 'build/libs').glob('*.jar'):
        shutil.copy2(jar, plugins / jar.name)
    shutil.copy2(originals / 'ExcellentCrates.jar', plugins / 'ExcellentCrates.jar')
    for previous in plugins.glob('TavernCrateBooster-*.jar'):
        previous.unlink()
    shutil.copy2(workspace / 'projects/TavernCrateBooster/build/libs/TavernCrateBooster-1.0.2.jar', plugins)
    if '--probe' in sys.argv:
        shutil.copy2(project / 'build/probe/CratesStackProbe.jar', plugins)
    log_file = evidence / ('runtime-probe.log' if '--probe' in sys.argv else 'runtime-startup.log')
    client = None
    with log_file.open('w', encoding='utf-8') as log:
        process = subprocess.Popen(['java', '-Xms256m', '-Xmx1536m', '-XX:ActiveProcessorCount=2',
            '-Dterminal.jline=false', '-Dterminal.ansi=false', '-Djava.io.tmpdir=' + str(evidence / 'tmp'),
            '-jar', 'paper.jar', '--nogui'], cwd=runtime, stdin=subprocess.PIPE,
            stdout=log, stderr=subprocess.STDOUT, text=True, creationflags=0x08000000)
        try:
            deadline = time.monotonic() + 180
            while process.poll() is None and time.monotonic() < deadline:
                output = log_file.read_text(encoding='utf-8', errors='replace')
                if '--probe' in sys.argv and 'STARTUP READY FOR CLIENT' in output and client is None:
                    client_log = (evidence / 'client.log').open('w', encoding='utf-8')
                    client = subprocess.Popen([os.environ['CRATES_NODE'], str(project / 'verification/client.cjs'),
                        str(workspace / 'compatibility-26.3/runtime/client/node_modules')],
                        stdout=client_log, stderr=subprocess.STDOUT, creationflags=0x08000000)
                if 'PROBE FAIL:' in output:
                    break
                if client is not None and client.poll() is not None and 'PROBE PASS:' not in output:
                    break
                if '--probe' not in sys.argv and 'Done (' in output:
                    time.sleep(5)
                    break
                time.sleep(1)
        finally:
            if process.poll() is None:
                process.stdin.write('stop\n')
                process.stdin.flush()
                try:
                    process.wait(timeout=40)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
            if client is not None:
                try:
                    client.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    client.kill()
                    client.wait()
                client_log.close()
    output = log_file.read_text(encoding='utf-8', errors='replace')
    selected = [line for line in output.splitlines() if any(marker in line for marker in (
        'Enabling ', 'Loaded ', 'ERROR', 'Caused by:', 'Exception:', 'CHECK PASS', 'PROBE PASS', 'PROBE FAIL'))]
    print('\n'.join(selected[-60:]), flush=True)
    baseline = json.loads((evidence / 'original-files.json').read_text())
    assert manifest() == baseline, 'An original input file changed.'
    print('Original configs, data and booster files match their pre-test hashes.', flush=True)
    if '--probe' in sys.argv:
        assert 'PROBE PASS:' in output and 'PROBE FAIL:' not in output, 'Probe did not pass; inspect the runtime/client logs.'
