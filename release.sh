#!/bin/bash -i
# Copyright 2025 Google Inc. All Rights Reserved
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

set -e

readonly SCRIPT_NAME=$(basename "$0")
readonly MAVEN_PROJECTS="parent main shaded unshaded externs"

deploy_to_sonatype=true
sonatype_auto_publish=false
bazel_executable=""
bazel_bin=$(pwd)/bazel-bin
bazel_define_flag=""

usage() {
  echo ""
  echo "${SCRIPT_NAME}: Build and release script for Closure Compiler."
  echo ""
  echo "Usage: ${SCRIPT_NAME} [--no-deploy] [--sonatype-auto-publish] [--help]"
  echo ""
  echo "Options:"
  echo "  --help                    Print this help output and exit."
  echo "  --no-deploy               Prepare all the maven artifacts but skip the deployment to Sonatype."
  echo "  --sonatype-auto-publish   Automatically publish the staging repository to Sonatype after deployment."
  echo ""
  echo "Use the environment variable RELEASE_NUM to specify the release number:"
  echo "  RELEASE_NUM=v1234567890 ./${SCRIPT_NAME}"
}

parse_arguments() {
  while [[ "$#" -gt 0 ]]; do
    case "$1" in
      --no-deploy)
        deploy_to_sonatype=false
        shift
        ;;
      --sonatype-auto-publish)
        sonatype_auto_publish=true
        shift
        ;;
      --help)
        usage
        exit 0
        ;;
      *)
        echo "Error: unexpected option '$1'"
        usage
        exit 1
        ;;
    esac
  done
}

check_prerequisites() {
  if [[ ! -f "MODULE.bazel" ]]; then
    echo "Error: This script must be run from the root of the Bazel repository (where MODULE.bazel exists)."
    exit 1
  fi

  if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed or not in your PATH."
    echo "Please install Maven to proceed."
    exit 1
  fi

  # Check for Bazel or Bazelisk
  if command -v bazelisk &> /dev/null; then
    bazel_executable="bazelisk"
  elif command -v bazel &> /dev/null; then
    bazel_executable="bazel"
  else
    echo "Error: Neither Bazel nor Bazelisk is installed or in your PATH."
    echo "Please install either Bazel or Bazelisk to proceed."
    exit 1
  fi

 # TODO: consider passing the release number as argument ?
 # Check if the global variable RELEASE_NUM exists
 if [[ -z "${RELEASE_NUM}" ]]; then
   echo "Warning: Global variable RELEASE_NUM is not set. Creating a snapshot release."
 else
   echo "Creating release ${RELEASE_NUM}."
   bazel_define_flag="--define=COMPILER_VERSION=${RELEASE_NUM}"
 fi
}

build_and_prepare() {
  local project="$1"
  local bazel_target=":compiler_${project}_bundle"
  local mvn_artifact_id="closure-compiler-${project}"
  if [[ "${project}" == "shaded" ]]; then
    mvn_artifact_id="closure-compiler"
  fi

  echo "Processing project: ${project}"
  # Build the bundle jar file using Bazel or Bazelisk
  echo "Building Bazel target: ${bazel_target}"
  "${bazel_executable}" build "${bazel_target}" ${bazel_define_flag}

  # Prepare the artifact for Maven
  cd "${mvn_temp_wd}"
  echo "Extracting bundle content: ${bazel_bin}/${mvn_artifact_id}_bundle.jar to ${mvn_temp_wd}"
  jar xf "${bazel_bin}/${mvn_artifact_id}_bundle.jar"

  # If a main JAR file exist, extract its content in the `classes` directory
  # maven will repackage everything in its own jar.
  if [[ -f "${mvn_artifact_id}.jar" ]]; then
    echo "Extracting: ${mvn_artifact_id}.jar in the classes directory."
    mkdir "${mvn_artifact_id}-classes"
    (cd "${mvn_artifact_id}-classes" && jar xf "${mvn_temp_wd}/${mvn_artifact_id}.jar")
    rm "${mvn_artifact_id}.jar"
  fi

  cd - > /dev/null # Go back to the original directory
}

package_and_deploy() {
  cd "${mvn_temp_wd}"

  if [[ "${deploy_to_sonatype}" == true ]]; then
    echo "Deploying Maven artifacts to Sonatype..."
    mvn -f closure-compiler-parent.pom.xml deploy "-DautoPublish=${sonatype_auto_publish}"
    rm -rf "${mvn_temp_wd}"
  else
    echo "Packaging Maven artifacts..."
    mvn -f closure-compiler-parent.pom.xml package
    echo "Maven artifacts created in: ${mvn_temp_wd}"
  fi

  cd - > /dev/null # Go back to the original directory

}

# --- Main Script ---
parse_arguments "$@"
check_prerequisites

echo "Starting build and release process."

mvn_temp_wd=$(mktemp -d)

for project in ${MAVEN_PROJECTS}; do
  build_and_prepare "${project}" "${mvn_temp_wd}"
  echo ""
done

package_and_deploy

echo "Build and release process completed."

exit 0
