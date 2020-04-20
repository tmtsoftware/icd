#!/bin/sh

# This script can be used to start the icd web app on a public server.
# It can be used to run icdwebserver with publishing enabled and uploading disabled.

logdir=$HOME/.icd
test -d $logdir || mkdir $logdir
exec icdwebserver -Dicd.allowUpload=false -Dhttp.port=80 > $logdir 2>&1
