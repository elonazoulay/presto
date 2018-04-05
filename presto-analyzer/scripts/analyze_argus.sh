#!/bin/bash
alias diff='diff -wbBdu'
FILE_BOTH=failed_both
FILE_NEW=failed_new
FILE_SAME=same
FILE_DIFF=different

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 id_file"
  echo "Analyzes output:"
  echo "  Failed in both: $FILE_BOTH"
  echo "  Failed in new:  $FILE_NEW"
  echo "  Same plans:     $FILE_SAME"
  echo "  Plans differ:   $FILE_DIFF"
  exit 2
fi

IDS=$1

for i in $(<$IDS); do
  OLDERR=argus.$i.old.err
  NEWERR=argus.$i.new.err
  OLDOUT=argus.$i.old.out
  NEWOUT=argus.$i.new.out
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