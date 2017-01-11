#!/usr/bin/env bash

export SBT_OPTS="-Xms4G -Xmx4G"


programname=$0

function usage {
    echo "usage: $programname (-e) [--nynorsktranslation] [--bokmaaltranslation] ([--nynorsk-dump]) ([--bokmaal-dump])"
    echo "	--nn-dump Wikipedia dump (Nynorsk)"
    echo "	--nb-dump Wikipedia dump (Bokmaal)"
    echo "	--nntrans output for translations from Nynorsk to Bokmaal"
    echo "	--nbtrans output for translations from Bokmaal to Nynorsk"
    exit 1
}

while [[ $# -gt 1 ]]
do
key="$1"

case $key in
    --nn-dump)
    NYNORSKDUMP="--nynorsk $2"
    shift # past argument
    ;;
    --nb-dump)
    BOKMAALDUMP="--bokmaal $2"
    shift # past argument
    ;;
    --nntrans)
    NYNORSKTRANSLATION="--nynorsk-translation $2"
    shift # past argument
    ;;
    --nbtrans)
    BOKMAALTRANSLATION="--bokmaal-translation $2"
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

export SBT_OPTS="-Xms4G -Xmx4G"

sbt "run-main no.nrk.samnorsk.wikiextractor.WikiExtractor $NYNORSKDUMP $BOKMAALDUMP $NYNORSKTRANSLATION $BOKMAALTRANSLATION"
