#!/bin/sh
#
# Creates a single install directory from all the csw stage directories.

dir=../install_icd

hash sbt 2>/dev/null || { echo >&2 "Please install sbt first.  Aborting."; exit 1; }

test -d $dir || mkdir -p $dir/bin $dir/lib $dir/conf
sbt clean stage "project root" clean stage

for i in bin lib; do
    for j in */target/universal/stage/$i/* ; do
        cp -f $j $dir/$i
    done
done

for i in bin lib conf; do
    for j in icd-web/icd-web-server/target/universal/stage/$i/* ; do
        cp -f $j $dir/$i
    done
done

rm -f $dir/bin/*.log.* $dir/bin/*.bat
