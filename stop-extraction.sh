#!/bin/bash
if [ "$1" = 'busybox' ]
then
    echo "Stopping busybox extraction"
    docker container stop variability-extraction-busybox
elif [ "$1" = 'linux' ]
then
    echo "Stopping linux extraction"
    docker container stop variability-extraction-linux
else
    echo "Select a SPL to extract [ ./stop-extraction.sh linux | ./stop-extraction.sh busybox ]"
fi