"""Deterministic schema-correct synthetic inputs; NOT original research data."""
import csv
import json
from pathlib import Path

def create_oxiod(root,rows=180,with_reference=True):
    root=Path(root);root.mkdir(parents=True,exist_ok=True)
    imu=root/"imu1.csv";ref=root/"vi1.csv"
    with imu.open("w",newline="",encoding="utf-8") as f:
        w=csv.writer(f)
        w.writerow(["Time","attitude_roll","attitude_pitch","attitude_yaw",
                    "rotation_rate_x","rotation_rate_y","rotation_rate_z",
                    "gravity_x","gravity_y","gravity_z",
                    "user_acc_x","user_acc_y","user_acc_z",
                    "magnetic_field_x","magnetic_field_y","magnetic_field_z"])
        for i in range(rows):
            pulse=.6 if i%60==11 else (-.05 if 12<=i%60<35 else 0)
            w.writerow([1.5+i*.01,0,0,0,0,0,.02,0,0,1,0,0,pulse,34,4,12])
    if with_reference:
        with ref.open("w",newline="",encoding="utf-8") as f:
            w=csv.writer(f)
            w.writerow(["Time","Header","translation.x","translation.y","translation.z",
                        "rotation.x","rotation.y","rotation.z","rotation.w"])
            for i in range(rows):
                w.writerow([1.5+i*.01,0,i*.02,0,0,0,0,0,1])
    return imu,ref

def create_ronin(root,rows=200,start_frame=12,with_reference=True):
    import h5py
    import numpy as np
    root=Path(root);root.mkdir(parents=True,exist_ok=True)
    (root/"info.json").write_text(json.dumps({"start_frame":start_frame,
        "device":"FAKE_DEVICE_FOR_SCHEMA_TESTS","length":rows/200}))
    t=np.arange(rows,dtype=np.float64)*.01+1650000000.5
    acc=np.zeros((rows,3),dtype=np.float64);acc[:,2]=9.80665
    acc[13::60,2]+=5.8
    gyro=np.zeros((rows,3),dtype=np.float64)
    pos=np.zeros((rows,3),dtype=np.float64)
    pos[:,0]=np.arange(rows)*.02
    with h5py.File(root/"data.hdf5","w") as h5:
        h5.create_dataset("synced/time",data=t)
        h5.create_dataset("synced/acce",data=acc)
        h5.create_dataset("synced/gyro",data=gyro)
        h5.create_dataset("synced/magnet",data=np.ones((rows,3),dtype=np.float64)*12)
        if with_reference:h5.create_dataset("pose/tango_pos",data=pos)
    return root
