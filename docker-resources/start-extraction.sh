#! /bin/bash
echo "Starting extraction"
gcc --version
java -version
if [ "$1" = 'busybox' ]
then
    git clone https://git.busybox.net/busybox/
    echo "Executing variability extraction of BusyBox."
    java -jar VariabilityExtraction-1.0.0-jar-with-dependencies.jar variability_analysis_BusyBox.properties
elif [ "$1" = 'linux' ]
then
    echo "Cloning Linux, this will take quite some time."
    git clone https://github.com/torvalds/linux.git
    echo "Executing variability extraction of Linux."
    java -jar VariabilityExtraction-1.0.0-jar-with-dependencies.jar variability_analysis_Linux.properties v4.1 v4.2
else
    echo "Select a SPL to extract [ linux | busybox ]"
fi
