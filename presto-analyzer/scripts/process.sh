#!/bin/bash

# Make sure all the scripts/jars are in the current directory
# Make sure there is no output directory already

. ./command.sh > queries.all 2> queries.all.err
./presto-analyzer-*-SNAPSHOT-executable.jar config
cd output
../get_ids.sh > ids
../run_all.sh ids
../analyze.sh ids

# Common errors that indicate rerun/manual verify
# Non-terminated statement
# Table.*does not exist
# Access Denied:
# No Presto coordinators found

