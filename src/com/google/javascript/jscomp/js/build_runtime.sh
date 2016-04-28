#!/bin/sh

# Assembles the es6_runtime.js file from the inputs.

help() {
  cat <<EOF >&2
Usage: build_runtime.sh [OPTIONS]
Options:
  --compiler=PATH/TO.JAR
  --js_output_file=FILENAME
EOF
  exit $1
}

dir="$(dirname $0)"
compiler="$dir/../../../../../../build/compiler.jar"
output=/dev/stdout

while [ $# -gt 0 ]; do
  arg=$1
  shift
  case "$arg" in
    (--*=*) set -- "${arg#*=}" "$@"; arg=${arg%%=*} ;;
  esac
  case "$arg" in
    (--help) help 0 ;;
    (--compiler) compiler=$1; shift ;;
    (--js_output_file) output=$1; shift ;;
    (*) echo "Unknown option: $arg" >&2; help 1 ;;
  esac
done

{
  cat license.js
  echo -e '\n// GENERATED FILE. DO NOT EDIT. REBUILD WITH build_runtime.sh.\n'
  java -jar $compiler "${args[@]}" \
       --formatting=PRETTY_PRINT \
       --noinject_libraries \
       --compilation_level=WHITESPACE_ONLY \
       --preserve_type_annotations \
       --language_in=ES6_STRICT \
       --language_out=ES5_STRICT \
       --js="$dir/es6/runtime.js" \
       --js="$dir/es6/object.js" \
      | sed 's/ *$//g'
  echo
} >| $output
