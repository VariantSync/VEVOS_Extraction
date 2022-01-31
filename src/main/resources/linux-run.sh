#! /bin/bash
# This script can be used to quickly run the ground truth extraction for Linux. It is meant to be copied to the working directory. The working
# directory should contain the Extraction repo and the SPL repo, i.e.,
# WORKDIR
# | -- Extraction
# | -- linux
# | -- linux-extract.sh
echo "Removing previous results"
rm -rf extraction-results
echo "Executing ground truth extraction for Linux."
java -jar Extraction-1.0.0-jar-with-dependencies.jar extraction_linux.properties v4.1 v4.2