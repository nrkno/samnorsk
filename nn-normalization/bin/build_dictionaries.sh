#!/usr/bin/env bash

DATA_DIR=../data

mkdir -p ${DATA_DIR}

if [ ! -f ${DATA_DIR}/nnwiki-20161017-cirrussearch-content.json.gz ]; then
    echo "Downloading NN wikipedia"
    (cd ${DATA_DIR}; wget https://dumps.wikimedia.org/other/cirrussearch/20161017/nnwiki-20161017-cirrussearch-content.json.gz)
fi

if [ ! -f ${DATA_DIR}/nowiki-20161017-cirrussearch-content.json.gz ]; then
    echo "Downloading NO wikipedia"
    (cd ${DATA_DIR}; wget https://dumps.wikimedia.org/other/cirrussearch/20161017/nowiki-20161017-cirrussearch-content.json.gz)
fi

docker build -t wiki-extraction .

PROCS=`docker run wiki-extraction bash -c "cat /proc/cpuinfo | grep processor | wc -l"`

echo "Using ${PROCS} processors ..."

if [ ! -f ${DATA_DIR}/nn-dict-top5-filtered.txt ]; then
    echo "Creating NN dictionary"
    docker run -v `pwd`/${DATA_DIR}:/data wiki-extraction bash -l -c \
        "build_dictionary.py -p ${PROCS} -d nno-nob -n 5 -s 0.5 -S 5 -i /data/nnwiki-20161017-cirrussearch-content.json.gz -o /data/nn-dict-top5-filtered.txt"
fi

if [ ! -f ${DATA_DIR}/no-dict-top5-filtered.txt ]; then
    echo "Creating NO dictionary"
    docker run -v `pwd`/${DATA_DIR}:/data wiki-extraction bash -l -c \
        "build_dictionary.py -p ${PROCS} -d nob-nno -n 5 -t 0.5 -T 5 -i /data/nowiki-20161017-cirrussearch-content.json.gz -o /data/no-dict-top5-filtered.txt"
fi

if [ ! -f ${DATA_DIR}/merged-dict-top5-filtered.txt ]; then
    echo "Merging counts"
    docker run -v `pwd`/${DATA_DIR}:/data wiki-extraction bash -l -c \
        "dict_cross_merge.py -k /data/nn-dict-top5-filtered.txt -m /data/no-dict-top5-filtered.txt -o data/merged-dict-top5-filtered.txt"
fi

if [ ! -f ${DATA_DIR}/merged-dict-top5-filtered.solr ]; then
    docker run -v `pwd`/${DATA_DIR}:/data wiki-extraction bash -l -c \
        "counts_to_solr.py -i ../data/merged-dict-top5-filtered.txt -o ../data/merged-dict-top5-filtered.solr"
fi

if [ ! -f ${DATA_DIR}/nn-dict-top5-filtered.solr ]; then
    docker run -v `pwd`/${DATA_DIR}:/data wiki-extraction bash -l -c \
        "counts_to_solr.py -i ../data/nn-dict-top5-filtered.txt -o ../data/nn-dict-top5-filtered.solr"
fi

if [ ! -f ${DATA_DIR}/no-dict-top5-filtered.solr ]; then
    docker run -v `pwd`/${DATA_DIR}:/data wiki-extraction bash -l -c \
        "counts_to_solr.py -i ../data/no-dict-top5-filtered.txt -o ../data/no-dict-top5-filtered.solr"
fi