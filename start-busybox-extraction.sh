#! /bin/bash
docker run \
--user "$(id -u)" \
--name variability-extraction-busybox \
--mount source=busybox-extraction,target=/home/user/extraction-results/output \
variability-extraction busybox

echo "Stopping busybox-extraction"
docker container stop variability-extraction-busybox

echo "Copying data from the Docker container to ./extraction-results/busybox"
mkdir -p extraction-results/busybox
docker run --rm --volumes-from variability-extraction-busybox --user "$(id -u)" -v "$(pwd)/extraction-results/busybox":/home/$(id -u)/data ubuntu cp -rf /home/user/extraction-results ~/data || exit

echo "Removing Docker container and volume"
docker container rm variability-extraction-busybox
docker volume rm busybox-extraction

echo "Done."