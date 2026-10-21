"""Checksum-pinned EuRoC mirror after the official endpoint timed out in CI.

Mirror-byte integrity is verified; equivalence to institutional original bytes
has not independently been authenticated. Original V1_01 attitude has a caveat.
"""
import argparse
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'tools' / 'benchmark'))
from fetch import checked_download, unpack_zip

MIRROR_COMMIT = 'd05bd6a92a5f456562153f83d65805fea27cb57d'
URL = f'https://huggingface.co/datasets/pepijn223/euroc-mirror/resolve/{MIRROR_COMMIT}/V1_01_easy.zip'
SHA256 = 'a920fe5b5e69a6ad19b32f1cfaf90ac2f59d45dfd1ca18cc9722e07684ba45fa'
EXPECTED_BYTES = 1149702102


def acquire(out):
    out = Path(out); out.mkdir(parents=True, exist_ok=False)
    archive = out / 'V1_01_easy.zip'
    total = checked_download(URL, SHA256, archive, 2 * 1024**3)
    if total != EXPECTED_BYTES: raise ValueError('Pinned source byte count mismatch')
    extraction = unpack_zip(archive, out / 'raw', max_bytes=8 * 1024**3)
    record = {'dataset': 'EuRoC V1_01_easy', 'requested_url': URL, 'mirror_commit': MIRROR_COMMIT,
              'archive_bytes': total, 'archive_sha256': SHA256, 'extraction': extraction,
              'hash_status': 'VERIFIED_PINNED_MIRROR_SHA256_NOT_INSTITUTIONALLY_AUTHENTICATED',
              'selection_reason': 'Official V2_01 endpoint timed out before any engine accuracy was measured',
              'reference_attitude_quality': 'Original V1_01 has a published attitude-accuracy caveat; diagnostic scores only',
              'redistribution': 'original bytes stay temporary; no upload'}
    (out / 'source.json').write_text(json.dumps(record, indent=2)+'\n')
    return record


if __name__ == '__main__':
    p = argparse.ArgumentParser(); p.add_argument('--out', type=Path, required=True)
    print(json.dumps(acquire(p.parse_args().out)))
