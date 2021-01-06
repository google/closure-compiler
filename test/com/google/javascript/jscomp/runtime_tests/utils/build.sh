#!/bin/bash
# Copyright 2020 Google Inc. All Rights Reserved
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS-IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

if [ -z $1 ]; then
  COMPILATION_LEVEL="SIMPLE"
else
  COMPILATION_LEVEL=$1
fi

# This directory.
THIS_DIR=$(dirname $0)

# Location of project root.
PROJECT_ROOT=$(readlink -f $THIS_DIR/../../../../../../..)

# Get the location of the local compiler in this directory, if it exists.
# If it doesn't, build it, then resume execution.
LOCAL_COMPILER="$PROJECT_ROOT/target/closure-compiler-1.0-SNAPSHOT.jar"
if [ ! -f "$LOCAL_COMPILER" ]; then
  echo -e "\nCompiler JAR not built. Building...\n" && yarn build:fast
fi

# Build tests from the $TEST_DIR directory, where files like
# `array_pattern_test.js` are stored.
echo -e "\nBuilding runtime tests..."
TEST_DIR="$THIS_DIR/.."

# Get the absolute path of the test directory.
ABS_PATH=$(readlink -f $TEST_DIR)

compileRuntimeTests(){
  local -i i=0
  local file
  for file in $@; do

    # /path/to/file.js -> /path/to/file
    local file_base=$(echo $file | rev | cut -f 2- -d '.' | rev)
    # /path/to/file -> file
    local test_name=$(basename $file_base)
    # /path/to/file.ext -> /path/to
    local test_loc=$(dirname $file)

    # Make sure the build directory exists.
    mkdir -p $test_loc/build

    # Echo a percentage progress indicator.
    ((i += 1))
    echo " $((100 * $i / $#))% | $test_name"

    # Output the test file, which will be executed in JSDOM.
    cat > $test_loc/build/$test_name.html << EOF
<html>
<head>
<title>$test_name</title>
<script defer>
$(
  java -server -XX:+TieredCompilation \
    -jar $LOCAL_COMPILER \
    -O $COMPILATION_LEVEL \
    --language_in ES_NEXT \
    --language_out NO_TRANSPILE \
    --process_common_js_modules \
    --module_resolution NODE \
    --dependency_mode PRUNE \
    --js $PROJECT_ROOT/node_modules/google-closure-library/ \
    --js $ABS_PATH/ \
    --entry_point $file
)
</script>
</head>
</html>
EOF

  done
}

# build tests
time(
  compileRuntimeTests $(find $ABS_PATH -type f -name '*_test.js')
)