"""First acquisition from the official EuRoC HTTPS endpoint, with an observed hash.

This is NOT a previously checksum-pinned download. Subsequent evidence uses the
observed SHA256; a successful first acquisition is not source authentication.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import urllib.request
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'tools' / 'benchmark'))
from fetch import unpack_zip

URL = 'https://robotics.ethz.ch/~asl-datasets/ijrr_euroc_mav_dataset/vicon_room2/V2_01_easy/V2_01_easy.zip'


def acquire(out):
    out = Path(out); out.mkdir(parents=True, exist_ok=False)
    archive = out / 'V2_01_easy.zip'; total = 0; h = hashlib.sha256()
    req = urllib.request.Request(URL, headers={'User-Agent': 'VECTOR-Basalt-research/1'})
    with urllib.request.urlopen(req, timeout=60) as response, archive.open('wb') as f:
        actual = response.geturl()
        if urlparse(actual).scheme != 'https': raise ValueError('TLS downgrade')
        while block := response.read(1024 * 1024):
            total += len(block)
            if total > 3 * 1024**3: raise ValueError('Archive exceeds 3 GiB cap')
            f.write(block); h.update(block)
    extraction = unpack_zip(archive, out / 'raw', max_bytes=8 * 1024**3)
    record = {'dataset': 'EuRoC V2_01_easy', 'requested_url': URL, 'resolved_url': actual,
              'archive_bytes': total, 'archive_sha256': h.hexdigest(), 'extraction': extraction,
              'hash_status': 'OBSERVED_AT_FIRST_ACQUISITION_NOT_PREPINNED',
              'redistribution': 'original bytes stay temporary; no upload'}
    (out / 'source.json').write_text(json.dumps(record, indent=2)+'\n')
    return record


if __name__ == '__main__':
    p = argparse.ArgumentParser(); p.add_argument('--out', type=Path, required=True)
    print(json.dumps(acquire(p.parse_args().out)))
