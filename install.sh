#!/bin/sh
#
# Creates a single install directory from all the csw stage directories.

dir=../install_icd

test -d $dir || mkdir -p $dir/bin $dir/lib $dir/conf
sbt stage "project root" stage

for i in bin lib ; do
    for j in */target/universal/stage/$i/* icd-web/icd-web-server/target/universal/stage/$i/* ; do
        cp -f $j $dir/$i
    done
done

rm -f $dir/bin/*.log.* $dir/bin/*.bat
