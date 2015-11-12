#!/bin/sh

# Generate dummy subsystem data for testing

# Subsystems to generate (already ahd NFIRAOS and TCS) 
subsystems="AOESW APS CIS CLN COAT COOL CSW DMS DPS ENC ESEN ESW HNDL HQ IRIS IRMS LGSF M1CS M2S M3S MCS NSCU OSS ROAD SCMS SOSS STR SUM TINC TINS WFOS"

template=NFIRAOS
t=`echo $template | awk '{print tolower($0)}'`

for s in $subsystems; do
    echo $s
    rm -rf $s
    mkdir $s
    cp -r $template/* $s
    find $s -type f -exec sed -i "" -e "s/$template/$s/g" {} \;
    lower=`echo $s | awk '{print tolower($0)}'`
    find $s -type f -exec sed -i "" -e "s/$t\./$lower./g" {} \;
done
 
 	
