#!/bin/bash

set -e

python3 -m pip install requests

python3 license_check/third_party_license_test.py ${@}
