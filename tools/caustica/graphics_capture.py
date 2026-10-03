"""Capture Seia's silhouette in the existing, isolated RT fixture world.

Requires the user's Seia resource pack and seia_halo fixture definition. Does not
create/delete entities or copy user assets into the repository. Never use on an
original save: this changes the camera, time and fixture halo assignments.
"""
import hashlib,json,shutil,sys,time,zipfile
from pathlib import Path
from debug import Client

game,out,label=Path(sys.argv[1]),Path(sys.argv[2]),sys.argv[3]
pose=json.loads(sys.argv[4]) if len(sys.argv)>4 else [3.0,111.5,7.2,180,0]
out.mkdir(parents=True,exist_ok=True)
jars=list((game/'mods').glob('halo-*.jar'))
assert len(jars)==1, 'Exactly one Halo JAR is required'
with zipfile.ZipFile(jars[0]) as jar:
    shader=jar.read('assets/halo/shaders/caustica/halo_surface.slang').decode()
provenance=dict(jar=str(jars[0]),sha256=hashlib.sha256(jars[0].read_bytes()).hexdigest(),
                normalPolicy=('continuous-reflection' if 'reflectionSafeNormal' in shader else
                              'triangle-fallback' if 'if(dot(current,input.outgoingDirection)<=0)' in shader else 'unconstrained'),pose=pose)
c=Client(game)
provenance['initialStatus']=c.call('status')
c.call('settings.set',values={'exposure.mode':'auto'})
c.call('view.set',name='off')
for command in ['gamemode spectator','time set 6000','tp @s '+' '.join(str(v) for v in pose),
                'halo hide @e[tag=halo_rt_8,limit=1]',
                'halo show @e[tag=halo_rt_9,limit=1] halo_rt:seia_halo']:
    c.call('command',command=command)
time.sleep(10)
for view in ['off','normals','trace-radiance']:
    c.call('view.set',name=view);time.sleep(3)
    shot=c.call('screenshot');shutil.copy2(shot['path'],out/(label+'-'+view+'.png'))
c.call('view.set',name='off');time.sleep(3)
raw=c.call('image.capture',names=['trace-color','reconstructed-color','normal-roughness','nrd-stable-radiance'])
for item in raw['images']:
    shutil.copy2(item['path'],out/(label+'-'+item['metadata']['name']+'.exr'))
(out/(label+'-raw.json')).write_text(json.dumps(raw,indent=2),encoding='utf-8')
provenance['finalStatus']=c.call('status')
(out/(label+'-provenance.json')).write_text(json.dumps(provenance,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in provenance.items() if k not in ['initialStatus','finalStatus']}))
