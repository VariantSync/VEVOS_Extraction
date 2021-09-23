#! /bin/bash
if [ "$1" = 'busybox' ]
then
    docker run --rm -v "$(pwd)/extraction-results/busybox":"/home/user/extraction-results/output" variability-extraction "$@"
elif [ "$1" = 'linux' ]
then
    docker run --rm -v "$(pwd)/extraction-results/linux":"/home/user/extraction-results/output" variability-extraction "$@"
else
    echo "Select a SPL to extract [ ./start-extraction.sh linux | ./start-extraction.sh busybox ]"
fi
echo "Done."
