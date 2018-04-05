#!/bin/bash
alias diff='diff -wbBdu'
FILE_BOTH=failed_both
FILE_NEW=failed_new
FILE_SAME=same
FILE_DIFF=different

if [[ $# -ge 1 ]]; then
  echo "Usage: $0"
  echo "Analyzes output:"
  echo "  Failed in both: $FILE_BOTH"
  echo "  Failed in new:  $FILE_NEW"
  echo "  Same plans:     $FILE_SAME"
  echo "  Plans differ:   $FILE_DIFF"
  exit 2
fi

for i in *.old; do
  OLDERR=${i}.err
  NW=$(echo $i | sed 's/\.old/\.new/')
  NEWERR=${NW}.err
  OLDOUT=${i}.out
  NEWOUT=${NW}.out
  if grep -q "^Query.*failed:" $OLDERR; then
    echo $i >> $FILE_BOTH
  elif grep -q "^Query.*failed:" $NEWERR; then
    echo $i >> $FILE_NEW
  elif diff -q $OLDOUT $NEWOUT; then
    echo $i >> $FILE_DIFF
  else
    echo $i >> $FILE_SAME
  fi
done

