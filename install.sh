#!/bin/sh
#
# Creates a single install directory from all the csw stage directories.

dir=../install_icd

test -d $dir || \
    for i in bin lib conf; do mkdir -p $dir/$i; done
sbt stage "project root" stage
for i in bin lib ; do cp -f */target/universal/stage/$i/* $dir/$i/; done
for i in bin lib conf ; do cp -f icd-web/icd-web-server/target/universal/stage/$i/* $dir/$i/; done
rm -f $dir/bin/*.log.* $dir/bin/*.bat

