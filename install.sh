#!/usr/bin/env bash
#
# Creates a single install directory from all the csw stage directories.
#
# By default does a clean install. Use `install.sh -nc` to not do a clean build.

# See build.sbt: Causes fullOpt to be used to optimize the generated JS code
export SCALAJS_PROD=true

#java_version=`java -version 2>&1 | head -1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1`
#if test $java_version != '11'; then
#  echo "This project requires Java-11, but you have java-$java_version: Aborting.";
#  exit 1
#fi

# Make sure we can find sbt for the build
hash sbt 2>/dev/null || { echo >&2 "Please install sbt first.  Aborting."; exit 1; }

# swagger-codegen is required for generating HTML from OpenApi JSON files for HTTP service APIs
hash cs 2>/dev/null || { echo >&2 "Please install cs (https://get-coursier.io/) first.  Aborting."; exit 1; }
hash swagger-codegen 2>/dev/null || { echo >&2 "Please install swagger-codegen first (use: cs install --contrib swagger-codegen).  Aborting."; exit 1; }

# Check version of swagger-codegen, since there is a problem with older versions and Java-17
sc_version=`swagger-codegen version`
test $"sc_version" == "3.0.35" -o `echo -e "$sc_version\n3.0.35" | sort -Vr | tail -1` == "3.0.35" || { echo >&2 "Version of swagger-codegen is too old: Please update (run: cs update swagger-codegen).  Aborting."; exit 1; }

# Graphviz is required for UML support and icd-viz
hash dot 2>/dev/null || { echo >&2 "Please install graphviz first (See https://graphviz.org/download/).  Aborting."; exit 1; }

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

dir=../install_icd
rm -rf $dir

for i in $dir $dir/bin $dir/lib $dir/conf; do test -d $i || mkdir $i; done

test "$1" == "-nc" || sbt clean

sbt stage

for i in bin lib conf; do
    for j in target/universal/stage/$i/* ; do
        cp -rf $j $dir/$i
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
