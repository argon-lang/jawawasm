# JVMWASM

JVMWASM is an experimental WebAssembly interpreter written in Java.

## Setup

If using SDKMAN, run `sdk env` to use the expected versions of Java and Gradle.

In order to run the tests for this project, submodules need to be checked out and built.
JVMWASM does not support the text format.
The tests use the reference interpreter to convert the test format into binary modules.
To perform this setup, run the script `setup-tests.sh`.
This script will set up the submodule and build the tools needed to run the test suite.
If you run into issues building, see the [reference interpreter README](https://github.com/WebAssembly/spec/tree/main/interpreter#building).

