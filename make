#!/bin/bash

set -e
pushd ../forge/mcp/
  ./recompile.sh
  ./reobfuscate_srg.sh
popd

./package


date
