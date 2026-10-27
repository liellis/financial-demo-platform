"""Bounded host-side collection and release-bound Ed25519 attestations.

Only the configured host collector reads workspaces; REST accepts no host path.
An attestation proves a recent observation by the registered collector, never
that a workspace will remain unchanged after collection.
"""
import base64
import hashlib
import json
import os
import stat
import time
from pathlib import Path
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey
from .contracts import DomainError, digest
from .sources import EXTENSIONS, EXCLUDED, safe_path

COLLECTOR = 'workspace-bytes-v1'
TTL = 300


def canonical(value):
    return json.dumps(value,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()


def scan(root):
    root=Path(root).resolve(strict=True)
    if not root.is_dir():raise DomainError('WORKSPACE_NOT_DIRECTORY',422)
    files=[];total=0;visited=0
    for parent,dirs,names,dirfd in os.fwalk(root,follow_symlinks=False):
        visited+=len(dirs)+len(names)
        if visited>20000:raise DomainError('WORKSPACE_SCAN_LIMIT',422)
        for name in dirs:
            if name not in EXCLUDED and (Path(parent)/name).is_symlink():
                raise DomainError('WORKSPACE_SYMLINK_FORBIDDEN',422)
        dirs[:]=sorted(d for d in dirs if d not in EXCLUDED)
        for name in sorted(names):
            file=Path(parent)/name
            relative=file.relative_to(root).as_posix()
            if safe_path(relative) is None:continue
            if file.is_symlink():raise DomainError('WORKSPACE_SYMLINK_FORBIDDEN',422)
            fd=os.open(name,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK,dir_fd=dirfd)
            with os.fdopen(fd,'rb') as stream:
                before=os.fstat(stream.fileno())
                if not stat.S_ISREG(before.st_mode):raise DomainError('WORKSPACE_FILE_TYPE_FORBIDDEN',422)
                if before.st_size>1024*1024:raise DomainError('WORKSPACE_FILE_LIMIT',422)
                raw=stream.read(1024*1024+1);after=os.fstat(stream.fileno())
                if len(raw)>1024*1024:raise DomainError('WORKSPACE_FILE_LIMIT',422)
            if (before.st_ino,before.st_size,before.st_mtime_ns)!=(after.st_ino,after.st_size,after.st_mtime_ns):
                raise DomainError('WORKSPACE_CHANGED_DURING_SCAN',409)
            raw.decode('utf-8')
            total+=len(raw)
            if total>25*1024*1024 or len(files)>=1000:raise DomainError('WORKSPACE_SCAN_LIMIT',422)
            files.append({'path':relative,'sha256':hashlib.sha256(raw).hexdigest(),'size':len(raw)})
    if not any(f['path'].endswith('.java') for f in files):raise DomainError('WORKSPACE_NO_JAVA_FILES',422)
    return sorted(files,key=lambda f:f['path'])


def collect(repository_id,release_id,config_file):
    config_path=Path(config_file).resolve(strict=True)
    config=json.loads(config_path.read_text())
    configured=config.get('repositories',{}).get(repository_id)
    if not configured:raise DomainError('WORKSPACE_NOT_CONFIGURED',409)
    # The model supplies only a repository ID; roots and keys come from host config.
    root=Path(configured['path']).expanduser()
    if root.is_symlink():raise DomainError('WORKSPACE_SYMLINK_FORBIDDEN',422)
    if not root.is_absolute():raise DomainError('WORKSPACE_ABSOLUTE_PATH_REQUIRED',422)
    first=scan(root);second=scan(root)
    if first!=second:raise DomainError('WORKSPACE_CHANGED_DURING_SCAN',409)
    key_path=Path(config['private_key_file']).expanduser()
    private=serialization.load_pem_private_key(key_path.read_bytes(),password=None)
    if not isinstance(private,Ed25519PrivateKey):raise DomainError('WORKSPACE_KEY_INVALID',422)
    now=int(time.time())
    payload={'collector':COLLECTOR,'repository_id':repository_id,'release_id':release_id,'content_hash':digest(first),'scope_manifest_hash':digest({'extensions':sorted(EXTENSIONS),'excluded':sorted(EXCLUDED)}),'file_count':len(first),'observed_at':now,'expires_at':now+TTL,'observation_id':os.urandom(16).hex()}
    signature=base64.b64encode(private.sign(canonical(payload))).decode()
    return {'repository_id':repository_id,'attestation':{'payload':payload,'signature':signature}}


def verify(settings,target,release_id):
    envelope=target.get('attestation')
    if not envelope:return None
    if len(canonical(envelope))>4096:raise DomainError('WORKSPACE_ATTESTATION_INVALID_OR_EXPIRED',422)
    payload=envelope.get('payload',{})
    key_path=Path(settings.baseline_public_key_file or settings.data_dir/'workspace-public-key.pem')
    try:
        public=serialization.load_pem_public_key(key_path.read_bytes())
        if not isinstance(public,Ed25519PublicKey):raise ValueError()
        public.verify(base64.b64decode(envelope['signature'],validate=True),canonical(payload))
        now=int(time.time())
        if payload['collector']!=COLLECTOR or payload['repository_id']!=target['repository_id'] or payload['release_id']!=release_id:raise ValueError()
        if not all(isinstance(payload[k],int) and not isinstance(payload[k],bool) for k in ('observed_at','expires_at','file_count')):raise ValueError()
        if payload['observed_at']>now+5 or payload['expires_at']<=now or not 0<payload['expires_at']-payload['observed_at']<=TTL:raise ValueError()
        if not 1<=payload['file_count']<=1000:raise ValueError()
        if any(not isinstance(payload[k],str) or len(payload[k])!=64 for k in ('content_hash','scope_manifest_hash')):raise ValueError()
        if not isinstance(payload['observation_id'],str) or len(payload['observation_id'])!=32:raise ValueError()
    except Exception:
        raise DomainError('WORKSPACE_ATTESTATION_INVALID_OR_EXPIRED',422)
    return payload


def status(settings):
    key_path=Path(settings.baseline_public_key_file or settings.data_dir/'workspace-public-key.pem')
    try:
        public=serialization.load_pem_public_key(key_path.read_bytes())
        if not isinstance(public,Ed25519PublicKey):raise ValueError()
        fingerprint=hashlib.sha256(public.public_bytes(serialization.Encoding.Raw,serialization.PublicFormat.Raw)).hexdigest()
        return {'configured':True,'collector':COLLECTOR,'key_fingerprint':fingerprint,'max_age_seconds':TTL,'scope':{'extensions':sorted(EXTENSIONS),'excluded':sorted(EXCLUDED)}}
    except Exception:return {'configured':False,'collector':COLLECTOR,'max_age_seconds':TTL}
