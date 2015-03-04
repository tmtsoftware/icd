#!/bin/sh
#
# Creates a single install directory from all the csw stage directories.

dir=../install

test -d $dir || mkdir -p $dir/{bin,lib,conf}
sbt publish-local stage
for i in bin lib ; do cp -f target/universal/stage/$i/* $dir/$i/; done
rm -f $dir/bin/*.log.* $dir/bin/*.bat
