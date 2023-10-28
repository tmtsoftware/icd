#!/bin/bash

# Test generated Typescript source code (called from CodeGenerationTests test case)
# usage: testTypescript.sh tempDir
# where tempDir contains the generated Typescript source file

set -e
set -x

tempDir=$1
cd "$(dirname "$0")"
scriptDir="$(pwd)"
cd $tempDir
unzip $scriptDir/typescriptProject.zip
mv $tempDir/Test.ts test/src
cd test
npm install
npm run test


