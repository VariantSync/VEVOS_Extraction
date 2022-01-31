#! /bin/bash
#! /bin/bash
# This script was written for development purposes. Its purpose is to set up the working directory for the ground truth extraction.
# WORKDIR
# | -- Extraction
# | -- linux
# | -- linux-debug.sh
echo "Creating directory 'linux-analysis'"
mkdir linux-analysis
echo "Changing into directory linux-analysis"
cd linux-analysis
echo "Cloning Extraction repo"
git clone git@gitlab.informatik.hu-berlin.de:mse/VariantSync/Extraction.git
echo "Building Extraction"
cd Extraction
mvn package
echo "Copying resources required to run the extraction."
cp target/Extraction-*-jar-with* src/main/resources/KernelHaven.jar src/main/resources/extraction_linux.properties src/main/resources/linux-run.sh ..
cd ..
echo "Cloning Linux"
git clone https://github.com/torvalds/linux.git
echo "Done."