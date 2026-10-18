#!/usr/bin/env python3
"""Pinned-source real smartphone camera+IMU and ground-truth fetcher.

Source archives are only temporary. Never publish the bag or raw images.
"""
import argparse,hashlib,json,shutil,urllib.request,zipfile
from pathlib import Path

SOURCE="https://zenodo.org/api/records/20525157"
PINNED={
 "Indoor-2.zip":("md5:2a5439c7064ecc1a254f8a5790586742",950890027),
 "calib.zip":("md5:35b950dd11f19d0c8f9a967a4687aa5e",3691)
}

def get_data(out):
    out.mkdir(parents=True,exist_ok=True)
    with urllib.request.urlopen(SOURCE,timeout=35) as request: record=json.load(request)
    if record.get("id")!=20525157: raise ValueError("Unexpected research record")
    result={"source":SOURCE,"dataset":"Mobile-GVIO indoor-2","archives":{}}
    for name,(md5,size) in PINNED.items():
        item=next(x for x in record["files"] if x["key"]==name)
        if item["checksum"]!=md5 or item["size"]!=size:
            raise ValueError("Source metadata checksum or length changed")
        h=hashlib.md5();total=0
        filename=out/name
        with urllib.request.urlopen(item["links"]["self"],timeout=150) as remote,filename.open("wb") as dest:
            while block:=remote.read(2*1024*1024):
                total+=len(block)
                if total>size:raise ValueError("Unexpected archive size")
                h.update(block);dest.write(block)
        if total!=size or ("md5:"+h.hexdigest())!=md5:
            raise ValueError("Download checksum mismatch")
        result["archives"][name]=dict(bytes=total,md5=md5)
        print("PINNED_ARCHIVE_VERIFIED",name,total,flush=True)
