#!/bin/sh

# This script can be used to start the icd web app on a public server.
# It can be used to run icdwebserver with publishing enabled and uploading disabled.

logdir=$HOME/.icd
logfile=$logdir/icd.log
test -d $logdir || mkdir $logdir
exec icdwebserver -Dicd.allowUpload=false -Dhttp.port=80 > $logfile 2>&1 &

