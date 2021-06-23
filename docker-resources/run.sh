#! /bin/bash
echo "Starting extraction"
gcc --version
java -version

cd /home/user || exit
ls -l
git clone --progress https://oauth2:XRzSBbQQyRfgEjJhFxr2@gitlab.informatik.hu-berlin.de/mse/VariantSync/VariabilityExtraction.git
cd VariabilityExtraction || exit
git checkout 44-docker-setup
echo "Listing files in VariabilityExtraction"
ls
echo ""

echo "Building with Maven"
mvn package || exit
echo ""

echo "Copying resources"
cp target/VariabilityExtraction-*-jar-with* docker-resources/* ..
cd ..
echo ""

echo "Files in WORKDIR"
ls .
echo ""

echo "Files in extraction-results"
ls -l extraction-results

if [ "$1" == 'busybox' ]
then
    git clone --progress https://git.busybox.net/busybox/
    echo "Executing variability extraction of BusyBox."
    if [ $# == 1 ]
    then
        java -jar VariabilityExtraction-1.0.0-jar-with-dependencies.jar variability_analysis_BusyBox.properties
    elif [ $# == 2 ]
    then
        java -jar VariabilityExtraction-1.0.0-jar-with-dependencies.jar variability_analysis_BusyBox.properties "$2"
    elif [ $# == 3 ]
    then
        java -jar VariabilityExtraction-1.0.0-jar-with-dependencies.jar variability_analysis_BusyBox.properties "$2" "$3"
    fi
elif [ "$1" == 'linux' ]
then
    echo "Cloning Linux, this will take quite some time."
    git clone --progress https://github.com/torvalds/linux.git
    echo "Executing variability extraction of Linux."
    if [ $# == 1 ]
    then
        java -jar VariabilityExtraction-1.0.0-jar-with-dependencies.jar variability_analysis_Linux.properties
    elif [ $# == 2 ]
    then
        java -jar VariabilityExtraction-1.0.0-jar-with-dependencies.jar variability_analysis_Linux.properties "$2"
    elif [ $# == 3 ]
    then
        java -jar VariabilityExtraction-1.0.0-jar-with-dependencies.jar variability_analysis_Linux.properties "$2" "$3"
    fi
else
    echo "Select a SPL to extract from [ linux | busybox ]"
fi
