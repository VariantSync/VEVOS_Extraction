#! /bin/bash
echo "Starting extraction"
cd /home/user || exit

echo "Files in ground-truth folder:"
ls -l /home/user
ls -l ground-truth

if [ "$1" == 'verification' ]
then
    echo "Executing variability extraction defined in verification.properties."
    PROPS=verification.properties
elif [ "$1" == 'custom' ]
then
    echo "Executing variability extraction defined in verification.properties."
    PROPS=custom.properties
elif [ "$1" == '--help' ]
then
    echo "Examples:"
    echo "# Run verification with small datasets"
    echo "./start-extraction.sh verification fast"
    echo "# Run extraction of all Diff Detective datasets apart from the linux kernel"
    echo "./start-extraction.sh fast"
    echo "# Run the extraction with a custom set of properties which have to be defined in docker-resources/custom.properties, BEFORE building the Docker image."
    echo "./start-extraction.sh custom full"
    exit 0
else
    echo "Executing variability extraction defined in without_linux.properties."
    PROPS=without_linux.properties
fi

if [ "$1" == 'fast' ] || [ "$2" == 'fast' ]
then
    JAR=FastExtraction-jar-with-dependencies.jar
elif [ "$1" == 'full' ] || [ "$2" == 'full' ]
then
    JAR=FullExtraction-jar-with-dependencies.jar
else
    echo "You either have to select the 'fast' or the 'full' extraction. See --help for more information"
    exit 1
fi

java -Xmx64g -jar -Dtinylog.configuration=/home/user/tinylog.properties $JAR $PROPS
#java -jar -Dtinylog.configuration=/home/user/tinylog.properties $JAR $PROPS