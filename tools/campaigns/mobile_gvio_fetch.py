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

        with zipfile.ZipFile(filename) as bundle:
            for member in bundle.infolist():
                name_in_archive=member.filename
                if name=="Indoor-2.zip" and name_in_archive.endswith(("/indoor_2.bag","/ground_truth.txt")):
                    if member.file_size>4_000_000_000:raise ValueError("Oversized uncompressed source member")
                    dest=out/Path(name_in_archive).name
                    with bundle.open(member) as src,dest.open("wb") as dst:
                        shutil.copyfileobj(src,dst)
                    print("DATASET_MEMBER",dest.name,member.file_size,flush=True)
                elif name=="calib.zip" and name_in_archive.endswith("/orbslam3.yaml"):
                    with bundle.open(member) as src,(out/"orbslam3.yaml").open("wb") as dst:
                        shutil.copyfileobj(src,dst)
        filename.unlink()
    for name in ("indoor_2.bag","ground_truth.txt","orbslam3.yaml"):
        if not (out/name).is_file():raise ValueError("Missing original input "+name)
    (out/"provenance.json").write_text(json.dumps(result,indent=2)+"\n")
    return result

if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--out",type=Path,required=True)
    opts=p.parse_args()
    get_data(opts.out)
