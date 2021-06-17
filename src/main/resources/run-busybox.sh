#!/bin/bash
rm VariabilityExtraction-1.0.0-jar-with-dependencies.jar
rm -rf extraction-results
cd VariabilityExtraction || exit
mvn package
clear
cd ..
mv VariabilityExtraction/target/VariabilityExtraction-1.0.0-jar-with-dependencies.jar .
java -jar VariabilityExtraction-1.0.0-jar-with-dependencies.jar variability_analysis_BusyBox.properties