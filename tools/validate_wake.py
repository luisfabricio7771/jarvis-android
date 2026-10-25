from pathlib import Path
import sys,wave,json,random,re,hashlib,zipfile
r=Path.cwd();sys.path.insert(0,str(r/'.tools/pylibs'))
from vosk import Model,KaldiRecognizer,SetLogLevel
SetLogLevel(-1)
model_path=r/'.tools/vosk-model-small-es-0.42'
if not model_path.exists():
    archive=r/'app/src/main/assets/wake-model.zip'
    assert hashlib.sha256(archive.read_bytes()).hexdigest()=='09b239888f633ef2f0b4e09736e3d9936acfd810bc65d53fad45261762c6511f'
    with zipfile.ZipFile(archive) as z: z.extractall(r/'.tools')
m=Model(str(model_path))
grammar=json.loads(re.search(r'const val GRAMMAR = (".*")', (r/'app/src/main/java/dev/jarvis/android/wake/WakePhrase.kt').read_text(encoding='utf-8')).group(1))
phrases=json.loads((r/'.tools/wake-fixtures/phrases.json').read_text(encoding='utf-8-sig'))
report=[]
for i,phrase in enumerate(phrases+['silence','clicks']):
    rec=KaldiRecognizer(m,16000,grammar);rec.SetWords(True)
    if i<len(phrases):
        with wave.open(str(r/f'.tools/wake-fixtures/{i}.wav')) as w: pcm=w.readframes(w.getnframes())
    else:
        pcm=bytes(64000) if phrase=='silence' else b''.join((32767 if n%8000==0 else 0).to_bytes(2,'little',signed=True) for n in range(32000))
    results=[]
    for offset in range(0,len(pcm)+32000,1024):
        part=(pcm+bytes(32000))[offset:offset+1024]
        if rec.AcceptWaveform(part):results.append(json.loads(rec.Result()))
    results.append(json.loads(rec.FinalResult()))
    activated=any(w.get('word')=='jarvis' and w.get('conf',0)>=.8 and w.get('end',0)-w.get('start',0)>=.15 for result in results for w in result.get('result',[]))
    item={'phrase':phrase,'expected':i<3,'activated':activated,'results':results};report.append(item)
    print(json.dumps(item,ensure_ascii=True),flush=True)
(r/'.tools/wake-validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
assert all(x['expected']==x['activated'] for x in report),'Wake test failed'
