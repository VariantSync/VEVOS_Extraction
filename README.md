# SPL Variability Extraction

<p>
This tool offers functionality for extracting the source code variants of a KBuild-based SPL, e.g.,
Linux, Busybox, or Coreboot. The extracted variants are used to simulate a clone-and-own dataset 
accroding to the history of the extracted variants. 
</p>

<p>
The extracted dataset comprises the source code of the different variants, their evolution history in 
form of git branches, matchings of the cloned artifacts, and feature mappings for the artefacts.
</p>

## Limitations
### Linux Versions
Due to the plugins that are used by KernelHaven (i.e., KbuildMiner and KconfigReader), it is not possible to extract 
the variability of linux revisions without prior setup of the operating system on which the extraction is run. 
More specifically, we were not able to get KernelHaven to run for linux versions of v5.0 or above. 
This is due to changes in the build structure that require changes to KbuildMiner and KconfigReader.

### Operating System
Due to the implementation of the VariabilityExtraction and KernelHaven, it is only possible to run the variability 
extraction on Linux (and possibly Mac). However, you can use a virtual machine or Windows Subsystem for Linux.

# System Setup
## Requirements
- build-essential packages or similar (depending on OS)
- git
- libelf-dev
- libssl-dev
- flex
- bison
- maven
- openJDK-8
- gcc v4.7.4 (or older)

## Setup Guide for Windows Subsystem for Linux (WSL) and Ubuntu
It is possible to use WSL to run the extraction on a Windows machine.

### Installing WSL2 with Ubuntu 20 LTS
- Follow the guide at https://docs.microsoft.com/en-us/windows/wsl/install-win10
- Using WSL2 is strongly recommended, because the extraction under WSL1 will take a lifetime.
- Install Ubuntu 20 LTS via the Microsoft store

### Install required packages
Tip: You can enable copy-paste with Ctrl+Shift+C/V in WSL by
`
right-clicking on the top of the linux terminal window > properties > setting the corrensponding property under edit options (look at the center of the window)
`

Install essential build packages:
```sudo apt install build-essential```

Install required libs:
```sudo apt install libelf-dev libssl-dev```

Install flex:
```sudo apt install flex```

Install bison:
```sudo apt install bison```

Remove too recent jdks, sadly we can only use jdk8:
```sudo apt remove openjdk-*```

Install JDK-8:
```sudo apt install openjdk-8-jdk```

Assert that the correct java version is used:
```java -version``` should display jdk-8 (or 1.8)

Install maven:
```sudo apt install maven```

### Installing old gcc
Older linux sources can only be compiled with old gcc versions, due to changes in gcc over time. In order to install an older gcc version follow these steps.

Remove currently installed gcc:
```sudo apt remove gcc```

Make sure it is gone. The following should **not work**:
```gcc --version```

Copy the sources.list to your working directory:
```cp /etc/apt/sources.list .```

Copy the following into your /etc/apt/sources.list. 
Tip: If you have Visual Studio Code installed under Windows, you can open the file with
```code sources.list```:
```
###### Ubuntu Main Repos
deb http://us.archive.ubuntu.com/ubuntu/ trusty main restricted universe multiverse
deb-src http://us.archive.ubuntu.com/ubuntu/ trusty main restricted universe multiverse

###### Ubuntu Update Repos
deb http://us.archive.ubuntu.com/ubuntu/ trusty-security main restricted universe multiverse
deb http://us.archive.ubuntu.com/ubuntu/ trusty-updates main restricted universe multiverse
deb http://us.archive.ubuntu.com/ubuntu/ trusty-proposed main restricted universe multiverse
deb http://us.archive.ubuntu.com/ubuntu/ trusty-backports main restricted universe multiverse
deb-src http://us.archive.ubuntu.com/ubuntu/ trusty-security main restricted universe multiverse
deb-src http://us.archive.ubuntu.com/ubuntu/ trusty-updates main restricted universe multiverse
deb-src http://us.archive.ubuntu.com/ubuntu/ trusty-proposed main restricted universe multiverse
deb-src http://us.archive.ubuntu.com/ubuntu/ trusty-backports main restricted universe multiverse
```

Move the changed sources.list file back into /etc/:
```sudo mv ./sources.list /etc/apt/```

Update the package list of apt:
```sudo apt update```

Install an old gcc version:
```sudo apt install gcc-4.4```

Because the linux makefiles are using gcc without specifying a version, we have to set a link to gcc-4:
```sudo ln -s /bin/gcc-4.4 /bin/gcc```

Assert the correct gcc is used:
```gcc --version```

## Prepare Working Directory
We recommend creating a working directory in which all data related to the extraction is managed. In the following, we refer to the working directory as *WORKDIR*.

Navigate to your *WORKDIR*:
```cd WORKDIR```

Clone the VariabilityExtraction repository:
```
git clone git@gitlab.informatik.hu-berlin.de:mse/VariantSync/VariabilityExtraction.git
```

Navigate into the repo:
```cd VariabilityExtraction```

Build the jar:
```mvn package```

Copy the required files to the work dir:
```
IF the VariabilityExtractionRepo is part of your work dir run:

cp target/VariabilityExtraction-*-jar-with* src/main/resources/KernelHaven.jar src/main/resources/variability_analysis_Linux.properties ..

ELSE

cp target/VariabilityExtraction-*-jar-with* src/main/resources/KernelHaven.jar src/main/resources/variability_analysis_Linux.properties WORKDIR/
```

Navigate back to the WORKDIR:
```cd WORKDIR```

## Configuration
Open the properties file `variability_analysis_Linux.properties` in an editor of your choice. 

Adjust the path to the linux sources (absolute path): ```source_tree = WORKDIR/linux```

Example: ```source_tree = /home/alice/linux-analysis/linux```

Save the file.

## Validation
The easiest way to check whether (almost) everything is set up correctly and whether it is possible to extract the variability for a specific Linux commit, is to run `make allyesconfig prepare` in the linux sources directory.

Navigate to the linux sources
```cd linux```

Checkout the desired commit or revision, e.g.,
```git checkout v4.2```

Run make:
```make allyesconfig prepare```

If no errors are thrown, the VariabilityExtraction *should* be successful for this commit. If you are able to complete the preparation for at least one commit, your system should be set up correctly.

Navigate back to working directory
```cd ..```

## Variability Extraction
You can run the variability extraction for a range of commits by specifying commit ids:
```
java -jar VariabilityExtraction-1.0.0-jar-with-dependencies.jar variability_analysis_Linux.properties *START_COMMIT_ID* *END_COMMIT_ID*   
```
or by specifying revision tags:
```
java -jar VariabilityExtraction-1.0.0-jar-with-dependencies.jar variability_analysis_Linux.properties *START_REVISION_TAG* *END_REVISION_TAG*   
```
For example, to extract the variability for all commits between Linux v4.5 and Linux v4.6, the following is possible:
```
java -jar VariabilityExtraction-1.0.0-jar-with-dependencies.jar variability_analysis_Linux.properties b562e44f507e863c6792946e4e1b1449fbbac85d 2dcd0af568b0cf583645c8a317dd12e344b1c72a

java -jar VariabilityExtraction-1.0.0-jar-with-dependencies.jar variability_analysis_Linux.properties v4.5 v5.6
```

By default, the files with the extracted variability are stored under `WORKDIR/evolution-analysis/output/`