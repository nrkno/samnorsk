FROM ubuntu:16.04
MAINTAINER André Lynum <andre.lynum@nrk.no>

ENV LANG=C.UTF-8 LC_ALL=C.UTF-8

RUN apt-get update -qq -y --fix-missing \
    && apt-get install -y -qq \
    wget \
    bzip2 \
    ca-certificates \
    build-essential \
    libglib2.0-0 \
    libxext6 \
    libsm6 \
    libxrender1 \
    git \
    mercurial \
    subversion

RUN wget http://apertium.projectjj.com/apt/install-nightly.sh -O - | /bin/bash \
    && apt-get update \
    && apt-get -f install -y \
    apertium-all-dev \
    apertium-nno \
    apertium-nob \
    apertium-nno-nob \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

RUN echo 'export PATH=/opt/conda/bin:$PATH' > /etc/profile.d/conda.sh && \
    wget --quiet https://repo.continuum.io/miniconda/Miniconda3-latest-Linux-x86_64.sh -O ~/miniconda.sh && \
    /bin/bash ~/miniconda.sh -b -p /opt/conda && \
    rm ~/miniconda.sh

ENV PATH /opt/conda/bin:$PATH

RUN mkdir -p /software
COPY environment.yml /software
RUN conda env create -q --force -f /software/environment.yml

COPY lib /software/lib
COPY bin /software/bin
RUN chmod +x /software/bin/*
RUN echo 'export PATH=/software/bin:$PATH' > /etc/profile.d/software.sh
RUN echo '. activate base' >> /etc/profile.d/software.sh

RUN /bin/bash -c -l /software/bin/install_models.py
