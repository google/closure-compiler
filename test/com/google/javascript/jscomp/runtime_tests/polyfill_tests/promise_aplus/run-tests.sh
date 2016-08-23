#!/bin/bash -e
#
# Copyright 2016 The Closure Compiler Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# 

if ! type npm >/dev/null 2>&1; then
    echo "npm must be installed to run these tests" >&2
    exit 1
fi

if ! type java >/dev/null 2>&1; then
    echo "java must be installed to run these tests" >&2
    exit 1
fi

cd $(dirname $0)
TEST_HOME=$(pwd)
cd ../../../../../../../..
JSCOMP_HOME=$(pwd)
JSCOMP_TARGET=$JSCOMP_HOME/target
JSCOMP_JAR=$JSCOMP_TARGET/closure-compiler-1.0-SNAPSHOT.jar

if [[ ! -r $JSCOMP_JAR ]]; then
    echo "You must build the compiler before running these tests" >&2
    exit 1
fi

TEST_TARGET=$JSCOMP_TARGET/promise_aplus
mkdir -p $TEST_TARGET
cd $TEST_TARGET

COMPILED_TEST_ADAPTER=test-adapter-compiled.js
java -jar $JSCOMP_JAR \
    --externs $TEST_HOME/test-externs.js \
    --js $TEST_HOME/test-adapter.js \
    --js_output_file $COMPILED_TEST_ADAPTER

npm install promises-aplus-tests
$(npm bin)/promises-aplus-tests $COMPILED_TEST_ADAPTER
