#!/bin/bash
echo "Removing previous results"
rm -rf extraction-results
echo "Executing variability extraction of BusyBox."
java -jar VariabilityExtraction-1.0.0-jar-with-dependencies.jar variability_analysis_BusyBox.properties