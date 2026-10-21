"""Record build identity and installed dependency metadata, not a legal sign-off."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess


def digest(p): return hashlib.sha256(p.read_bytes()).hexdigest()


def record(source, image):
    sha = subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'HEAD'], text=True).strip()
    vcpkg = subprocess.check_output(['git', '-C', str(source / 'thirdparty/vcpkg'), 'rev-parse', 'HEAD'], text=True).strip()
    installed = source / 'build/release/vcpkg_installed'
    notices = {str(p.relative_to(installed)): digest(p) for p in sorted(installed.glob('*/share/*/copyright'))}
    if not notices: raise ValueError('Installed dependency license files not found')
    binary = source / 'build/release/basalt_vio'
    return {'schema': 1, 'project_source_sha': os.environ.get('SOURCE_SHA'),
            'engine_source_sha': sha, 'vcpkg_source_sha': vcpkg,
            'upstream_manifest_sha256': digest(source / 'vcpkg.json'),
            'registry_config_sha256': digest(source / 'vcpkg-configuration.json'),
            'cmake_cache_sha256': digest(source / 'build/release/CMakeCache.txt'),
            'release_triplet_sha256': digest(Path(__file__).with_name('x64-linux-release.cmake')),
            'host_image': image.strip(), 'image_pin_policy': 'resolved digest recorded at first attempt; tag not pre-pinned',
            'engine_binary_sha256': digest(binary), 'dependency_license_file_sha256': notices,
            'build_options': {'scalar': 'float', 'jobs': 2, 'target': 'basalt_vio', 'march': 'x86-64'},
            'license_review': 'UNREVIEWED_TRANSITIVES; no binary redistribution or APK packaging',
            'source_changes': subprocess.check_output(['git', '-C', str(source), 'status', '--porcelain'], text=True).splitlines()}


if __name__ == '__main__':
    p = argparse.ArgumentParser(); p.add_argument('--source', type=Path, required=True)
    p.add_argument('--image-file', type=Path, required=True); p.add_argument('--out', type=Path, required=True)
    a = p.parse_args(); a.out.write_text(json.dumps(record(a.source, a.image_file.read_text()), indent=2)+'\n')
