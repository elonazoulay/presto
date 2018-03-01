#! /bin/bash

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 FILE"
  echo "Runs file agains presto cluster"
  exit 2
fi

FILE=$1
CATALOG=$(sed -n 2p $FILE | sed 's/^--\ //')
TIER=$(sed -n 1p $FILE | sed 's/^--\ //')
SCHEMA=$(sed -n 3p $FILE | sed 's/^--\ //')

if [[ $CATALOG == "raptor" ]]; then
  exec presto --output-format TSV --file $FILE --smc $TIER $SCHEMA  2> ${FILE}.err | sed 's/_[0-9]*//g' > ${FILE}.out
else
  exec presto --output-format TSV --file $FILE $SCHEMA 2> ${FILE}.err | sed 's/_[0-9]*//g' > ${FILE}.out
fi
