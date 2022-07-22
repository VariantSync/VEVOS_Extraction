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
        java -jar Extraction-jar-with-dependencies.jar extraction_busybox.properties "$1"
    elif [ $# == 2 ]
    then
        java -jar Extraction-jar-with-dependencies.jar extraction_busybox.properties "$1" "$2"
    elif [ $# == 3 ]
    then
        java -jar Extraction-jar-with-dependencies.jar extraction_busybox.properties "$1" "$2" "$3"
    fi
elif [ "$1" == 'linux' ]
then
    echo "Executing variability extraction of Linux."
    if [ $# == 1 ]
    then
        java -jar Extraction-jar-with-dependencies.jar extraction_linux.properties "$1"
    elif [ $# == 2 ]
    then
        java -jar Extraction-jar-with-dependencies.jar extraction_linux.properties "$1" "$2"
    elif [ $# == 3 ]
    then
        java -jar Extraction-jar-with-dependencies.jar extraction_linux.properties "$1" "$2" "$3"
    fi
elif [ "$1" == '--help' ]
then
    echo "You can start the extraction by providing the clone link to a git repository and optionally a commit or range of commits."
    echo "You can also specify 'linux'|'busybox' for a detailed extraction of additional build system information
    (i.e., file conditions and feature model) for Linux|BusyBox."
    echo "Examples:"
    echo "# Process linux versions v4.2 - v4.3"
    echo "./start-extraction.sh linux v4.2 v4.3"
    echo "# Process only linux version v4.7"
    echo "./start-extraction.sh linux v4.7"
    echo "# Process the entire history of busybox"
    echo "./start-extraction.sh busybox"
    echo "# Process the entire history of any repo (without build information)"
    echo "./start-extraction.sh https://github.com/OTHER_REPO.git"
    echo "# Process a specific commit of any repo (without build information)"
    echo "./start-extraction.sh https://github.com/OTHER_REPO.git COMMIT_ID"
else
    echo "Executing variability extraction of $1."
    if [ $# == 1 ]
    then
      java -jar Extraction-jar-with-dependencies.jar extraction_generic.properties "$1"
    elif [ $# == 2 ]
    then
      java -jar Extraction-jar-with-dependencies.jar extraction_generic.properties "$1" "$2"
    elif [ $# == 3 ]
    then
      java -jar Extraction-jar-with-dependencies.jar extraction_generic.properties "$1" "$2" "$3"
    fi
fi