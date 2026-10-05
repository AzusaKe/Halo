"""Actual OpenGL compile/link and deterministic shader execution (task-local moderngl)."""
import json
import pathlib
import struct
import sys
import zipfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'.local/ba/python'))
import moderngl
context=moderngl.create_standalone_context(require=330)
pack=ROOT/".local/ba/delivery/Halo BA Experimental (Chocapic13' Shaders edit).zip"
with zipfile.ZipFile(pack) as archive: library=archive.read('shaders/lib/halo_ba.glsl').decode()
library=library.replace('texture2D(', 'texture(')
vertex='''#version 330
void main() { vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2); gl_Position=vec4(p*2.0-1.0,0,1); }
'''
fragment='#version 330\n'+library+'''
out vec4 result;
uniform vec3 testBase;
uniform vec4 testMask;
uniform vec4 testSpec;
uniform vec3 testIllumination;
uniform vec3 testAmbient;
uniform float testExposure;
void main() {
    result=vec4(baEvaluate(testBase,testMask,testSpec,testIllumination,testAmbient,testExposure,vec3(0,0,1),vec3(0,0,1)),1);
}
'''
program=context.program(vertex_shader=vertex,fragment_shader=fragment)
target=context.simple_framebuffer((1,1),components=4,dtype='f4')
target.use(); vao=context.vertex_array(program,[])
params=json.loads((ROOT/'src/main/resources/assets/halo/ba/parameters-v1.json').read_text())
profiles=json.loads((ROOT/'core/src/main/resources/network/azusake/halo/ba/arisu-v1.json').read_text())['profiles']
def name(s):
    import re
    return 'HaloBA_'+('mask_default' if s=='mask' else re.sub('[^a-zA-Z0-9_]','_',s))
def setvalue(key,value):
    if key in program: program[key].value=tuple(value) if isinstance(value,list) else value
def render(kind,profile,base,mask,spec,light,illumination=[1,1,1],ambient=[1,1,1],exposure=1):
    for key,value in params.items(): setvalue(key,value)
    for key,value in profiles[profile].items(): setvalue(name(key),value)
    setvalue('HaloBA_type',kind);setvalue('HaloBA_hasMask',1);setvalue('HaloBA_hasSpec',1)
    setvalue('HaloBA_fixedLight',light)
    program['HaloBA_viewToReference'].write(struct.pack('9f',1,0,0,0,1,0,0,0,1))
    setvalue('HaloBA_objectDirection',[0,0,1])
    setvalue('testBase',base);setvalue('testMask',mask);setvalue('testSpec',spec)
    setvalue('testIllumination',illumination)
    setvalue('testAmbient',ambient);setvalue('testExposure',exposure)
    vao.render(vertices=3)
    return struct.unpack('4f',target.read(components=4,dtype='f4'))[:3]
checks=[]
def close(actual,expected,label):
    error=max(abs(a-b) for a,b in zip(actual,expected))
    if error>2e-5: raise AssertionError((label,actual,expected,error))
    checks.append({'case':label,'actual':actual,'expected':expected,'max_error':error})
close(render(7,'unlit',[.1,.2,.3],[0,0,0,1],[.5,.5,1,0],0),[.1,.2,.3],'unlit ignores environment')
close(render(6,'halo',[.1,.2,.3],[0,0,0,1],[.5,.5,1,0],0),[.05,.1,.15],'halo exact current emission .5')
close(render(6,'halo',[0,0,0],[0,0,0,1],[.5,.5,1,0],1),[0,0,0],'halo near-black replacement')
close(render(1,'body',[.7,.8,.9],[0,0,.5,1],[.5,.5,1,0],1),[.7,.8,.9],'body fully lit base')
close(render(4,'hair',[.1,.2,.3],[0,.5,0,1],[.5,.5,1,0],1),[.1,.2,.3],'hair Spec Alpha disables highlight')
close(render(4,'hair',[.1,.2,.3],[0,.5,0,1],[.5,.5,1,0],0),[.075,.15,.225],'hair shadow HSV value .5 mix .5')
close(render(1,'body',[.7,.8,.9],[0,0,.5,1],[.5,.5,1,0],-1,[2,2,2],[2,2,2]),[1.4,1.6,1.8],'body emission follows overall environment brightness')
close(render(4,'hair',[.1,.2,.3],[0,.5,0,1],[.5,.5,1,0],-1,[2,2,2],[2,2,2]),[.2,.4,.6],'hair emission follows overall environment brightness')
close(render(1,'body',[.7,.8,.9],[0,0,.5,1],[.5,.5,1,0],-1,[0,0,0],[0,0,0]),[0,0,0],'body is adapted to total darkness')
close(render(7,'unlit',[.1,.2,.3],[0,0,0,1],[.5,.5,1,0],-1,[8,4,2],[.2,.2,.2]),[.02,.04,.06],'unlit follows shared gain without directional lighting')
close(render(8,'unlit',[.1,.2,.3],[0,0,0,1],[.5,.5,1,0],-1,[8,4,2],[0,0,0]),[0,0,0],'outline has no emission in total darkness')
close(render(7,'unlit',[.1,.2,.3],[0,0,0,1],[.5,.5,1,0],-1,[0,0,0],[0,0,0]),[0,0,0],'eyes and mouth have no emission in total darkness')
for exposure in [.1,1,2]:
    actual=render(6,'halo',[.1,.2,.3],[0,0,0,1],[.5,.5,1,0],-1,[0,0,0],[0,0,0],exposure)
    close(tuple(v*exposure for v in actual),[.002,.004,.006],'halo emission floor stays stable across exposure '+str(exposure))
colored=[.2,.4,.8]
gain=sum(v*w for v,w in zip(colored,[.2126,.7152,.0722]))
for kind,profile,base,mask in [(1,'body',[.7,.8,.9],[0,0,.5,1]),(4,'hair',[.1,.2,.3],[0,.5,0,1]),(7,'unlit',[.1,.2,.3],[0,0,0,1])]:
    close(render(kind,profile,base,mask,[.5,.5,1,0],-1,[8,4,2],colored),[v*gain for v in base],profile+' colored environment preserves artist chromaticity')
close(render(1,'body',[.7,.8,.9],[0,0,.5,1],[.5,.5,1,0],-1,[8,8,8],[1,1,1]),[.7,.8,.9],'body does not receive a second diffuse multiply after toon palette selection')
report={'renderer':context.info['GL_RENDERER'],'version':context.info['GL_VERSION'],'actual_glsl_compile_link':'passed','cases':checks}
(ROOT/'.local/ba/delivery/glsl-results.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
