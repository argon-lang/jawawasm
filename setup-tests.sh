#!/bin/bash -e

git submodule update --init
cd webassembly-spec/interpreter
make

