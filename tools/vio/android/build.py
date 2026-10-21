"""Pinned lean Basalt Android build; excludes desktop/ROS/GUI executables."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile

BASALT='0f3b2b52c807f70ff4e2973ce253c73329eea7bc'
VCPKG='1e199d32ad53aab1defda61ce41c380302e3f95c'
BASELINE='05442024c3fda64320bd25d2251cc9807b84fb6f'
OPENCV='4.10.0'


def run(*args, **kwargs):
    subprocess.run([str(a) for a in args],check=True,**kwargs)


def clone(url,sha,dst):
    if not (dst/'.git').exists():
        run('git','init',dst)
        run('git','-C',dst,'remote','add','origin',url)
    run('git','-C',dst,'fetch','--depth','1','origin',sha)
    run('git','-C',dst,'checkout','--detach',sha)
    actual=subprocess.check_output(['git','-C',str(dst),'rev-parse','HEAD'],text=True).strip()
    if actual!=sha:raise ValueError('Source identity mismatch')


def build(work,abi,ndk):
    root=Path(__file__).resolve().parents[3]
    work=work.resolve();work.mkdir(parents=True,exist_ok=True)
    source=work/'basalt';vcpkg=work/'vcpkg'
    clone('https://github.com/VladyslavUsenko/basalt.git',BASALT,source)
    clone('https://github.com/microsoft/vcpkg.git',VCPKG,vcpkg)
    # The pinned registry baseline must be available for version resolution.
    run('git','-C',vcpkg,'fetch','--depth','1','origin',BASELINE)
    run(vcpkg/'bootstrap-vcpkg.sh','-disableMetrics')
    manifest=work/'manifest';manifest.mkdir(exist_ok=True)
    (manifest/'vcpkg.json').write_text(json.dumps({'name':'vector-basalt-mobile','version-string':'0.1',
        'dependencies':['eigen3','tbb','fmt','magic-enum','nlohmann-json','basalt-headers','opengv']}))
    (manifest/'vcpkg-configuration.json').write_text(json.dumps({'default-registry':{'kind':'builtin','baseline':BASELINE},
        'overlay-ports':[str(source/'vcpkg/ports')]}))
    triple={'arm64-v8a':'arm64','x86_64':'x64'}[abi]+'-android-release'
    triplets=work/'triplets';triplets.mkdir(exist_ok=True)
    (triplets/(triple+'.cmake')).write_text('set(VCPKG_TARGET_ARCHITECTURE '+('arm64' if abi=='arm64-v8a' else 'x64')+')\n'
        'set(VCPKG_CMAKE_SYSTEM_NAME Android)\nset(VCPKG_CRT_LINKAGE dynamic)\nset(VCPKG_LIBRARY_LINKAGE static)\nset(VCPKG_BUILD_TYPE release)\nset(VCPKG_CMAKE_SYSTEM_VERSION 26)\n'
        'set(VCPKG_CMAKE_CONFIGURE_OPTIONS -DANDROID_ABI='+abi+')\n'
        'set(VCPKG_CXX_FLAGS_RELEASE "-O1 -g0")\n')
    sdk=work/'opencv';archive=work/'opencv.zip'
    if not (sdk/'OpenCV-android-sdk/sdk/native/jni/OpenCVConfig.cmake').is_file():
        urllib.request.urlretrieve(f'https://github.com/opencv/opencv/releases/download/{OPENCV}/opencv-{OPENCV}-android-sdk.zip',archive)
        with zipfile.ZipFile(archive) as z:z.extractall(sdk)
    cv=sdk/'OpenCV-android-sdk/sdk'
    env=os.environ.copy();env['ANDROID_NDK_HOME']=str(ndk);env['VCPKG_MAX_CONCURRENCY']='2'
    env['VCPKG_BINARY_SOURCES']='clear;files,'+str(work/'archives')+',readwrite'
    (work/'archives').mkdir(exist_ok=True)
    out=work/('build-v2-'+abi)
    run('cmake','-S',root/'native/android','-B',out,'-G','Ninja',
        '-DCMAKE_BUILD_TYPE=Release','-DVCPKG_CHAINLOAD_TOOLCHAIN_FILE='+str(ndk/'build/cmake/android.toolchain.cmake'),'-DCMAKE_TOOLCHAIN_FILE='+str(vcpkg/'scripts/buildsystems/vcpkg.cmake'),
        '-DVCPKG_MANIFEST_DIR='+str(manifest),'-DVCPKG_OVERLAY_TRIPLETS='+str(triplets),'-DVCPKG_TARGET_TRIPLET='+triple,
        '-DANDROID_ABI='+abi,'-DANDROID_PLATFORM=android-26','-DANDROID_STL=c++_shared',
        '-DANDROID_NDK='+str(ndk),'-DBASALT_SOURCE='+str(source),'-DOpenCV_DIR='+str(cv/'native/jni'),env=env)
    run('cmake','--build',out,'--target','vector_basalt','-j2',env=env)
    target=root/'app/src/main/jniLibs'/abi;target.mkdir(parents=True,exist_ok=True)
    libraries=[out/'libvector_basalt.so',cv/'native/libs'/abi/'libopencv_java4.so']
    arch='aarch64-linux-android' if abi=='arm64-v8a' else 'x86_64-linux-android'
    libraries.append(ndk/'toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib'/arch/'libc++_shared.so')
    hashes={}
    for lib in libraries:
        shutil.copy2(lib,target/lib.name);hashes[lib.name]=hashlib.sha256(lib.read_bytes()).hexdigest()
    notices=root/'app/src/main/assets/native-notices';notices.mkdir(parents=True,exist_ok=True)
    for p in out.glob('vcpkg_installed/*/share/*/copyright'):
        shutil.copy2(p,notices/(p.parent.name+'-copyright.txt'))
    shutil.copy2(source/'LICENSE',notices/'basalt-LICENSE.txt')
    for p in (sdk/'OpenCV-android-sdk').glob('*LICENSE*'):shutil.copy2(p,notices/p.name)
    receipt={'basalt_sha':BASALT,'vcpkg_sha':VCPKG,'registry_baseline':BASELINE,'opencv_version':OPENCV,
        'opencv_archive_sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),
        'abi':abi,'libraries_sha256':hashes,'ndk':str(ndk),'status':'CROSS_COMPILED_NOT_DEVICE_QUALIFIED'}
    (root/'app/src/main/assets/native_build.json').write_text(json.dumps(receipt,indent=2))
    print(json.dumps(receipt,indent=2))


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);p.add_argument('--abi',default='arm64-v8a');p.add_argument('--ndk',type=Path,required=True)
    a=p.parse_args();build(a.work,a.abi,a.ndk.resolve())
