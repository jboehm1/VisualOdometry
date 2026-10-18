#!/usr/bin/env python3
"""Manual-only immutable HTTPS asset acquisition for GitHub Actions/local use.

Archive checks: pinned SHA256, bounded download/expansion, no paths outside
destination, no symlinks. Never auto-download restricted/noncommercial corpora.
"""
import argparse
import hashlib
import os
import re
import shutil
import stat
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path


def checked_download(url, sha, out, limit):
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme != "https" or not parsed.netloc:
        raise ValueError("Only explicitly provided HTTPS downloads are allowed")
    if not re.fullmatch(r"[a-fA-F0-9]{64}", sha):
        raise ValueError("A complete 64-character SHA256 is required")
    out = Path(out)
    out.parent.mkdir(parents=True, exist_ok=True)
    temporary = out.with_name(out.name + ".partial")
    digest = hashlib.sha256()
    size = 0
    try:
        with urllib.request.urlopen(urllib.request.Request(
                url, headers={"User-Agent":"VECTOR-benchmark/1.0"}), timeout=45) as response:
            if urllib.parse.urlparse(response.geturl()).scheme != "https":
                raise ValueError("Download redirected to non-HTTPS URL")
            with temporary.open("wb") as destination:
                while block := response.read(1024*1024):
                    size += len(block)
                    if size > limit:
                        raise ValueError("Download larger than limit")
                    destination.write(block)
                    digest.update(block)
        if digest.hexdigest().lower() != sha.lower():
            raise ValueError("SHA256 mismatch; downloaded data rejected")
        os.replace(temporary, out)
    finally:
        temporary.unlink(missing_ok=True)
    return size


def unpack_zip(archive, destination, max_bytes=12*1024**3, max_files=300000):
    destination = Path(destination)
    destination.mkdir(parents=True, exist_ok=True)
    total = 0
    with zipfile.ZipFile(archive) as content:
        members = content.infolist()
        if len(members) > max_files:
            raise ValueError("Too many archive entries")
        for item in members:
            name = item.filename.replace("\\", "/")
            pieces = name.rstrip("/").split("/")
            if (not name or name.startswith("/") or any(p in ("", ".", "..") for p in pieces)
                    or re.match(r"^[a-zA-Z]:", name)):
                raise ValueError(f"Unsafe path in ZIP: {name}")
            if stat.S_ISLNK((item.external_attr >> 16) & 0xffff):
                raise ValueError("ZIP symlinks forbidden")
            total += item.file_size
            if total > max_bytes:
                raise ValueError("ZIP uncompressed size exceeds limit")
            resolved = (destination / name).resolve()
            if not resolved.is_relative_to(destination.resolve()):
                raise ValueError("Archive path escapes target directory")
        for item in members:
            target = destination / item.filename
            if item.is_dir():
                target.mkdir(parents=True, exist_ok=True)
                continue
            target.parent.mkdir(parents=True, exist_ok=True)
            with content.open(item, "r") as src, target.open("wb") as dst:
                shutil.copyfileobj(src, dst, length=1024*1024)
    return {"files":len(members), "uncompressed_bytes":total}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("url")
    p.add_argument("--sha256", required=True)
    p.add_argument("--out", type=Path, required=True)
    p.add_argument("--max-download-gb", type=float, default=4)
    p.add_argument("--unzip-to", type=Path)
    args = p.parse_args()
    size = checked_download(args.url, args.sha256, args.out,
                            int(args.max_download_gb*1024**3))
    print(f"verified {args.out}: {size} bytes, sha256={args.sha256.lower()}")
    if args.unzip_to:
        print(unpack_zip(args.out, args.unzip_to))


if __name__ == "__main__":
    main()
