#!/bin/bash

if [[ $# -ge 1 ]]; then
  echo "Usage: $0"
  echo "Runs all explains in the directory"
  exit 2
fi

SOURCE_DIR=$(dirname $0)

for i in *.old; do
  $SOURCE_DIR/run_explain.sh $i &
  NW=$(echo $i | sed 's/\.old/\.new/')
  $SOURCE_DIR/run_explain.sh $NW &
  wait
done
