#!/bin/bash
rm VariabilityExtraction-1.0.0-jar-with-dependencies.jar
rm -rf extraction-results
cd VariabilityExtraction
mvn package
clear
cd ..
mv VariabilityExtraction/target/VariabilityExtraction-1.0.0-jar-with-dependencies.jar .
java -jar VariabilityExtraction-1.0.0-jar-with-dependencies.jar variability_analysis_BusyBox.properties e039e689e3c4baefb4b62703429aaf86eef1bd99 7de0ab21d939a5a304157f75918d0318a95261a3