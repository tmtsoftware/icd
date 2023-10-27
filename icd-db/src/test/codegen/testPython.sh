#!/bin/bash

# Test generated Python source code (called from CodeGenerationTests test case)
# usage: testPython.sh tempDir
# where tempDir contains the generated Python source file

set -e
set -x

tempDir=$1
cd "$(dirname "$0")"
scriptDir="$(pwd)"
cd $tempDir
unzip $scriptDir/pythonProject.zip
mv $tempDir/Test.py test
cd test
mkdir .venv
python3 -m venv .venv
pipenv install
. .venv/bin/activate
python -m pytest


