#!/bin/bash
# Run the local build of CC in target/. Make sure to run build.sh before running
# this script.
java -jar target/closure-compiler-1.0-SNAPSHOT.jar $@
