import io
import json
import logging
import os
import re
from bz2 import BZ2File
from gzip import GzipFile


def is_page(action, source):
    if ('index' in action) and ('_id' in action['index']) and ('_type' in action['index']) \
            and (action['index']['_type'] == 'page') and ('title' in source) and ('text' in source):
        return True

    return False


def articles(wiki_json_fn, limit=None):
    count = 0

    _, ext = os.path.splitext(wiki_json_fn)

    if ext == '.gz':
        f = GzipFile(wiki_json_fn, mode='r')
    elif ext == '.bz2':
        f = BZ2File(wiki_json_fn, mode='r')
    else:
        f = io.open(wiki_json_fn, mode='rb')

    while True:
        line = f.readline()

        if line == b'':
            break

        action = json.loads(line.decode('utf-8'))

        line = f.readline()

        if line == b'':
            break

        source = json.loads(line.decode('utf-8'))

        if is_page(action, source):
            yield {'id': action['index']['_id'], 'title': source['title'], 'text': source['text']}

            count += 1

        if limit and count > limit:
            return

        if count % 10000 == 0:
            logging.info("read %d articles" % count)

    f.close()


def tokenize(text):
    return [token.lower() for token in re.findall(r'\w+', text, re.UNICODE | re.MULTILINE)]