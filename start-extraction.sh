#! /bin/bash
if [ "$1" = 'busybox' ]
then
    rm -rf extraction-results/busybox
    docker run \
    --user "$(id -u):$(id -g)" \
    --name variability-extraction-busybox \
    --mount source=busybox-extraction,target=/home/user/extraction-results/output \
    variability-extraction "$@"

    echo "Copying data from the Docker container to ./extraction-results/busybox"
    mkdir -p extraction-results/busybox
    docker run --rm --volumes-from variability-extraction-busybox \
    -u "$(id -u):$(id -g)" \
    -v "$(pwd)/extraction-results/busybox":"/home/user/data" \
    ubuntu cp -rf /home/user/extraction-results/output /home/user/data || exit

    echo "Removing Docker container and volume"
    docker container rm variability-extraction-busybox
    docker volume rm busybox-extraction
elif [ "$1" = 'linux' ]
then
    rm -rf extraction-results/linux
    docker run \
    --user "$(id -u):$(id -g)" \
    --name variability-extraction-linux \
    --mount source=linux-extraction,target=/home/user/extraction-results/output \
    variability-extraction "$@"

    echo "Copying data from the Docker container to ./extraction-results/linux"
    mkdir -p extraction-results/linux
    docker run --rm --volumes-from variability-extraction-linux \
    -u "$(id -u):$(id -g)" \
    -v "$(pwd)/extraction-results/linux":"/home/user/data" \
    ubuntu cp -rf /home/user/extraction-results/output /home/user/data || exit

    echo "Removing Docker container and volume"
    docker container rm variability-extraction-linux
    docker volume rm linux-extraction
else
    echo "Select a SPL to extract [ ./start-extraction.sh linux | ./start-extraction.sh busybox ]"
fi
echo "Done."