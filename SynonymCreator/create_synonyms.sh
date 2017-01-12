#!/usr/bin/env bash

programname=$0

function usage {
    echo "usage: $programname (-e) [--trans] [--output] ([--reduction])"
    echo "	--trans input translations"
    echo "	--output dictionary output file"
    echo "	--reduction reduction to language (nb or nn)"
    exit 1
}

while [[ $# -gt 1 ]]
do
key="$1"

case $key in
    --trans)
    TRANS="--trans $2"
    shift # past argument
    ;;
    --output)
    OUTPUT="--output $2"
    shift # past argument
    ;;
    --reduction)
    REDUCTION="--reduction $2"
    shift # past argument
    ;;
    *)
            # unknown option
    ;;
esac
shift # past argument or value
done

if [[ -z "$TRANS" || -z "$OUTPUT" ]]
then
    usage
    exit -1
fi

export SBT_OPTS="-Xms4G -Xmx4G"

sbt "run-main no.nrk.samnorsk.synonymmapper.SynonymMapper $TRANS $OUTPUT $REDUCTION"
