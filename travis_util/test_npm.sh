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

# Set build dir to current working dir if not set by Travis, just so it's easy
# to locally run this script.
if [ -z $TRAVIS_BUILD_DIR ]; then
  TRAVIS_BUILD_DIR=`pwd`
fi

if [ -z $TRAVIS_BUILD_DIR ]; then
  TRAVIS_BUILD_DIR=`pwd`
fi

# Run yarn install so that dev dependencies are available
yarn install && cd ${TRAVIS_BUILD_DIR}/node_modules/closure-compiler-npm && yarn install

# Copy the compiler after yarn install so that the files don't get cleaned up
cp ${TRAVIS_BUILD_DIR}/target/closure-compiler-1.0-SNAPSHOT.jar ${TRAVIS_BUILD_DIR}/node_modules/closure-compiler-npm/packages/google-closure-compiler-java/compiler.jar
cp ${TRAVIS_BUILD_DIR}/target/closure-compiler-1.0-SNAPSHOT.jar ${TRAVIS_BUILD_DIR}/node_modules/closure-compiler-npm/packages/google-closure-compiler-osx/compiler.jar
cp ${TRAVIS_BUILD_DIR}/target/closure-compiler-1.0-SNAPSHOT.jar ${TRAVIS_BUILD_DIR}/node_modules/closure-compiler-npm/packages/google-closure-compiler-linux/compiler.jar
cp ${TRAVIS_BUILD_DIR}/target/closure-compiler-1.0-SNAPSHOT.jar ${TRAVIS_BUILD_DIR}/node_modules/closure-compiler-npm/packages/google-closure-compiler-windows/compiler.jar
cp ${TRAVIS_BUILD_DIR}/target/closure-compiler-gwt-1.0-SNAPSHOT/jscomp/jscomp.js ${TRAVIS_BUILD_DIR}/node_modules/closure-compiler-npm/packages/google-closure-compiler-js/jscomp.js

# Build then test each project
cd ${TRAVIS_BUILD_DIR}/node_modules/closure-compiler-npm && yarn workspaces run build && yarn workspaces run test
