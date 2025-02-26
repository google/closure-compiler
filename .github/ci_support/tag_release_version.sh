#!/bin/bash
set -e

err_exit() {
  printf "$@\n" >&2
  exit 1
}

usage_exit() {
  cat <<_eof_
USAGE: $(basename "$0") PIPER_CUT_CL VERSION_ID

PIPER_CUT_CL - # of the CL where a release was cut
VERSION_ID - the id of the release, in the format vYYYYMMDD

_eof_
  printf '====\n'
  err_exit "$@"
}

# Expect a single argument specifying the release cut CL number
find_piper_cut_commit() {
  local -r cut_cl=$1
  # 1. Get git commit IDs and their corresponding CLs from the git log.
  # 2. select and print the commit ID associated with the highest CL# that is
  # still lower than the cut CL
  git_commit_cl_log | last_commit_before_cl "$cut_cl"
}

# Read the git log and produce lines of the form
#
# `COMMIT_ID CL_NUMBER`
git_commit_cl_log() {
  # PiperOrigin-RevId: indicates the CL number
  git log master \
    --format='format:%h %(trailers:key=PiperOrigin-RevId,separator=,valueonly=true)'
}

# Expect a single argument specifying the release cut CL number
#
# Expect input lines in the form:
# `COMMIT_ID [CL_NUMBER]`
#
# Further expect that the CL numbers are in decscending order.
#
# Print the first CL number we encounter that is lower than
# the argument CL number.
#
# Report an error and return a non-zero value if we are unable
# to print a result CL number for any reason.
last_commit_before_cl() {
  awk -v cut_cl="$1" '
  # In some cases there is no CL associated with a commit.
  # We skip all of those by ignoring lines with only one field.
  NF > 1 {
    # force the CL to be interpreted as an integer
    cl = + $2
    if (cl <= cut_cl) {
      found_commit = 1
      print $1
      # skip reading any more records
      exit
    }
  }

  END {
    # This section always gets executed
    if (!found_commit) {
      printf "no commit found: Earliest CL seen is %d which is > %d\n",
        cl, cut_cl > "/dev/stderr"
      exit 1
    }
  }
  '
}

main() {
    local -r piper_cut_cl=$1
    local -r version_id=$2

    existing=$(git tag -l "$version_id")
    # Check if the version ID is already tagged.
    if [ -n "$existing" ]; then
      echo "Tag already exists for version '$version_id'"
      exit 0
    fi

    COMMIT=$(find_piper_cut_commit $piper_cut_cl)
    # check that the commit variable is not empty
    if [[ -z "$COMMIT" ]]; then
      echo "No commit found prior to CL $piper_cut_cl"
      exit 1
    fi

    echo "Tagging commit $COMMIT with version $version_id"
    git tag -a "$version_id" -m "Release $version_id" "$COMMIT"
    git push origin master "$version_id"
}

(( $# == 2 )) || usage_exit 'incorrect argument count\n' "$@"

readonly PIPER_CUT_CL=$1
readonly VERSION_ID=$2

if [[ ! "$PIPER_CUT_CL" =~ ^[0-9]+$ ]]; then
    usage_exit "Invalid Piper cut CL number: '$PIPER_CUT_CL'"
fi

if [[ ! "$VERSION_ID" =~ ^v[0-9]+$ ]]; then
    usage_exit "Invalid version ID: '$VERSION_ID'. Expected format is vYYYYMMDD."
fi

main "$PIPER_CUT_CL" "$VERSION_ID"

