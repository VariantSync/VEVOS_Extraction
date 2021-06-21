#! /bin/bash
echo "Removing results directory for linux"
rm -rf extraction-results/linux
docker run -v "$(pwd)/extraction-results/linux:/variability-extraction/extraction-results" variability-extraction linux