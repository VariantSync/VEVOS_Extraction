#!/bin/bash
echo "Stopping busybox-extraction"
docker container stop variability-extraction-busybox

echo "Copying data from the Docker container to ./extraction-results/busybox"
mkdir -p extraction-results/busybox
docker run --rm --volumes-from variability-extraction-busybox \
-v "$(pwd)/extraction-results/busybox":"/home/user/data" \
ubuntu cp -rf /home/user/extraction-results /home/user/data || exit

echo "Removing Docker container and volume"
docker container rm variability-extraction-busybox
docker volume rm busybox-extraction

echo "Done."