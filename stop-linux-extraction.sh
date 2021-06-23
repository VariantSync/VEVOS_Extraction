#!/bin/bash
echo "Stopping linux-extraction"
docker container stop variability-extraction-linux

echo "Copying data from the Docker container to ./extraction-results/linux"
mkdir -p extraction-results/linux
docker run --rm --volumes-from variability-extraction-linux --user "$(id -u)" -v "$(pwd)/extraction-results/linux":/home/$(id -u)/data ubuntu cp -rf /home/user/extraction-results ~/data || exit

echo "Removing Docker container and volume"
docker container rm variability-extraction-linux
docker volume rm linux-extraction

echo "Done."