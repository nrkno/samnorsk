#!/usr/bin/env bash

programname=$0

function usage {
    echo "usage: $programname (-e) [--nntrans] [--nbtrans] ([--nndump]) ([--nbdump])"
    echo "	--nndump Wikipedia dump (gzip) for Nynorsk"
    echo "	--nbdump Wikipedia dump (gzip) for Bokmaal"
    echo "	--nntrans output for translations from Nynorsk to Bokmaal"
    echo "	--nbtrans output for translations from Bokmaal to Nynorsk"
    exit 1
}

while [[ $# -gt 1 ]]
do
key="$1"

case $key in
    --nndump)
    NYNORSKDUMP="--nndump $2"
    shift # past argument
    ;;
    --nbdump)
    BOKMAALDUMP="--nbdump $2"
    shift # past argument
    ;;
    --nntrans)
    NYNORSKTRANSLATION="--nntrans $2"
    shift # past argument
    ;;
    --nbtrans)
    BOKMAALTRANSLATION="--nbtrans $2"
    shift # past argument
    ;;
    *)
            # unknown option
    ;;
esac
shift # past argument or value
done

if [[ -z "$NYNORSKTRANSLATION" || -z "$BOKMAALTRANSLATION" ]]
then
    usage
    exit -1
fi

export SBT_OPTS="-Xms2G -Xmx2G"

sbt "run-main no.nrk.samnorsk.wikiextractor.WikiExtractor $NYNORSKDUMP $BOKMAALDUMP $NYNORSKTRANSLATION $BOKMAALTRANSLATION"
