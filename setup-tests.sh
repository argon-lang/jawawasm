#!/bin/bash -e

git submodule update --init

# The latest version has a bug with json-from-wast
#cargo install --locked --version 1.235.0 wasm-tools --root ./cargo-tools

# Use the newer git version
cargo install --locked --git https://github.com/bytecodealliance/wasm-tools.git wasm-tools --rev 7aee4a379dd2bbeb3e707cf85bf3434381d4afbf --root ./cargo-tools

