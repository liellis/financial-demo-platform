"""Internal fixed-purpose CodeGraph runner. No arbitrary query/script/command endpoint."""
import hashlib,json,os,re,secrets,signal,subprocess,tempfile,time,threading,shutil
from pathlib import Path,PurePosixPath
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer

SOURCE=Path('/data/snapshots');ARTIFACTS=Path('/artifacts');SCRIPT=Path('/runner/export.sc')
LOCK=threading.Lock()
IMAGE='sha256:11b574bc88e8fdf1d7f324cfd9c8e7b696495f5e50a48b633251fa672156a38a'
ENGINE={'name':'Joern CodeGraph/CPG','version':'4.0.662','image_digest':IMAGE,'commit':'ea6ef8502fd856f392b01793591108bfce1bb41a','exporter_sha256':hashlib.sha256(SCRIPT.read_bytes()).hexdigest(),'delombok':'disabled','dependency_fetch':'disabled'}

def digest(value):return hashlib.sha256(json.dumps(value,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def run(args,cwd,log):
    proc=subprocess.Popen(args,cwd=cwd,stdout=log,stderr=subprocess.STDOUT,start_new_session=True,env={**os.environ,'JAVA_OPTS':'-Xmx2g'})
    try:
        if proc.wait(timeout=540)!=0:
            shutil.copy2(log.name,ARTIFACTS/'last-failure.log')
            raise ValueError('JOERN_COMMAND_FAILED')
    except subprocess.TimeoutExpired:
        os.killpg(proc.pid,signal.SIGKILL);proc.wait();raise ValueError('JOERN_TIMEOUT')


def analyze(data):
    sid=data['snapshot_id']
    if not re.fullmatch(r'snapshot_[0-9a-f]{32}',sid):raise ValueError('INVALID_SNAPSHOT')
    root=(SOURCE/sid).resolve()
    if not root.is_relative_to(SOURCE.resolve()) or not root.is_dir():raise ValueError('INVALID_SNAPSHOT')
    if digest(data['files'])!=data['content_hash']:raise ValueError('SOURCE_MANIFEST_MISMATCH')
    java=0
    for entry in data['files']:
        path=PurePosixPath(entry['path'])
        if path.is_absolute() or '..' in path.parts or '\\' in entry['path']:raise ValueError('UNSAFE_PATH')
        file=(root/str(path)).resolve()
        if not file.is_relative_to(root) or hashlib.sha256(file.read_bytes()).hexdigest()!=entry['sha256']:raise ValueError('SOURCE_HASH_MISMATCH')
        java+=path.suffix=='.java'
    if not 0<java<=200:raise ValueError('JAVA_FILE_LIMIT')
    key=digest([sid,data['content_hash'],ENGINE]);target=ARTIFACTS/key
    if (target/'result.json').exists():return json.loads((target/'result.json').read_text())
    ARTIFACTS.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(dir=ARTIFACTS) as temp:
        work=Path(temp)
        with (work/'joern.log').open('wb') as log:
            run(['javasrc2cpg','-J-Xmx2g',str(root),'--output',str(work/'cpg.bin'),'--delombok-mode','no-delombok'],work,log)
            run(['joern','-J-Xmx2g','--script',str(SCRIPT),'--param',f'input={work / "cpg.bin"}','--param',f'output={work / "facts.json"}'],work,log)
        raw=json.loads((work/'facts.json').read_text())
        raw['artifact']={'id':key,'format':'Joern CPG','sha256':hashlib.sha256((work/'cpg.bin').read_bytes()).hexdigest(),'engine':ENGINE}
        result={'snapshot_id':sid,'content_hash':data['content_hash'],'engine':ENGINE,'result':raw}
        (work/'result.json').write_text(json.dumps(result,ensure_ascii=False))
        work.rename(target)
    return result


class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def respond(self,status,data):
        body=json.dumps(data,ensure_ascii=False).encode();self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
    def do_GET(self):
        if self.path!='/health':return self.respond(404,{'error':'NOT_FOUND'})
        self.respond(200,{'status':'ready','engine':ENGINE})
    def do_POST(self):
        token=os.getenv('BOS_CODEGRAPH_TOKEN','')
        if not token or not secrets.compare_digest(self.headers.get('Authorization','').encode(),('Bearer '+token).encode()):return self.respond(401,{'error':'UNAUTHENTICATED'})
        if self.path!='/analyze':return self.respond(404,{'error':'NOT_FOUND'})
        try:
            length=int(self.headers.get('Content-Length','0'))
            if not 0<length<=1024*1024:raise ValueError('BODY_LIMIT')
            data=json.loads(self.rfile.read(length))
            with LOCK:result=analyze(data)
            self.respond(200,result)
        except Exception:
            self.respond(422,{'error':'CODEGRAPH_ANALYSIS_FAILED'})

if __name__=='__main__':ThreadingHTTPServer(('0.0.0.0',8080),Handler).serve_forever()
