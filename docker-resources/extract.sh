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
        java -jar Extraction-1.0.0-jar-with-dependencies.jar extraction_busybox.properties
    elif [ $# == 2 ]
    then
        java -jar Extraction-1.0.0-jar-with-dependencies.jar extraction_busybox.properties "$2"
    elif [ $# == 3 ]
    then
        java -jar Extraction-1.0.0-jar-with-dependencies.jar extraction_busybox.properties "$2" "$3"
    fi
elif [ "$1" == 'linux' ]
then
    echo "Executing variability extraction of Linux."
    if [ $# == 1 ]
    then
        java -jar Extraction-1.0.0-jar-with-dependencies.jar extraction_linux.properties
    elif [ $# == 2 ]
    then
        java -jar Extraction-1.0.0-jar-with-dependencies.jar extraction_linux.properties "$2"
    elif [ $# == 3 ]
    then
        java -jar Extraction-1.0.0-jar-with-dependencies.jar extraction_linux.properties "$2" "$3"
    fi
else
    echo "Select a SPL to extract from [ linux | busybox ]"
fi