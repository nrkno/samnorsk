import imp
import logging
import os
import sys
from argparse import ArgumentParser

from gensim.corpora.dictionary import Dictionary
from gensim.models.ldamulticore import LdaMulticore

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'python', 'lib')))

from wikipedia import articles, tokenize


def wiki_docs(dump_fn, limit=None):
    for article in articles(dump_fn, limit=limit):
        yield tokenize(article['text'])


class WikiIterator():
    def __init__(self, dump_fn, limit=None):
        super().__init__()

        self.dump_fn = dump_fn
        self.limit = limit

    def __iter__(self):
        return wiki_docs(self.dump_fn, limit=self.limit)


def main():
    parser = ArgumentParser()
    parser.add_argument('-d', '--dump-file')
    parser.add_argument('-m', '--model-file', default='model-lda-100')
    parser.add_argument('-l', '--limit', default=-1, type=int)
    opts = parser.parse_args()

    dump_fn = opts.dump_file
    model_fn = opts.model_file
    limit = opts.limit

    if limit == -1:
        limit = None

    it = WikiIterator(dump_fn, limit=limit)

    vocab = Dictionary(it)
    vocab.filter_extremes(no_above=.5, no_below=20, keep_n=50000)
    vocab.compactify()

    model = LdaMulticore(corpus=[vocab.doc2bow(a) for a in it], id2word=vocab, passes=2)

    for topic in model.print_topics(20):
        print(topic)

    model.save(model_fn)


if __name__ == '__main__':
    imp.reload(logging)
    logging.basicConfig(level=logging.INFO)

    main()
