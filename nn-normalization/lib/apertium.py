import logging
import re
import subprocess


def translate(sents, direction):
    sents = [re.sub(r'\s+', ' ', sent) for sent in sents]

    proc = subprocess.Popen(['apertium', direction],
                            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    output, err = proc.communicate(u'\n'.join(sents).encode('utf-8'))

    if len(err) > 0:
        logging.error("apertium returned error: %s" % err.decode('utf-8', errors='ignore'))

    return output.decode('utf-8').split(u'\n')
