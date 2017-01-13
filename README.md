# Samnorsk
Elastic dictionaries for Bokmål/Nynorsk. 

This repository contains stopword and synonym [dictionaries](https://github.com/nrkno/samnorsk/tree/master/dict) for Norwegian, aimed to be used with Elastic.
Both dictionaries are using the dictionary format assumed by Elastic:

https://www.elastic.co/guide/en/elasticsearch/guide/master/using-stopwords.html

https://www.elastic.co/guide/en/elasticsearch/guide/master/synonyms-expand-or-contract.html

The synonym dictionaries should typically be used if we have an elastic index which contains document in
both Bokmål and Nynorsk. Without some kind of normalization, the query results from the index will be very
biased by the language of the query. E.g. a query for "fremtiden" will typically to find other Nynorsk documents, where
the word "framtida" have been used instead. The synonym dictionary will either expand a number of synonyms for
each word in the document, or reduce words of similar meaning to one canonical form, either in Nynorsk or 
Bokmål.

Stopword dictionary snippet:

	blant
	ble
	blei
	bli
	blir
	blitt
	bør
	bort
	bortsett
	bra
	bruk
	bruke

Synonym expansion dictionary snippet:

	antagelig,antakeleg
	antagelige,antakelege
	antagonistene,antagonistane
	antagonister,antagonistar
	antallet,mengden,mengda
	antall,mengd

Synonym reduction (to Nynorsk) dictionary snippet:

	fødte => fødde
	fogdene,futene => futane
	fogden => futen
	fogder => futar
	fogd => fut
	føk => fauk

The synonym dictionaries are algorithmically created with [SynonymCreator](https://github.com/nrkno/samnorsk/tree/master/SynonymCreator), and will contain some inaccuracies and dubious entries.
