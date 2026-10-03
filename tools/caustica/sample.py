from debug import Client
from pathlib import Path
import subprocess,json,time,sys
c=Client(sys.argv[1]);out=Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
java=Path('C:/Program Files/Eclipse Adoptium/jdk-25.0.4.7-hotspot/bin')
kind=sys.argv[3];count=int(sys.argv[4]);label=sys.argv[5]
# Remove the previous assignments, preserving the same 128 non-rendering marker entities in every sample.
c.call('command',command='execute as @e[tag=halo_rt_bench] run halo hide @s')
if count:c.call('command',command=f'execute as @e[tag=halo_rt_bench,limit={count},sort=nearest] run halo show @s halo_rt:{kind}')
c.call('command',command='tp @s 0 111 16 180 20')
c.call('screen.close');status=c.call('status')
assert status['window']['width']==1920 and status['window']['height']==1080 and status['window']['fullscreen']
assert status['frameActive'] and not status['settings']['frame-generation.enabled']['value']
print(label,'warmup 10 seconds',flush=True);time.sleep(10)
# All events use the fixed host API; Halo custom events are enabled by default in the profile recording.
c.call('jfr.start')
print(label,'sampling 30 seconds',flush=True);time.sleep(30)
record=c.call('jfr.stop');target=out/(label+'.jfr');Path(record['path']).replace(target)
mem=c.call('memory.capture');Path(mem['path']).replace(out/(label+'-memory.json'))
(out/(label+'-status.json')).write_text(json.dumps(status,indent=2),encoding='utf-8')
subprocess.run([str(java/'java.exe'),'-cp','build/jfr-summary','JfrSummary',str(target),str(out/(label+'.json'))],check=True)
print(label,'complete',flush=True)
