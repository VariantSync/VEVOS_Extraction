#! /bin/bash
if [ "$1" = 'busybox' ]
then
    mkdir -p extraction-results/busybox
    docker run --rm -v "%cd%/extraction-results/busybox":"/home/user/extraction-results/output" variability-extraction "$@"
elif [ "$1" = 'linux' ]
then
    mkdir -p extraction-results/linux
    docker run --rm -v "%cd%/extraction-results/linux":"/home/user/extraction-results/output" variability-extraction "$@"
else
    echo "Select a SPL to extract [ ./start-extraction.sh linux | ./start-extraction.sh busybox ]"
fi
echo "Done."