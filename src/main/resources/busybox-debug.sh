#!/bin/bash
# This script can be used to quickly debug the Extraction code. It is meant to be copied to the working directory. The working 
# directory should contain the Extraction repo and the SPL repo, i.e.,
# WORKDIR
# | -- Extraction
# | -- busybox
# | -- busybox-debug.sh
rm Extraction-1.0.0-jar-with-dependencies.jar
rm -rf extraction-results
cd Extraction || exit
mvn package
clear
cd ..
mv Extraction/target/Extraction-1.0.0-jar-with-dependencies.jar .
java -jar Extraction-1.0.0-jar-with-dependencies.jar extraction_busybox.properties b35eef5383a4e7a6fb60fcf3833654a0bb2245e0 7de0ab21d939a5a304157f75918d0318a95261a3