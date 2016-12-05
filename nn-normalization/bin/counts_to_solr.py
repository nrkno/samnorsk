#!/usr/bin/env python

import io
import logging
import os
import sys
from argparse import ArgumentParser

sys.path.append(os.path.join(os.path.abspath(os.path.dirname(__file__)), '..', 'lib'))

from translation_counter import TranslationCounter


def main():
    parser = ArgumentParser()
    parser.add_argument('-i', '--input-file')
    parser.add_argument('-o', '--output-file')
    opts = parser.parse_args()

    in_fn = opts.input_file
    out_fn = opts.output_file

    if not in_fn and out_fn:
        logging.error("missing filenames...")
        sys.exit(1)

    with io.open(in_fn, mode='r', encoding='utf-8') as f:
        trans_counter = TranslationCounter.read(f)

    with io.open(out_fn, mode='w', encoding='utf-8') as f:
        trans_counter.print(f, format='solr')


if __name__ == '__main__':
    logging.basicConfig(level=logging.INFO)

    main()