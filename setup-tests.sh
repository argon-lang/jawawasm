#!/bin/bash -e

git submodule update --init

cargo install --locked --version 1.245.1 wasm-tools --root ./cargo-tools

