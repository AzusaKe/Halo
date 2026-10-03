"""Tracking-only unload regression in the isolated benchmark world."""
from debug import Client
from pathlib import Path
import json, subprocess, sys, time

c = Client(sys.argv[1])
out = Path(sys.argv[2]); out.mkdir(parents=True, exist_ok=True)
java = 'C:/Program Files/Eclipse Adoptium/jdk-25.0.4.7-hotspot/bin/java.exe'
results = []

def record(label):
    c.call('jfr.start')
    time.sleep(3)
    result = c.call('jfr.stop')
    target = out / (label + '.jfr')
    Path(result['path']).replace(target)
    summary = out / (label + '.json')
    subprocess.run([java, '-cp', 'build/jfr-summary', 'JfrSummary', str(target), str(summary)], check=True)
    data = json.loads(summary.read_text(encoding='utf-8'))
    results.append(dict(label=label, instancesMin=data['instancesMin'], instancesMax=data['instancesMax'],
                        resourcesFirst=data['resourcesFirst'], resourcesLast=data['resourcesLast']))
    print(label, data['instancesMin'], data['instancesMax'], flush=True)

c.call('command', command='execute as @e[tag=halo_rt_bench] run halo hide @s')
c.call('command', command='tp @s 0.5 111 16.5 180 20')
c.call('command', command='halo show @e[tag=halo_rt_bench,limit=1,sort=nearest] halo_rt:mesh')
time.sleep(5)
record('tracking-fixed-before')
for cycle in range(1, 4):
    c.call('command', command='tp @s 0.5 111 216.5 180 20')
    time.sleep(5)
    record(f'tracking-fixed-{cycle}-away')
    c.call('command', command='tp @s 0.5 111 16.5 180 20')
    time.sleep(5)
    record(f'tracking-fixed-{cycle}-returned')
(out / 'tracking-fixed-results.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
assert results[0]['instancesMin'] == 1
for result in results[1:]:
    assert result['instancesMin'] == result['instancesMax'] == (0 if result['label'].endswith('away') else 1), result
    assert result['resourcesFirst'][:3] == results[0]['resourcesFirst'][:3], result
print('Three tracking-only return cycles passed; no geometry/texture upload or BLAS preparation.', flush=True)
