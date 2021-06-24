#! /bin/bash
# This script can be used to quickly debug the VariabilityExtraction code. It is meant to be copied to the working directory. The working 
# directory should contain the VariabilityExtraction repo and the SPL repo, i.e.,
# WORKDIR
# | -- VariabilityExtraction
# | -- linux
# | -- linux-debug.sh
clear
echo "Removing old folders"
rm -rf extraction-results

echo "Packaging jar"
rm -rf VariabilityExtraction-1.0.0-jar-with-dependencies.jar
cd VariabilityExtraction || exit
mvn package
cp target/VariabilityExtraction-1.0.0-jar-with-dependencies.jar ..
cd ..
clear

echo "Executing variability extraction."
java -jar VariabilityExtraction-1.0.0-jar-with-dependencies.jar variability_analysis_Linux.properties db196935d9562abec4510f48d887bc1f1e054fcf