"""Fixed Yuuka/Toki metal tint regression captures; isolated world only."""
from debug import Client
from pathlib import Path
import hashlib, json, shutil, sys, time, zipfile

c = Client(sys.argv[1]); out = Path(sys.argv[2]); label = sys.argv[3]
out.mkdir(parents=True, exist_ok=True)
rows = []
mods=list((Path(sys.argv[1])/'mods').glob('halo*.jar'))
assert len(mods)==1, 'Material control requires exactly one Halo jar'
shader=zipfile.ZipFile(mods[0]).read('assets/halo/shaders/caustica/halo_surface.slang')
tinted=b'surface.base_color=color*saturate' in shader
assert tinted == (not label.startswith('untinted')), 'Wrong shader expression in installed jar'
end=time.monotonic()+120
while True:
    status=c.call('status')
    if status.get('ready') and status.get('frameActive') and status.get('terrainOutstandingBuilds')==0:break
    if time.monotonic()>end:raise TimeoutError('RT world did not become ready')
    time.sleep(1)
time.sleep(10)
c.call('command', command='execute as @e[tag=halo_rt_bench] run halo hide @s')
c.call('command', command='data merge entity @e[tag=halo_rt_7,limit=1] {Marker:false,Invisible:false}')
c.call('command', command='gamemode spectator @s')
c.call('settings.set', values={'exposure.mode':'auto', 'frame-generation.enabled':False})
for definition in ['yuuka', 'toki']:
    c.call('command', command=f'halo show @e[tag=halo_rt_7,limit=1] millennium:{definition}')
    for moment in [1000, 6000]:
        c.call('command', command=f'time set {moment}')
        c.call('command', command='tp @s -1.0 111.5 7.2 180 0')
        for view in ['off', 'albedo']:
            c.call('view.set', name=view)
            time.sleep(5)
            status=c.call('status')
            result=c.call('screenshot')
            target=out/f'{label}-{definition}-{moment}-{view}.png'
            shutil.copy2(result['path'], target)
            rows.append(dict(file=target.name, status=status, frameId=result['frameId']))
            print(target.name, flush=True)
c.call('view.set', name='off')
(out/f'{label}-capture.json').write_text(json.dumps(dict(
    haloJar=str(mods[0]), haloSha256=hashlib.sha256(mods[0].read_bytes()).hexdigest(),
    tinted=tinted, captures=rows), indent=2), encoding='utf-8')
