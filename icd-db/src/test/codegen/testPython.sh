#!/bin/bash

# Test generated Python source code (called from CodeGenerationTests test case)
# usage: testPython.sh tempDir
# where tempDir contains the generated Python source file

set -e
set -x
PYTHON=python3.13
tempDir=$1
cd "$(dirname "$0")"
scriptDir="$(pwd)"
cd $tempDir
unzip $scriptDir/pythonProject.zip
mv $tempDir/Test.py test
cd test
rm -rf .venv
mkdir .venv
$PYTHON -m venv .venv
. .venv/bin/activate
python -m pip install pytest
python -m pip install pipenv
pipenv install
python -m pytest


