"""Local Caustica loopback client. Never print its session bearer token."""
import json,time,urllib.request,sys
from pathlib import Path
class Client:
 def __init__(self,game):
  self.game=Path(game);self.session=json.loads((self.game/'caustica-debug/session.json').read_text(encoding='utf-8'))
 def post(self,body):
  req=urllib.request.Request(self.session['baseUrl']+'/api',data=json.dumps(body).encode(),headers={'Authorization':'Bearer '+self.session['token'],'Content-Type':'application/json'})
  return json.loads(urllib.request.urlopen(req,timeout=20).read())
 def call(self,op,timeout=120,**values):
  result=self.post(dict(op=op,**values))
  if 'jobId' not in result:return result
  job=result['jobId'];end=time.monotonic()+timeout
  while time.monotonic()<end:
   result=self.post(dict(op='job',jobId=job))['result']
   if result.get('state')=='completed':return result['result']
   if result.get('state')=='failed':raise RuntimeError(result.get('error'))
   time.sleep(.25)
  raise TimeoutError(op)
if __name__=='__main__':
 c=Client(sys.argv[1]);args=json.loads(sys.argv[3]) if len(sys.argv)>3 else {}
 print(json.dumps(c.call(sys.argv[2],**args),ensure_ascii=False,indent=2))
