#! /bin/bash
echo "Removing previous results"
rm -rf extraction-results
echo "Executing variability extraction of Linux."
java -jar VariabilityExtraction-1.0.0-jar-with-dependencies.jar variability_analysis_Linux.properties v4.5 v4.6