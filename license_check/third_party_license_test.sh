#!/bin/bash

set -e

ROOT_DIR=${TEST_SRCDIR}/com_google_javascript_jscomp/third_party/

python3 ${ROOT_DIR}/third_party_license_test.py ${ROOT_DIR}/maven_artifacts.bzl ${ROOT_DIR}/THIRD_PARTY_NOTICES "${@:2}"
