#!/bin/bash -e

git submodule update --init

cargo install --locked --version 1.253.0 wasm-tools --root ./cargo-tools

