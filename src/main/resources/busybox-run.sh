#!/bin/bash
# This script can be used to quickly run the variability extraction for BusyBox. It is meant to be copied to the working directory. The working 
# directory should contain the Extraction repo and the SPL repo, i.e.,
# WORKDIR
# | -- Extraction
# | -- busybox
# | -- busybox-extract.sh
echo "Removing previous results"
rm -rf extraction-results
echo "Executing ground truth extraction for BusyBox."
java -jar Extraction-1.0.0-jar-with-dependencies.jar extraction_busybox.properties