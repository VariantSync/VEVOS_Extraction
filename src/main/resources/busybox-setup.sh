#! /bin/bash
# This script was written for development purposes. Its purpose is to set up the working directory for the variability extraction. 
# WORKDIR
# | -- VariabilityExtraction
# | -- busybox
# | -- busybox-debug.sh
echo "Creating directory 'busybox-analysis'"
mkdir busybox-analysis
echo "Changing directory into busybox-analysis"
cd busybox-analysis
echo "Cloning VariabilityExtraction repo"
git clone git@gitlab.informatik.hu-berlin.de:mse/VariantSync/VariabilityExtraction.git
echo "Building VariabilityExtraction"
cd VariabilityExtraction
mvn package
echo "Copying resources required to run the extraction."
cp target/VariabilityExtraction-*-jar-with* src/main/resources/KernelHaven.jar src/main/resources/variability_analysis_BusyBox.properties src/main/resources/busybox-run.sh ..
cd ..
echo "Cloning BusyBox"
git clone https://git.busybox.net/busybox/
echo "Done."