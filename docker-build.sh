#!/bin/sh
#
# Builds a docker image for icdwebserver.
#
# Note: This script tries to run in the docker environment, using the boot2docker shell, if found.
# To start again from scratch, run these commands in a bash shell (assumes boot2docker is installed):
#
# boot2docker delete
# boot2docker init
# boot2docker up

# Get the IP address (Use boot2docker for Mac. Linux it should be the same IP?)
if  [ `which boot2docker` ] ; then
    eval "$(boot2docker shellinit)"
    host=`boot2docker ip`
else
    host=`uname -n`
fi

# user name for docker push
user=abrighton

# version tag
version=latest

sbt docker:stage || exit 1
cd icd-web/icd-web-server/target/docker/stage
docker build -t $user/icdwebserver:$version .  || exit 1

# Push to docker hub...
# docker push abrighton/icdwebserver:latest

# Note: For boot2docker, need to run this once the application is running to expose ports
# VBoxManage controlvm "boot2docker-vm" natpf1 "tcp-port9000,tcp,,9000,,9000";
