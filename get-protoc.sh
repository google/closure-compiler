#!/bin/bash
# 
# Gets the version of protoc needed to build Closure, and prints its path.
# Downloads sources and builds them if needed, and caches the binary in ~/.protobuf.
#
# Note: the version of protobuf-compiler available through
# Travis CI's addons.apt.packages mechanism is 2.4.1, and protoc's version really
# needs to match that of the protobuf-java dependency.
#
set -eu

PROTOBUF_VERSION=$( \
  cat pom-main.xml | \
  grep "<protobuf.version>" | \
  sed -E 's/.*>(.*)<.*/\1/' \
)

# Where we'll install protoc (on Travis CI, will be cached).
PROTOBUF_DIR=$HOME/.protobuf/$PROTOBUF_VERSION
PROTOC=$PROTOBUF_DIR/bin/protoc

function getAndBuildProtoc() {
  local name=protobuf-$PROTOBUF_VERSION

  rm -fR $name*
  wget https://protobuf.googlecode.com/files/$name.tar.gz
  tar -xzvf $name.tar.gz
  cd $name

  ./configure --prefix=$PROTOBUF_DIR
  make
  make install

  cd ..
  rm -fR $name*
}

if [[ ! -x $PROTOC ]]; then
  getAndBuildProtoc >&2
fi

# Print the path to protoc.
echo $PROTOC