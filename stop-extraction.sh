#! /bin/bash
echo "Stopping all running extractions. This will take a moment..."
docker stop $(docker ps -a -q --filter "ancestor=variability-extraction")
echo "...done."