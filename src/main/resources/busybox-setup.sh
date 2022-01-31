#! /bin/bash
# This script was written for development purposes. Its purpose is to set up the working directory for the variability extraction. 
# WORKDIR
# | -- Extraction
# | -- busybox
# | -- busybox-debug.sh
echo "Creating directory 'busybox-extraction'"
mkdir busybox-extraction
echo "Changing directory into busybox-extraction"
cd busybox-extraction
echo "Cloning Extraction repo"
git clone git@gitlab.informatik.hu-berlin.de:mse/VariantSync/Extraction.git
echo "Building Extraction"
cd Extraction
mvn package
echo "Copying resources required to run the extraction."
cp target/Extraction-*-jar-with* src/main/resources/KernelHaven.jar src/main/resources/extraction_busybox.properties src/main/resources/busybox-run.sh ..
cd ..
echo "Cloning BusyBox"
git clone https://git.busybox.net/busybox/
echo "Done."