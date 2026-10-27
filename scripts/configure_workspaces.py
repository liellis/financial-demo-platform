"""Register host-only roots, create signing keys, or export a recent observation."""
import argparse
import json
import os
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from backend.workspace import collect


def private_write(path,content):
    path.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
    fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
    with os.fdopen(fd,'w') as stream:stream.write(content)
    path.chmod(0o600)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository-id');parser.add_argument('--root',type=Path)
    parser.add_argument('--collect',action='store_true');parser.add_argument('--release-id');parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    private=ROOT/'.data/m2/workspace-signing.pem';public=ROOT/'.data/m2/runtime/workspace-public-key.pem';config=ROOT/'.data/m2/workspaces.json'
    if not private.exists():
        if public.exists():raise SystemExit('Registered public key exists but host private key is missing; explicit recovery/rotation required.')
        key=Ed25519PrivateKey.generate()
        private_write(private,key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()).decode())
    key=serialization.load_pem_private_key(private.read_bytes(),password=None)
    pem=key.public_key().public_bytes(serialization.Encoding.PEM,serialization.PublicFormat.SubjectPublicKeyInfo).decode()
    if public.exists() and public.read_text()!=pem:raise SystemExit('Signing key mismatch; refusing implicit trust-key rotation.')
    private_write(public,pem)
    data=json.loads(config.read_text()) if config.exists() else {'private_key_file':str(private),'repositories':{}}
    if args.root:
        if not args.repository_id:parser.error('--root requires --repository-id')
        if not args.root.is_absolute() or args.root.is_symlink() or not args.root.is_dir():parser.error('An existing absolute directory without a symlink root is required')
        data['repositories'][args.repository_id]={'path':str(args.root.resolve())}
    private_write(config,json.dumps(data,indent=2))
    if args.collect:
        if not all((args.repository_id,args.release_id,args.output)):parser.error('--collect requires --repository-id, --release-id and --output')
        report=collect(args.repository_id,args.release_id,config)
        private_write(args.output,json.dumps([report],indent=2))
        print('Recent signed observation exported. Valid for 300 seconds; no source content included.')
    else:print('Host collector configured. Private key and directory mappings remain outside the container runtime mount.')

if __name__=='__main__':main()
