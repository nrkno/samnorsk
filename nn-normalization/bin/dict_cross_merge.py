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
    parser.add_argument('-k', '--key-dictionary')
    parser.add_argument('-m', '--merge-dictionary')
    parser.add_argument('-o', '--output-file')
    opts = parser.parse_args()

    key_fn = opts.key_dictionary
    merge_fn = opts.merge_dictionary
    out_fn = opts.output_file

    if not (key_fn and merge_fn and out_fn):
        logging.error("Missing input/output files ...")
        sys.exit(1)

    with io.open(key_fn, mode='r', encoding='utf-8') as f:
        key_counter = TranslationCounter.read(f)

    with io.open(merge_fn, mode='r', encoding='utf-8') as f:
        merge_counter = TranslationCounter.read(f)

    key_counter.cross_merge(merge_counter)

    with io.open(out_fn, mode='w', encoding='utf-8') as f:
        key_counter.print(f)


if __name__ == '__main__':
    logging.basicConfig(level=logging.INFO)

    main()