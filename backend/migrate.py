"""Lossless, fail-closed M1 backup/export and M2 import. Does not run workers."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import shutil
import sqlite3
from sqlalchemy import text
from .contracts import digest
from .sources import safe_path
from .store import Store

ACTIVE = {'RUNNING','QUEUED','WAITING_RETRY','PAUSE_REQUESTED','CANCEL_REQUESTED'}

def export(source, destination):
    source, destination = Path(source).resolve(), Path(destination).resolve()
    if destination.exists():raise ValueError('Backup destination already exists')
    if destination == source or source in destination.parents:raise ValueError('Backup must be outside the source directory')
    destination.mkdir(parents=True, mode=0o700)
    with sqlite3.connect(f'file:{source / "studio.db"}?mode=ro', uri=True) as src, sqlite3.connect(destination/'studio.db') as dst:
        src.backup(dst)
    with sqlite3.connect(destination/'studio.db') as db:
        rows=db.execute('SELECT kind,id,revision,payload FROM records ORDER BY kind,id').fetchall()
    if any(k=='job' and json.loads(p)['status'] in ACTIVE for k,i,r,p in rows):
        raise ValueError('Active jobs present; pause/settle jobs before migration')
    for name in ('snapshots','credentials'):
        if (source/name).exists():
            if any(p.is_symlink() for p in (source/name).rglob('*')):raise ValueError('Symlinks are not permitted in exported data')
            shutil.copytree(source/name,destination/name)
    if (source/'access-tokens.json').exists():shutil.copy2(source/'access-tokens.json',destination/'access-tokens.json')
    files={str(p.relative_to(destination)):hashlib.sha256(p.read_bytes()).hexdigest() for p in destination.rglob('*') if p.is_file()}
    manifest={'format':'bos-migration-1','records':len(rows),'counts':dict(Counter(row[0] for row in rows)), 'records_digest':digest([[k,i,r,json.loads(p)] for k,i,r,p in rows]),'files':files}
    (destination/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2))
    return manifest


def restore(backup, database_url, runtime):
    backup,runtime=Path(backup).resolve(),Path(runtime).resolve()
    manifest=json.loads((backup/'manifest.json').read_text())
    if manifest['format']!='bos-migration-1':raise ValueError('Unsupported migration format')
    for path,expected in manifest['files'].items():
        resolved=(backup/path).resolve()
        if not resolved.is_relative_to(backup) or hashlib.sha256(resolved.read_bytes()).hexdigest()!=expected:raise ValueError('Backup file mismatch')
    with sqlite3.connect(f'file:{backup / "studio.db"}?mode=ro',uri=True) as db:
        rows=db.execute('SELECT kind,id,revision,payload FROM records ORDER BY kind,id').fetchall()
    if digest([[k,i,r,json.loads(p)] for k,i,r,p in rows])!=manifest['records_digest']:raise ValueError('Record digest mismatch')
    for kind,key,rev,payload in rows:
        value=json.loads(payload)
        if rev<1 or value['id']!=key or value['revision']!=rev:raise ValueError('Invalid authoritative revision')
        if kind=='snapshot':
            for entry in value['files']:
                path=entry['path']
                if safe_path(path)!=path:raise ValueError('Unsafe snapshot path')
                file=backup/'snapshots'/key/path
                if hashlib.sha256(file.read_bytes()).hexdigest()!=entry['sha256']:raise ValueError('Source evidence mismatch')
    runtime.mkdir(parents=True,exist_ok=True,mode=0o700)
    # Copy immutable dependencies first; refuse to replace any differing key or source file.
    for path in manifest['files']:
        if path=='studio.db':continue
        target=runtime/path
        if not target.resolve().is_relative_to(runtime) or target.is_symlink():raise ValueError('Unsafe destination path')
        if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest()!=manifest['files'][path]:raise ValueError('Destination file conflict; nothing overwritten')
    for path in manifest['files']:
        if path=='studio.db':continue
        target=runtime/path;target.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
        if not target.exists():shutil.copy2(backup/path,target)
    store=Store(database_url)
    with store.transaction() as conn:
        existing=conn.execute(text('SELECT kind,id,revision,payload FROM records ORDER BY kind,id')).all()
        if existing:
            if digest([[k,i,r,json.loads(p)] for k,i,r,p in existing])!=manifest['records_digest']:raise ValueError('Destination database is not empty or identical')
        else:
            for k,i,r,p in rows:
                conn.execute(text('INSERT INTO records(kind,id,revision,payload) VALUES(:k,:i,:r,:p)'),{'k':k,'i':i,'r':r,'p':p})
        result=conn.execute(text('SELECT kind,id,revision,payload FROM records ORDER BY kind,id')).all()
        if digest([[k,i,r,json.loads(p)] for k,i,r,p in result])!=manifest['records_digest']:raise ValueError('Post-import digest mismatch')
    return {'verified':True,'records':len(rows),'counts':manifest['counts'],'records_digest':manifest['records_digest']}


def main():
    parser=argparse.ArgumentParser();sub=parser.add_subparsers(dest='command',required=True)
    exp=sub.add_parser('export');exp.add_argument('--source',required=True);exp.add_argument('--destination',required=True)
    imp=sub.add_parser('import');imp.add_argument('--backup',required=True);imp.add_argument('--runtime',required=True)
    args=parser.parse_args()
    if args.command=='export':result=export(args.source,args.destination)
    else:
        import os
        result=restore(args.backup,os.environ['BOS_DATABASE_URL'],args.runtime)
    print(json.dumps({k:v for k,v in result.items() if k!='files'},ensure_ascii=False))

if __name__=='__main__':main()
