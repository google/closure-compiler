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

# Download and test closure-compiler-npm using the compiler from this commit.
#
# Requirements:
#   - PWD = closure-compiler-npm repo root
#
# Params:
#   1. File name of compiler_uberjar_deploy.jar
function main() {
  local compiler_jar="$1"

  # Download all NPM deps to "node_modules".
  yarn install

  # Copy the version of the compiler built as a pre-requisite of this script
  # into closure-compiler-npm. The NPM repo normally builds the compiler itself,
  # and puts the binary in these locations, so we're superceeding that here.
  #
  # Make sure to copy after yarn install so that the files don't get cleaned up.
  #
  # TODO(nickreid): This should be done using scripts from inside the NPM repo
  cp "${compiler_jar}" "packages/google-closure-compiler-java/compiler.jar"

  # Run the java tests inside closure-compiler-npm. This will use the version
  # of the compiler we just copied into it.
  #
  # The NPM repo is divided into multiple Yarn workspaces: one for each
  # variant of the compiler, including the Graal native builds. We only
  # need to test the java build.
  yarn workspace google-closure-compiler run test --java-only --colors
}

main "$@"
