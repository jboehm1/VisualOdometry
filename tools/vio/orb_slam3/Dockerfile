FROM osrf/ros:noetic-desktop-full@sha256:7dbfb9576d8e6d226c31e06129a82aaab8702695f38eca2116918cb9b9308797

ARG ORB_SLAM3_SHA=4452a3c4ab75b1cde34e5505a36ec3f9edcdc4c4
ARG PANGOLIN_SHA=f55cb3e5e81cb7d58511fbc3ebadcf0610cae245
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential cmake git pkg-config \
    libopencv-dev libeigen3-dev libboost-all-dev libssl-dev \
    libgl1-mesa-dev libglew-dev libepoxy-dev libwayland-dev libxkbcommon-dev \
    libavcodec-dev libavformat-dev libavutil-dev libswscale-dev \
    libjpeg-dev libpng-dev libtiff-dev libopenexr-dev \
    xvfb xauth ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN git clone https://github.com/stevenlovegrove/Pangolin.git /opt/Pangolin \
    && git -C /opt/Pangolin checkout "$PANGOLIN_SHA" \
    && git -C /opt/Pangolin submodule update --init --recursive \
    && cmake -S /opt/Pangolin -B /opt/Pangolin/build -DCMAKE_BUILD_TYPE=Release \
       -DBUILD_PANGOLIN_PYTHON=OFF -DBUILD_EXAMPLES=OFF \
    && cmake --build /opt/Pangolin/build -j2 \
    && cmake --install /opt/Pangolin/build

WORKDIR /opt/orb_slam3
COPY . /opt/orb_slam3
RUN for dep in Thirdparty/DBoW2 Thirdparty/g2o Thirdparty/Sophus; do \
         cmake -S "$dep" -B "$dep/build" -DCMAKE_BUILD_TYPE=Release; \
         cmake --build "$dep/build" -j2; \
       done \
    && tar -xf Vocabulary/ORBvoc.txt.tar.gz -C Vocabulary \
    && cmake -S . -B build -DCMAKE_BUILD_TYPE=Release \
    && cmake --build build --target stereo_inertial_euroc -j2

ENV LD_LIBRARY_PATH=/opt/orb_slam3/lib:/opt/orb_slam3/Thirdparty/DBoW2/lib:/opt/orb_slam3/Thirdparty/g2o/lib:/usr/local/lib
WORKDIR /out
CMD ["bash"]
