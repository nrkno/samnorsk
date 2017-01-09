# SynonymCreator

SynonymCreator is used to generate synonym dictionaries for parallel corpus. It assumes one or two input files in the source language, and corresponding 
input files in the target language. The corresponding files should contain the same text, with identical line aligment. 
 
The scripts assumes that scala and sbt is installed. Usage:

    
    ./create_synonyms.sh --source1 nynorsk1.txt --target1 bokmaal-translated-to-nynorsk.txt --source2 bokmaal-translated-to-nynorsk2.txt --target2 nynorsk2.txt -o reduction.dictionary.txt
    
This will create an elastic/lucene synonym reduction dictionary of this type:

    sportsbegivenheter,sportshendelser => sportshendingar
    dramaene => dramaa
    høydesyke => høgdesjuke
    skipsregistrene => skipsregistera
    uårene => uåra
    stråbiter => stråbitar
    saken => saka
    borgerparti => borgarparti
    avvikssenteret => avviksenteret
    opphetet,opphetede => oppheita


Alternatively, we can create a synonym by expansion dictionary using the "-e" flag:

	./create_synonyms.sh -e --source1 nynorsk1.txt --target1 bokmaal-translated-to-nynorsk.txt --source2 bokmaal-translated-to-nynorsk2.txt --target2 nynorsk2.txt -o reduction.dictionary.txt

    sportsbegivenheter,sportshendelser,sportshendingar
    dramaene,dramaa
    høydesyke,høgdesjuke
    skipsregistrene,skipsregistera
    uårene,uåra
    stråbiter,stråbitar
    saken,saka
    borgerparti,borgarparti
    avvikssenteret,avviksenteret
    opphetet,opphetede,oppheita

