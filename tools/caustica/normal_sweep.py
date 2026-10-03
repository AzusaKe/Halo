"""Capture nearby Seia views in the already prepared isolated fixture world.

Requires graphics3/user-initial-status.json (or equivalent initial capture).
Never use on an original save. Camera and creative mode are restored at the end.
"""
import json,math,shutil,sys,time
from pathlib import Path
from debug import Client

game,out=Path(sys.argv[1]),Path(sys.argv[2])
c=Client(game);records=[]
initial=json.loads((out/'user-initial-status.json').read_text(encoding='utf-8'))
c.call('view.set',name='off')
try:
    for k,x in enumerate([2.6,2.75,2.9,3.05,3.2,3.35,3.5]):
        z=5.0635223159544624;dx=3-x;dz=6-z;dy=113.29-113.6751596327445
        yaw=-math.degrees(math.atan2(dx,dz));pitch=-math.degrees(math.atan2(dy,math.hypot(dx,dz)))
        pose=[x,112.05515974718541,z,yaw,pitch]
        c.call('command',command='tp @s '+' '.join(str(v) for v in pose));time.sleep(2)
        shot=c.call('screenshot');shutil.copy2(shot['path'],out/f'smooth-sweep-{k}.png')
        raw=c.call('image.capture',names=['normal-roughness','nrd-stable-radiance'])
        for item in raw['images']:
            shutil.copy2(item['path'],out/(f'smooth-sweep-{k}-'+item['metadata']['name']+'.exr'))
        records.append(dict(pose=pose,raw=raw,status=c.call('status')))
        print('sweep',k,flush=True)
finally:
    (out/'smooth-sweep.json').write_text(json.dumps(records,indent=2),encoding='utf-8')
    p=initial['player'];pose=[p[key] for key in ['x','y','z','yaw','pitch']]
    c.call('command',command='gamemode creative')
    c.call('command',command='tp @s '+' '.join(str(v) for v in pose))
    c.call('command',command='halo show @e[tag=halo_rt_8,limit=1] millennium:yuuka')
    c.call('view.set',name='off')
    c.call('settings.set',values={'exposure.mode':'auto'})
    print('restored')
