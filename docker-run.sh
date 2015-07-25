#!/usr/bin/env bash

# Starts MongoDB and icd-web-server inside a docker container

# Get the IP address (Use boot2docker for Mac. Linux it should be the same IP?)
if  [ `which boot2docker` ] ; then
    eval "$(boot2docker shellinit)"
    host=`boot2docker ip`
else
    host=`uname -n`
fi

# Start MongoDB
docker run -t -d -p 27017:27017 --name mongo mongo || exit 1

# Start the application
docker run -d -P -p 9000:9000 --name icdwebserver --link mongo abrighton/icdwebserver -Dicd.db.host=$host  || exit 1

# Note: For boot2docker, need to run this once the application is running to expose ports
# VBoxManage controlvm "boot2docker-vm" natpf1 "tcp-port9000,tcp,,9000,,9000";