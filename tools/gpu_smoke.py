#!/usr/bin/env python3
"""Compile actual Android overlay shaders and draw using headless EGL/GLES.
Software-driver correctness smoke test, NOT phone performance evidence.
"""
import ctypes as C
import json
import os
from pathlib import Path
import re
os.environ.setdefault('EGL_PLATFORM','surfaceless')
egl=C.CDLL('libEGL.so.1')
def api(lib,name,restype,*args):
    f=getattr(lib,name);f.restype=restype;f.argtypes=list(args);return f
ptr=C.c_void_p;i=C.c_int;u=C.c_uint;f=C.c_float
get=api(egl,'eglGetDisplay',ptr,ptr);d=get(None)
assert api(egl,'eglInitialize',u,ptr,C.POINTER(i),C.POINTER(i))(d,None,None),'EGL initialize'
assert api(egl,'eglBindAPI',u,u)(0x30A0)
attrs=(i*13)(0x3033,1,0x3040,4,0x3024,8,0x3023,8,0x3022,8,0x3021,8,0x3038)
config=ptr();n=i();assert api(egl,'eglChooseConfig',u,ptr,C.POINTER(i),C.POINTER(ptr),i,C.POINTER(i))(d,attrs,C.byref(config),1,C.byref(n)) and n.value
surf=api(egl,'eglCreatePbufferSurface',ptr,ptr,ptr,C.POINTER(i))(d,config,(i*5)(0x3057,16,0x3056,16,0x3038))
ctx=api(egl,'eglCreateContext',ptr,ptr,ptr,ptr,C.POINTER(i))(d,config,None,(i*3)(0x3098,2,0x3038))
assert api(egl,'eglMakeCurrent',u,ptr,ptr,ptr,ptr)(d,surf,surf,ctx)
proc=api(egl,'eglGetProcAddress',ptr,C.c_char_p)
def gl(name,restype,*args):return C.CFUNCTYPE(restype,*args)(proc(name.encode()))
source=(Path(__file__).resolve().parents[1]/'app/src/main/java/dev/jboehm/vectorvio/WorldOverlayRenderer.java').read_text()
shaders=[]
for var,kind in [('vs',0x8B31),('fs',0x8B30)]:
    text=json.loads(re.search(r'String '+var+r'=("[^\n]*?");',source).group(1)).encode()
    shader=gl('glCreateShader',u,u)(kind);p=C.c_char_p(text)
    gl('glShaderSource',None,u,i,C.POINTER(C.c_char_p),ptr)(shader,1,C.byref(p),None)
    gl('glCompileShader',None,u)(shader);ok=i();gl('glGetShaderiv',None,u,u,C.POINTER(i))(shader,0x8B81,C.byref(ok))
    if not ok.value:
        log=C.create_string_buffer(4096);gl('glGetShaderInfoLog',None,u,i,ptr,ptr)(shader,4096,None,log);raise AssertionError(log.value)
    shaders.append(shader)
program=gl('glCreateProgram',u)()
for shader in shaders:gl('glAttachShader',None,u,u)(program,shader)
gl('glLinkProgram',None,u)(program);ok=i();gl('glGetProgramiv',None,u,u,C.POINTER(i))(program,0x8B82,C.byref(ok));assert ok.value,'shader link'
gl('glUseProgram',None,u)(program)
loc=gl('glGetUniformLocation',i,u,C.c_char_p)
identity=(f*16)(1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1)
gl('glUniformMatrix4fv',None,i,i,u,C.POINTER(f))(loc(program,b'uVP'),1,0,identity)
gl('glUniform1f',None,i,f)(loc(program,b'uRound'),0)
gl('glUniform1f',None,i,f)(loc(program,b'uSize'),4)
gl('glUniform4f',None,i,f,f,f,f)(loc(program,b'uColor'),1,.5,.25,1)
position=gl('glGetAttribLocation',i,u,C.c_char_p)(program,b'aPosition')
verts=(f*9)(-.8,-.8,0,.8,-.8,0,0,.8,0)
gl('glVertexAttribPointer',None,u,i,u,u,i,ptr)(position,3,0x1406,0,0,C.cast(verts,ptr));gl('glEnableVertexAttribArray',None,u)(position)
gl('glViewport',None,i,i,i,i)(0,0,16,16);gl('glDrawArrays',None,u,i,i)(4,0,3)
pixel=(C.c_ubyte*4)();gl('glReadPixels',None,i,i,i,i,u,u,ptr)(8,8,1,1,0x1908,0x1401,pixel)
assert pixel[0]>200 and pixel[1]>80, list(pixel)
assert gl('glGetError',u)()==0
print('PASS: actual overlay shaders compile/link and draw; center RGBA',list(pixel))
