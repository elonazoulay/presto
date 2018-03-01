#!/bin/bash
ls argus.* | sed 's/argus.//;s/\.old.*//;s/\.new.*//' | sort -u
