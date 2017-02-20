#!/usr/bin/env python

import os
import sys

sys.path.append(os.path.join(os.path.abspath(os.path.dirname(__file__)), '..', 'lib'))

from apertium import translate

if __name__ == '__main__':
    print(translate(['Eg skriv nynorsk!'], 'nno-nob'))