#!/usr/bin/env bash
#
# Creates a single install directory from all the csw stage directories.
#
# By default does a clean install. Use `install.sh -nc` to not do a clean build.

# See build.sbt: Causes fullOpt to be used to optimize the generated JS code
export SCALAJS_PROD=true

dir=../install_icd
rm -rf $dir

# Make sure we can find sbt
hash sbt 2>/dev/null || { echo >&2 "Please install sbt first.  Aborting."; exit 1; }

# Some scalajs related sbt plugins depend on node.js,
# but the executable has different names on different systems
if hash nodejs 2>/dev/null ; then
    NODEJS=`hash -t nodejs`
elif hash node 2>/dev/null ; then
    NODEJS=`hash -t node`
else
    echo >&2 "Please install node.js first.  Aborting."
    exit 1
fi
# Should not be needed? See https://github.com/sbt/sbt-less/issues/95
export SBT_OPTS="-Dsbt.jse.engineType=Node -Dsbt.jse.command=$NODEJS"

for i in $dir $dir/bin $dir/lib $dir/conf; do test -d $i || mkdir $i; done

test "$1" == "-nc" || sbt "project root" clean

sbt "project root" stage

for i in bin lib; do
    for j in target/universal/stage/$i/* ; do
        cp -f $j $dir/$i
    done
done

for i in bin lib conf; do
    for j in icd-web-server/target/universal/stage/$i/* ; do
        cp -f $j $dir/$i
    done
done

chmod +x icd-web-server.sh
cp icd-web-server.sh $dir/bin

rm -f $dir/bin/*.log.* $dir/bin/*.bat
