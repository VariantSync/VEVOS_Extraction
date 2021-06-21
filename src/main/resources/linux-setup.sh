#! /bin/bash
echo "Creating directory 'linux-analysis'"
mkdir linux-analysis
echo "Changing into directory linux-analysis"
cd linux-analysis
echo "Cloning VariabilityExtraction repo"
git clone git@gitlab.informatik.hu-berlin.de:mse/VariantSync/VariabilityExtraction.git
echo "Building VariabilityExtraction"
cd VariabilityExtraction
mvn package
echo "Copying resources required to run the extraction."
cp target/VariabilityExtraction-*-jar-with* src/main/resources/KernelHaven.jar src/main/resources/variability_analysis_Linux.properties src/main/resources/linux-run.sh ..
cd ..
echo "Cloning Linux"
git clone https://github.com/torvalds/linux.git
echo "Done."