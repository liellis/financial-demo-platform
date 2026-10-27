"""Initialize local-only Compose credentials; never overwrite an existing environment."""
import json
import os
from pathlib import Path
import secrets
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def main():
    target = ROOT / '.data/m2/compose.env'
    target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    if target.exists():
        if 'BOS_CODEGRAPH_TOKEN=' not in target.read_text():
            with target.open('a') as out:out.write('BOS_CODEGRAPH_TOKEN='+secrets.token_hex(32)+'\n')
        print('M2 environment already exists; retained.')
        return
    # Pin the locally available official PostgreSQL image by content digest.
    image = json.loads(subprocess.check_output(['docker', 'image', 'inspect', 'postgres:16-alpine']))[0]
    digests = image.get('RepoDigests', [])
    postgres = next((d for d in digests if d.startswith('postgres@sha256:')), None)
    if not postgres:
        raise SystemExit('Pull an official postgres:16-alpine image before setup; no floating tag is written.')
    fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as out:
        out.write(f'POSTGRES_IMAGE={postgres}\nPOSTGRES_PASSWORD={secrets.token_hex(24)}\nNEO4J_PASSWORD={secrets.token_hex(24)}\nBOS_M2_PORT=8002\nBOS_CODEGRAPH_TOKEN={secrets.token_hex(32)}\n')
    (target.parent / 'runtime').mkdir(mode=0o700, exist_ok=True)
    print('Created private M2 environment; credentials were not printed.')

if __name__ == '__main__': main()
