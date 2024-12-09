#!/bin/bash

set -e

if ! python3 -c "import requests"; then
  if python3 -m pip --version; then
    echo Installing \"requests\" package.
    python3 -m pip install requests
  else
    echo Error: \"requests\" package is not available. \
      Cannot install automatically since \"pip\" is also not available.
    echo Please manually install \"requests\" or \"pip\" package.
    exit 1
  fi
fi

python3 license_check/third_party_license_test.py ${@}
