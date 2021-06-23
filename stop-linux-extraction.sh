#!/bin/bash
echo "Stopping linux-extraction"
docker container stop variability-extraction-linux

echo "Copying data from the Docker container to ./extraction-results/linux"
docker run --rm --volumes-from variability-extraction-linux -v "$(pwd)/extraction-results/linux":/data ubuntu cp -rf /variability-extraction /data/

echo "Removing Docker container and volume"
docker container rm variability-extraction-linux
docker volume rm linux-extraction

echo "Done."