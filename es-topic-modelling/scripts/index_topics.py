import imp

import logging
from argparse import ArgumentParser

import numpy as np
import sys
import os

from elasticsearch.client import Elasticsearch
from elasticsearch.helpers import scan
from gensim.models.ldamodel import LdaModel

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'python', 'lib')))

from wikipedia import tokenize


def main():
    parser = ArgumentParser()
    parser.add_argument('-m', '--model-file', default='model-lda-100')
    parser.add_argument('-e', '--es-host', default='localhost:9200')
    parser.add_argument('-i', '--index', default='reco-test')
    opts = parser.parse_args()

    model_fn = opts.model_file
    es_hosts = [opts.es_host]
    es_index = opts.index

    es = Elasticsearch(hosts=es_hosts)

    model = LdaModel.load(model_fn)

    q = {'query': {'match': {'subtitles_language': 'nb'}}}

    for hit in scan(es, index=es_index, doc_type='tv', query=q, _source=['subtitles']):
        source = hit['_source']
        sub = source['subtitles']

        vec = np.zeros(100)

        for i, v in model.get_document_topics(model.id2word.doc2bow(tokenize(sub))):
            vec[i] = v

        doc_id = hit['_id']

        es.update(es_index, 'tv', doc_id, body={'doc': {'wiki-topics': vec.tolist()}})


if __name__ == '__main__':
    imp.reload(logging)
    logging.basicConfig(level=logging.INFO)

    main()