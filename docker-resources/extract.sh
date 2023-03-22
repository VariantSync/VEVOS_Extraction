#! /bin/bash
echo "Starting extraction"
cd /home/user || exit

echo "Files in ground-truth folder:"
ls -l /home/user
ls -l ground-truth

if [ "$1" == 'verification' ]
then
    echo "Executing variability extraction defined in verification.properties."
    java -jar -Dtinylog.configuration=/home/user/tinylog.properties Extraction-jar-with-dependencies.jar verification.properties
elif [ "$1" == 'custom' ]
then
    echo "Executing variability extraction defined in custom.properties."
    java -jar -Dtinylog.configuration=/home/user/tinylog.properties Extraction-jar-with-dependencies.jar custom.properties
elif [ "$1" == '--help' ]
then
    echo "Examples:"
    echo "# Run verification with small datasets"
    echo "./start-extraction.sh verification"
    echo "# Run extraction of all Diff Detective datasets apart from the linux kernel"
    echo "./start-extraction.sh"
    echo "# Run the extraction with a custom set of properties which have to be defined in docker-resources/custom.properties, BEFORE building the Docker image."
    echo "./start-extraction.sh custom"
else
    echo "Executing variability extraction defined in without_linux.properties."
    java -jar Extraction-jar-with-dependencies.jar without_linux.properties
fi