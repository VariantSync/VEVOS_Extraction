#! /bin/bash
docker run -v "$(pwd)/extraction-results/busybox:/variability-extraction/extraction-results" variability-extraction busybox
