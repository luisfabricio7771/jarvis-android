from pathlib import Path
import wave,json,hashlib,sys
sys.path.insert(0,str(Path.cwd()/'.tools/pylibs'))
import numpy as np
import onnxruntime as ort
r=Path.cwd();session=ort.InferenceSession(str(r/'app/src/main/assets/silero_vad.onnx'),providers=['CPUExecutionProvider'])
with wave.open(str(r/'.tools/wake-fixtures/0.wav')) as w: voice=np.frombuffer(w.readframes(w.getnframes()),dtype='<i2').astype(np.float32)/32768
rng=np.random.default_rng(42)
clips={'voice':voice,'silence':np.zeros(32000,dtype=np.float32),'white_noise':rng.normal(0,.15,32000).astype(np.float32),'clicks':np.array([.9 if i%4000==0 else 0 for i in range(32000)],dtype=np.float32)}
results={}
for name,samples in clips.items():
    state=np.zeros((2,1,128),dtype=np.float32);context=np.zeros((1,64),dtype=np.float32);scores=[]
    padded=np.pad(samples,(0,(-len(samples))%512))
    for frame in padded.reshape(-1,512):
        data=np.concatenate([context,frame.reshape(1,512)],axis=1)
        score,state=session.run(None,{'input':data,'state':state,'sr':np.array(16000,dtype=np.int64)})
        scores.append(float(score[0,0]));context=frame[-64:].reshape(1,64)
    results[name]={'maxProbability':max(scores),'speechFrames':sum(x>=.7 for x in scores)}
assert results['voice']['speechFrames']>=4
assert all(results[x]['speechFrames']<4 for x in ('silence','white_noise','clicks'))
(r/'.tools/silero-validation.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
print(json.dumps(results),flush=True)
