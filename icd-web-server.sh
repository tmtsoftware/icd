#!/bin/sh

# This script should be modified for use on a public server.
# It can be used to run icdwebserver with publishing enabled, uploading disabled,
# using https with a given certificate.

# Edit these settings!
#host=localhost
#keystore=/shared/work/tmt/csw/icd/icd-web-server/conf/generated.keystore
#secretKey='U3spB5^7L;EK_:<=OKxwpOJGM:0JsAyTu2pWhKsqbiGMO@tT_8voDNs:x7q=x?e<'

exec icdwebserver \
  -Dicd.allowUpload=false \
  -Dhttp.port=80

#  -Dhttp.port=disabled \
#  -Dhttps.port=9443 \
#  -Dhttps.address=$host \
#  -Dplay.server.https.keyStore.path=$keystore \
#  -Dplay.http.secret.key=$secretKey

