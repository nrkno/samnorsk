# samnorsk
Elastic dictionaries for Bokmål/Nynorsk. 

This repo contains pre-generated stopword dictionaries for Norwegian (Bokmål and Nynorsk). Further, it includes synonym expansion and synonym reduction dictionaries for Bokmål/Nynorsk, intended to be used with Elastic indices which merge Bokmål/Nynorsk in the same index. By either reducing Bokmål/Nynorsk words to one canonical form, or expanding synonyms for Bokmål/Nynorsk given an input word, we ensure that even if we query in Nynorsk, we would still get hits from Bokmål content, and vice versa.
