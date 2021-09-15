#! /bin/bash
echo "Starting extraction"
gcc --version
java -version

cd /home/user || exit

echo "Files in extraction-results"
ls -l extraction-results

if [ "$1" == 'busybox' ]
then
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