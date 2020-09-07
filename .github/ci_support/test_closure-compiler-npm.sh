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

function main() {
  # Run yarn install to download closure-compiler-npm, which is listed as a dev
  # dependency.
  yarn install

  (
    cd node_modules/closure-compiler-npm
    yarn install
  )

  # Copy the compiler after yarn install so that the files don't get cleaned up
  # TODO(nickreid): This should be done using scripts from inside the NPM repo
  local compiler_jar=bazel-bin/compiler_unshaded_deploy.jar
  local packages_dir=node_modules/closure-compiler-npm/packages
  cp "$compiler_jar" "$packages_dir/google-closure-compiler-java/compiler.jar"
  cp "$compiler_jar" "$packages_dir/google-closure-compiler-osx/compiler.jar"
  cp "$compiler_jar" "$packages_dir/google-closure-compiler-linux/compiler.jar"
  cp "$compiler_jar" "$packages_dir/google-closure-compiler-windows/compiler.jar"

  # Build then test each project
  (
    cd node_modules/closure-compiler-npm
    yarn workspaces run build
    yarn workspaces run test
  )
}

main "$@"
