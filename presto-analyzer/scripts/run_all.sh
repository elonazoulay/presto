#!/bin/bash

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 FILE"
  echo "Runs all explains in the directory"
  exit 2
fi

SOURCE_DIR=$(dirname $0)

FILE=$1

if [[ ! -f $FILE ]]; then
  echo "Invalid file $FILE"
  exit 1
fi

for i in $(<$FILE); do
  $SOURCE_DIR/run_explain.sh argus.$i.old &
  $SOURCE_DIR/run_explain.sh argus.$i.new &
  wait
done
