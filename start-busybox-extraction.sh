#! /bin/bash
rm -rf extraction-results/busybox
docker run \
--user "$(id -u):$(id -g)" \
--name variability-extraction-busybox \
--mount source=busybox-extraction,target=/home/user/extraction-results/output \
variability-extraction busybox

echo "Copying data from the Docker container to ./extraction-results/busybox"
mkdir -p extraction-results/busybox
docker run --rm --volumes-from variability-extraction-busybox \
--name copy-busybox-data \
-u "$(id -u):$(id -g)" \
-v "$(pwd)/extraction-results/busybox":"/home/user/data" \
ubuntu cp -rf /home/user/extraction-results/output /home/user/data || exit

echo "Removing Docker container and volume"
docker wait copy-busybox-data
docker container rm variability-extraction-busybox
docker volume rm busybox-extraction

echo "Done."