#! /bin/bash
echo "Removing results directory for BusyBox"
rm -rf extraction-results/busybox
docker run -v "$(pwd)/extraction-results/busybox:/variability-extraction/extraction-results" variability-extraction busybox
