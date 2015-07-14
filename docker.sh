#!/bin/sh
#
# Creates a docker image for icdwebserver.
#
# Note: This script must be run in the docker environment, for example in the boot2docker shell.
# To start again from scratch, run these commands in a bash shell (assumes boot2docker is installed):
#
# boot2docker delete
# boot2docker init
# boot2docker up
# eval "$(boot2docker shellinit)"
# docker run -t -d -p 27017:27017 --name mongo mongo

# Get the IP address (XXX TODO: Mac specific: for Linux it should be the same IP?)
host=`boot2docker ip`

# user name for docker push
user=abrighton

# version tag
version=latest

sbt docker:stage || exit 1
cd icd-web/icd-web-server/target/docker/stage
docker build -t $user/icdwebserver:$version .  || exit 1

#docker run -d -P -p $port:$port --name icdwebserver --link mongo icdwebserver -Dicd.db.host=$host  || exit 1


