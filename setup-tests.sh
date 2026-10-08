#!/bin/bash -e

git submodule update --init

cargo install --locked --version 1.261.0 wasm-tools --root ./cargo-tools

