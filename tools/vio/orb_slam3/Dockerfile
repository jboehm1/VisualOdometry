FROM ubuntu:jammy-20260930@sha256:98510ac07bb4545e389be502a8918b385c018bfe4ceeb5ac7e0375c782268b04

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
# Pangolin's pinned sigslot headers need C++14. ORB's full-BA generation counter is
# mistakenly bool upstream; promote it to int so increments and cancellation work.
RUN sed -i 's/-std=c++11/-std=c++14/g; s/-std=c++0x/-std=c++14/g' CMakeLists.txt \
    && sed -i 's/bool mnFullBAIdx;/int mnFullBAIdx;/' include/LoopClosing.h \
    && for dep in Thirdparty/DBoW2 Thirdparty/g2o Thirdparty/Sophus; do \
         cmake -S "$dep" -B "$dep/build" -DCMAKE_BUILD_TYPE=Release; \
         cmake --build "$dep/build" -j2; \
       done \
    && tar -xf Vocabulary/ORBvoc.txt.tar.gz -C Vocabulary \
    && cmake -S . -B build -DCMAKE_BUILD_TYPE=Release \
    && cmake --build build --target stereo_inertial_euroc -j2

ENV LD_LIBRARY_PATH=/opt/orb_slam3/lib:/opt/orb_slam3/Thirdparty/DBoW2/lib:/opt/orb_slam3/Thirdparty/g2o/lib:/usr/local/lib
WORKDIR /out
CMD ["bash"]
