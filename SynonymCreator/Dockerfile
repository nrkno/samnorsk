FROM java:latest
MAINTAINER Gisle Ytrestol <gisleyt@gmail.com>

ENV SCALA_VERSION 2.11.8
ENV SBT_VERSION 0.13.13

RUN \
  curl -fsL http://downloads.typesafe.com/scala/$SCALA_VERSION/scala-$SCALA_VERSION.tgz | tar xfz - -C /root/ && \
  echo >> /root/.bashrc && \
  echo 'export PATH=~/scala-$SCALA_VERSION/bin:$PATH' >> /root/.bashrc
  
RUN \
  curl -L -o sbt-$SBT_VERSION.deb http://dl.bintray.com/sbt/debian/sbt-$SBT_VERSION.deb && \
  dpkg -i sbt-$SBT_VERSION.deb && \
  rm sbt-$SBT_VERSION.deb && \
  apt-get update && \
  apt-get install sbt && \
  sbt sbtVersion

RUN wget http://apertium.projectjj.com/apt/install-nightly.sh -O - | /bin/bash \
    && apt-get update \
    && apt-get -f install -y \
    apertium-all-dev \
    apertium-nno \
    apertium-nob \
    apertium-nno-nob \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /synonymcreator/src

ADD translate_wiki.sh /synonymcreator
ADD create_synonyms.sh /synonymcreator
ADD src /synonymcreator/src
ADD build.sbt /synonymcreator
WORKDIR synonymcreator
RUN sbt compile
