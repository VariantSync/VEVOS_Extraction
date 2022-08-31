#! /bin/bash
# This script can be used to quickly debug the Extraction code. It is meant to be copied to the working directory. The working 
# directory should contain the Extraction repo and the SPL repo, i.e.,
# WORKDIR
# | -- Extraction
# | -- linux
# | -- linux-debug.sh
clear
echo "Removing old folders"
rm -rf extraction-results

echo "Packaging jar"
rm -rf Extraction-jar-with-dependencies.jar
cd Extraction || exit
mvn package
cp target/Extraction-jar-with-dependencies.jar ..
cd ..
clear

echo "Executing ground truth extraction."
java -jar Extraction-jar-with-dependencies.jar extraction_linux.properties db196935d9562abec4510f48d887bc1f1e054fcf