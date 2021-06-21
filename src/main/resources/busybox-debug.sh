#!/bin/bash
rm VariabilityExtraction-1.0.0-jar-with-dependencies.jar
rm -rf extraction-results
cd VariabilityExtraction || exit
mvn package
clear
cd ..
mv VariabilityExtraction/target/VariabilityExtraction-1.0.0-jar-with-dependencies.jar .
java -jar VariabilityExtraction-1.0.0-jar-with-dependencies.jar variability_analysis_BusyBox.properties b35eef5383a4e7a6fb60fcf3833654a0bb2245e0 7de0ab21d939a5a304157f75918d0318a95261a3