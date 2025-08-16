#!/bin/sh

# This script can be used to start the icd web app on a public server.
# It can be used to run icdwebserver with publishing enabled and uploading disabled.

logdir=$HOME/.icd
logfile=$logdir/icd.log
test -d $logdir || mkdir $logdir
icd-git --ingestAll > $logfile 2>&1
exec icdwebserver -Dicd.isPublicServer=true -Dhttp.port=80 >> $logfile 2>&1 &
