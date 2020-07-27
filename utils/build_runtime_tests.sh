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

#!/bin/bash

# to translate from relative dir
abs_dirname() {
  echo "$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
}

LOCAL_COMPILER=$(dirname ..)/target/closure-compiler-1.0-SNAPSHOT.jar
echo $LOCAL_COMPILER
if [ ! -f "$LOCAL_COMPILER" ]; then
  echo -e "\nCompiler JAR not built. Building...\n" && yarn build:fast
fi

echo -e "\nBuilding runtime tests..."
TEST_DIR="test/com/google/javascript/jscomp/runtime_tests"

if [ -z $1 ]; then
  ABS_PATH=$(abs_dirname "./$TEST_DIR")
else
  ABS_PATH=$(abs_dirname "$1")
fi

i=0
compileRuntimeTests(){
  for FILE in $@; do

    FILE_BASE=$(echo $FILE | rev | cut -f 2- -d '.' | rev)
    TEST_NAME=$(basename $FILE_BASE)
    TEST_LOC=$(dirname $FILE)

    # make build dir
    mkdir -p $TEST_LOC/build

    ((i += 1))
    echo " $((100 * $i / $#))% | $TEST_NAME"

    echo "
<html>
<head>
<title>$TEST_NAME</title>
<script defer>
$(
  java -server -XX:+TieredCompilation \
    -jar $LOCAL_COMPILER \
    --language_in ES_NEXT \
    --language_out NO_TRANSPILE \
    --process_common_js_modules \
    --module_resolution NODE \
    --dependency_mode PRUNE \
    --js node_modules/google-closure-library/ \
    --js $ABS_PATH/ \
    --entry_point $FILE
)
</script>
</head>
</html>" > $TEST_LOC/build/$TEST_NAME.html

  done
}

# build tests
time(
  compileRuntimeTests $(find $ABS_PATH -type f -name '*_test.js')
)