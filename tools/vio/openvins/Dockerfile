FROM osrf/ros:noetic-desktop-full@sha256:7dbfb9576d8e6d226c31e06129a82aaab8702695f38eca2116918cb9b9308797

ARG OPENVINS_SHA=69488123ed9362dd44b6f28e7f4680abbff1442b
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential cmake git libeigen3-dev libboost-all-dev libceres-dev \
    libgoogle-glog-dev libgflags-dev libsuitesparse-dev libatlas-base-dev \
    python3-catkin-tools python3-osrf-pycommon python3-numpy python3-opencv \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt
COPY . /opt/open_vins
RUN mkdir -p /ws/src \
    && ln -s /opt/open_vins /ws/src/open_vins \
    && bash -lc 'source /opt/ros/noetic/setup.bash && cd /ws && catkin init && catkin build --cmake-args -DCMAKE_BUILD_TYPE=Release'

ENV ROS_HOME=/tmp/ros_home
WORKDIR /ws
CMD ["bash"]
