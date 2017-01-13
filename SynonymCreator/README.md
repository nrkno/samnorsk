# SynonymCreator

SynonymCreator is used to generate synonym dictionaries for Bokmål and Nynorsk. It consists of two scripts, 

`translate_wiki.sh`
and
`create_synonym.sh`

The scripts have some none-trival dependencies. As an alternative to installing Java, Scala, SBT and [Apertium](https://www.apertium.org/index.eng.html), 
the scripts may be executed as a Docker image. 
 
A docker image can be built with:

    docker build -t <image-name> .
    

# WikiExtractor

WikiExtractor extracts the articles from the Bokmål and Nynorsk Wikipedia, and creates parallel corpora of the two data sets. The output from 
WikiExtractor is lines with Json objects of the format:

```
{
"original":         the original Wikipedia article,
 "translation":     the translated version of the article,
 "fromLanguage":    the language of the original article, nb (Bokmål) or nn (Nynorsk), 
 "toLanguage":      the language to which the article has been translated, nb (Bokmål) or nn (Nynorsk)
}
```

It can be invoked with `translate_wiki.sh [--trans] ([--nndump]) ([--nbdump])`. `--trans` is the output file. `--nndump` or  `--nbdump` defines
local gzipped versions of the Wikipedia dump for Bokmål and Nynorsk in the [cirrussearch-content](https://dumps.wikimedia.org/other/cirrussearch/current/) format.
If a local file is not provided, the script will download the latest available dump.

The script may be executed within the docker image with:
    
    docker run -v /tmp/dockertmp:/data <image-name> bash -l -c /synonymcreator/translate_wiki.sh --trans /data/trans.full.docker.txt

This assumes that the directory `/tmp/dockertmp` exists, and the translated parallel corpora will be written to this directory.

NB: It may take several hours to extract a translation of the full Wikipedia dumps. 

# SynonymMapper

SynonymMapper takes as input a Nynorsk/Bokmål parallel corpus of the same format as is produced by **WikiExtractor**. It can be invoked with
 ` create_synonyms.sh [--trans] [--output] ([--reduction])`, where `--trans` is the input file, and `--output` is the output synonym dictionary. The optional
parameter `--reduction` assumes a language, either `nb` (Bokmål) or `nn` (Nynorsk). When the reduction flag is used, all synonyms will be mapped to a canonical
form, either in Nynorsk or Bokmål: 

Synonoms with a Nynorsk canonical form:

    sportsbegivenheter,sportshendelser => sportshendingar
    dramaene => dramaa
    høydesyke => høgdesjuke
    skipsregistrene => skipsregistera

If the reduction flag is not used, all synonyms will be listed without mapping to a main form.

    sportsbegivenheter,sportshendelser,sportshendingar
    dramaene,dramaa
    høydesyke,høgdesjuke
    skipsregistrene,skipsregistera
