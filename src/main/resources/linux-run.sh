#! /bin/bash
# This script can be used to quickly run the variability extraction for Linux. It is meant to be copied to the working directory. The working 
# directory should contain the VariabilityExtraction repo and the SPL repo, i.e.,
# WORKDIR
# | -- VariabilityExtraction
# | -- linux
# | -- linux-extract.sh
echo "Removing previous results"
rm -rf extraction-results
echo "Executing variability extraction of Linux."
java -jar VariabilityExtraction-1.0.0-jar-with-dependencies.jar variability_analysis_Linux.properties v4.1 v4.2