#!/usr/bin/env bash

programname=$0

function usage {
    echo "usage: $programname (-e) [--source1] [--target1] ([--source2] [--target2]) [-o output]"
    echo "	-e expansion      use synonym by reduction (default is reduction)"
    echo "	--source1 source input file"
    echo "	--target1 translation of source 1"
    echo "	--source2 source input file"
    echo "	--target2 translation of source 2"
    echo "	--output dictionary output file"
    exit 1
}

while [[ $# -gt 1 ]]
do
key="$1"

case $key in
    --source1)
    SOURCE1="--source1 $2"
    shift # past argument
    ;;
    --target1)
    TARGET1="--target1 $2"
    shift # past argument
    ;;
    --source2)
    SOURCE2="--source2 $2"
    shift # past argument
    ;;
    --target2)
    TARGET2="--target2 $2"
    shift # past argument
    ;;
    -o|--output)
    OUTPUT="--output $2"
    shift # past argument
    ;;
    -e|--expansion)
    EXPANSION="--expansion true"
    ;;
    *)
            # unknown option
    ;;
esac
shift # past argument or value
done

if [[ -z "$SOURCE1" || -z "$TARGET1" || -z "$OUTPUT" ]]
then
    usage
    exit -1
fi

export SBT_OPTS="-Xms4G -Xmx4G"

sbt "run-main no.nrk.samnorsk.synonymmapper.SynonymMapper $SOURCE1 $TARGET1 $SOURCE2 $TARGET2 $OUTPUT $EXPANSION"
