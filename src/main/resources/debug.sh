#!/bin/bash
rm VariabilityExtraction-1.0.0-jar-with-dependencies.jar
rm -rf extraction-results
cd VariabilityExtraction
mvn package
clear
cd ..
mv VariabilityExtraction/target/VariabilityExtraction-1.0.0-jar-with-dependencies.jar .
java -jar VariabilityExtraction-1.0.0-jar-with-dependencies.jar variability_analysis_BusyBox.properties ac216873095a0d7c30737df5cdfa3bf6c261f079 760fc6debcba8cb5ca8d8e2252fac3757c453e11