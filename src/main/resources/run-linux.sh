#! /bin/bash
clear
echo "Removing old folders"
rm -rf extraction-results

echo "Packaging jar"
rm -rf VariabilityExtraction-1.0.0-jar-with-dependencies.jar
cd VariabilityExtraction || exit
mvn package
cp target/VariabilityExtraction-1.0.0-jar-with-dependencies.jar ..
cd ..
clear

echo "Executing variability extraction."
java -jar VariabilityExtraction-1.0.0-jar-with-dependencies.jar variability_analysis_Linux.properties e24b6c03a17b20fb6473b3679f7423fae5731d05 9544f8b6e2ee9ed02d2322ff018837b185f51d45