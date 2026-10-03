from debug import Client
from pathlib import Path
import sys,json,time
c=Client(sys.argv[1]);out=Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
def command(text):
 try:return c.call('command',command=text)
 except Exception as e:print(text,str(e),flush=True);return {'error':str(e)}
commands=['gamemode creative @s','gamerule minecraft:advance_time false','gamerule minecraft:advance_weather false','gamerule minecraft:spawn_mobs false','time set noon','weather clear','halo hide @s','kill @e[type=minecraft:armor_stand]','fill -20 110 -25 20 110 18 minecraft:stone','tp @s 0 111 16 180 20']
results={text:command(text) for text in commands}
for n in range(128):
 x=(n%16-7.5)*2;z=6-(n//16)*3
 command(f'summon armor_stand {x} 111 {z} {{Tags:["halo_rt_bench","halo_rt_{n}"],NoGravity:1b,Invulnerable:1b,Invisible:1b,Marker:1b}}')
 if n%16==0:print('Prepared',n,'wearers',flush=True)
c.call('screen.close');c.call('settings.set',values={'frame-generation.enabled':False,'dlss-rr.quality':1,'dlss-rr.preset':5,'denoising.route':'ray_reconstruction','composite.max-bounces':4,'particles.enabled':False,'exposure.mode':'auto'})
c.call('window.fullscreen',enabled=True)
(out/'setup.json').write_text(json.dumps({'commands':results,'status':c.call('status')},indent=2),encoding='utf-8')
print('Fixed scene ready',flush=True)
