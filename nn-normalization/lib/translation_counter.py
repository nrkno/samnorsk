from collections import Counter
from operator import itemgetter


class TranslationCounter():
    def __init__(self, source_tf_filter=1, source_df_filter=1.0, trans_tf_filter=1, trans_df_filter=1.0,
                 top_n=None, print_counts=True):
        self.source_tf_filter = source_tf_filter
        self.source_df_filter = source_df_filter
        self.trans_tf_filter = trans_tf_filter
        self.trans_df_filter = trans_df_filter
        self.top_n = top_n
        self.print_counts = print_counts

        self.count_dict = {}
        self.source_tf = Counter()
        self.trans_tf = Counter()
        self.source_df = Counter()
        self.trans_df = Counter()
        self.count_docs = 0

    def _add_to_map(self, pairs):
        for a, b in pairs:
            if a in self.count_dict:
                counter = self.count_dict[a]
                counter.update([b])
            else:
                self.count_dict[a] = Counter([b])

        return self

    def _filtered_trans(self, word):
        return not ((self.trans_tf[word] >= self.trans_tf_filter) and
                    (self.trans_df[word] / float(self.count_docs) <= self.trans_df_filter))

    def _format(self, word, count):
        if self.print_counts:
            return '%s:%d' % (word, count)
        else:
            return word

    def update(self, pairs):
        if len(pairs) == 0:
            return self

        self.count_docs += 1

        source_tokens, trans_tokens = zip(*pairs)
        self.source_tf.update(source_tokens)
        self.source_df.update(set(source_tokens))
        self.trans_tf.update(trans_tokens)
        self.trans_df.update(set(trans_tokens))

        self._add_to_map(pairs)

        return self

    def print(self, f, format='counts'):
        for key, counts in self.count_dict.items():
            if (self.source_tf[key] >= self.source_tf_filter) and \
                    (self.source_df[key] / float(self.count_docs) <= self.source_df_filter):
                candidates = [(v, c) for v, c in counts.items() if not self._filtered_trans(v)]
                candidates = sorted(candidates, key=itemgetter(1), reverse=True)
            elif len(self.source_tf) == 0:
                # no tf/df counts - dictionary read from file
                candidates = sorted(counts.items(), key=itemgetter(1), reverse=True)
            else:
                continue

            if self.top_n:
                candidates = candidates[:self.top_n]

            if candidates:
                if format == 'counts':
                    f.write(u'%s\t%s\n' % (key, ' '.join([self._format(v, c) for v, c in candidates])))
                elif format == 'solr':
                    f.write(u'%s => %s\n' % (key, candidates[0][0]))

    def cross_merge(self, other_counter):
        for key, counter in other_counter.count_dict.items():
            for trans, count in counter.items():
                if trans in self.count_dict:
                    self.count_dict[trans].update({key: count})
                else:
                    self.count_dict[trans] = Counter({key: count})

        return self

    @staticmethod
    def read(f):
        inst = TranslationCounter()

        for line in f:
            line = line.strip()

            if line == "":
                continue

            key, counts = line.split('\t')

            inst.count_dict[key] = Counter({trans: int(count) for trans, count
                                            in [item.split(':') for item in counts.split()]})

        return inst
