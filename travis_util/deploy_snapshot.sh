#!/bin/bash

# see https://coderwall.com/p/9b_lfq

set -e -u

function mvn_deploy() {
  mvn clean source:jar deploy \
    --settings="$(dirname $0)/settings.xml" -DskipTests=true -Dstyle.color=always "$@"
}

# Restrict when snapshots are published
# * Only for Google's repo, not any other forks
# * Only deploy the build for the latest JDK version
# * Not when Travis was run to check a pull request.
# * Only for the master branch.
if [ "$TRAVIS_REPO_SLUG" == "google/closure-compiler" ] && \
   [ "$TRAVIS_JDK_VERSION" == "oraclejdk8" ] && \
   [ "$TRAVIS_PULL_REQUEST" == "false" ] && \
   [ "$TRAVIS_BRANCH" == "master" ]; then
  echo "Publishing Maven snapshot..."

  mvn_deploy

  echo "Maven snapshot published."
fi
