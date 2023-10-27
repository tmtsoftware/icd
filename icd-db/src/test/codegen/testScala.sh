#!/bin/bash

# Test generated Scala source code (called from CodeGenerationTests test case)
# usage: testScala.sh tempDir
# where tempDir contains the generated scala source file

set -e
set -x
tempDir=$1
cd "$(dirname "$0")"
scriptDir="$(pwd)"
cd $tempDir
unzip $scriptDir/scalaProject.zip
mv $tempDir/Test.scala test/src/main/scala/test/
cd test
sbt test


