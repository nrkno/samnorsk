# Building simple translation dictionary

This software builds a data derived simple translation dictionary from Nynorsk to Bokmål. This can be used for dual language text  normalization of Norwegian for search, IR or other purposes where high quality translation is not needed. 

The docker image is set up with python and dictionary build scripts.

Use the ```build-dictionaries.sh``` to build the dictionary files. Artifacts and Wikipedia dumps are stored in ```../data``` relative to where you run the script. Edit the ```$DATA``` variable if you want to change the location. It uses all processors by default. Edit the ```$PROCS``` variable to change tis behaviour.

The script builds the following files in the data directory:

    nn-dict-top5-filtered.txt
    no-dict-top5-filtered.txt
    merged-dict-top5-filtered.txt

Which is the translation dictionaries from Nynorsk to Bokmål based on the NN wiki, NO wiki or both respectively.

TODO find best filtering criteria.
TODO directly generate Solr synonym files.

Synonym dictionaries can be built with
(TODO: NOT WORKING DUE TO python 3 dependency issues)

    docker run -v `pwd`/data:/data wiki-extraction bash -l -c "software/bin/create_synonym_dictionary.sh -n /data/nn.json -b /data/nb.json -o /data/synonyms.txt"
