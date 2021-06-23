#! /bin/bash
echo "Starting extraction"
gcc --version
java -version

cd /home/user || exit
ls -l
git clone --progress https://oauth2:XRzSBbQQyRfgEjJhFxr2@gitlab.informatik.hu-berlin.de/mse/VariantSync/VariabilityExtraction.git
cd VariabilityExtraction || exit

echo "Building with Maven"
mvn package || exit
echo ""

echo "Copying resources"
cp target/VariabilityExtraction-*-jar-with* docker-resources/* ..
cd ..
echo ""

if [ "$1" = 'busybox' ]
then
    git clone --progress https://git.busybox.net/busybox/
    echo "Executing variability extraction of BusyBox."
    java -jar VariabilityExtraction-1.0.0-jar-with-dependencies.jar variability_analysis_BusyBox.properties "$2" "$3"
elif [ "$1" = 'linux' ]
then
    echo "Cloning Linux, this will take quite some time."
    git clone --progress https://github.com/torvalds/linux.git
    echo "Executing variability extraction of Linux."
    java -jar VariabilityExtraction-1.0.0-jar-with-dependencies.jar variability_analysis_Linux.properties "$2" "$3"
else
    echo "Select a SPL to extract from [ linux | busybox ]"
fi
