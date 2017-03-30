import imp
import logging
from argparse import ArgumentParser

from elasticsearch import helpers
from elasticsearch.client import Elasticsearch
from elasticsearch.client.indices import IndicesClient
from gensim.corpora import Dictionary
from gensim.models import LdaMulticore


def main():
    parser = ArgumentParser()
    parser.add_argument('-e', '--es-host', default='localhost:9200')
    parser.add_argument('-i', '--index', default='reco')
    parser.add_argument('-t', '--type', default=None, type=str)
    parser.add_argument('-f', '--field', default='subtitles')
    parser.add_argument('-a', '--analyzer', default=None, type=str)
    parser.add_argument('-l', '--limit', default=None, type=int)
    parser.add_argument('-m', '--model-file', default='lda-model')
    opts = parser.parse_args()

    es_hosts = [opts.es_host]
    es_index = opts.index
    es_type = opts.type
    es_field = opts.field
    es_analyzer = opts.analyzer
    limit = opts.limit
    model_fn = opts.model_file

    es = Elasticsearch(hosts=es_hosts)
    ic = IndicesClient(es)

    logging.info("Collecting documents")

    docs = []

    q = {'query': {'exists': {'field': es_field}}}

    count = 0

    for hit in helpers.scan(es, index=es_index, doc_type=es_type, query=q, _source=[es_field]):
        source = hit['_source']
        sub = source[es_field]

        resp = ic.analyze(index=es_index, body={'analyzer': es_analyzer, 'text': sub})

        docs.append([token['token'] for token in resp['tokens']])

        count += 1

        if limit and count >= limit:
            break

    logging.info("Collected %d documents" % count)

    vocab = Dictionary(docs)
    vocab.filter_extremes(no_above=.5, no_below=20, keep_n=50000)
    vocab.compactify()

    model = LdaMulticore(corpus=[vocab.doc2bow(a) for a in docs], id2word=vocab, passes=2)

    for topic in model.print_topics(20):
        print(topic)

    model.save(model_fn)


if __name__ == '__main__':
    imp.reload(logging)
    logging.basicConfig(level=logging.INFO)

    main()