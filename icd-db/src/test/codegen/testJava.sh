#!/bin/bash

# Test generated Java source code (called from CodeGenerationTests test case)
# usage: testJava.sh tempDir
# where tempDir contains the generated java source file

set -e
set -x
tempDir=$1
cd "$(dirname "$0")"
scriptDir="$(pwd)"
cd $tempDir
unzip $scriptDir/javaProject.zip
mv $tempDir/Test.java test/src/main/java/test/
cd test
sbt test


