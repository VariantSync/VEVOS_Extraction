# VEVOS: Ground Truth Extraction

<p>
This project offers functionality for extracting feature models, presence conditions, and other metadata for a kbuild-based software product line. The setup was configured and tested for BusyBox and Linux.
</p>

## Quick Start using Docker
In the following, we provide instructions on how to quickly extract the ground truth of Linux or Busybox with the provided 
Docker setup.

### Requirements
The only requirement is Docker. We provide batch and bash scripts that execute the necessary Docker setup and execution.
We tested the Docker setup under Windows and Linux. 
We have not tested the setup on Mac, but you should be able to use follow the instructions for Linux.

### Preparation
#### Docker
Docker must be installed on your system, and the Docker daemon must be running.
For installation, follow the instructions given in the installation guide for your OS which you can find 
[here](https://docs.docker.com/get-docker/).
Under Linux, you should follow the optional 
[post-installation instructions](https://docs.docker.com/engine/install/linux-postinstall/).

#### Repository
Clone the repository to a location of your choice 
```
git clone https://github.com/VariantSync/Extraction.git
``` 
Then, navigate to the repository's root directory in a terminal of your choice. 

### Build the Docker Image
Before the extraction can be executed, we have to build the Docker image. This can be done by executing the correspsonding build script in a terminal.

- Linux terminal: `./build-docker-image.sh`
- Windows CMD: `build-docker-image.bat`

### Start the Ground Truth Extraction in a Docker Container
We provide bash and batch scripts that start the ground truth extraction and copy all data to 
_Extraction/extraction-results_ once the extraction is complete, or has been stopped.
Start the extraction by executing the `start-extraction` script (see examples further below). 
The basic syntax is the following:

- `start-extraction.(sh|bat) (linux|busybox) [commit-id/tag] [commit-id/tag]`
- `(option-1|option-2)` -> You *must* provide one of the two.
- `[option]` -> You *may* provide a value

The script must be provided with `busybox` or `linux` as first argument, in order to specify which SPL should be considered.
In addition, you can optionally provide either one or two more arguments specifying a commit-id or git-tag. 

If you specify __no__ id or tag, the entire history is considered.

If you specify __exactly one__ id or tag, the extraction will only consider the one commit that is found under the id/tag. 
This can be used to quickly test whether everything is working as intended. 

If you specify __two__ ids or tags, the extraction will consider the range of commits that lies between the first and the second
commit. The commit retrieval follows the same logic as [git log](https://git-scm.com/docs/git-log), i.e., it will retrieve
all commits that are ancestors of the second commit, but __not__ ancestors of the first commit.

- Windows CMD: 
  - `start-extraction.bat busybox [id/tag] [id/tag]` 
  - `start-extraction.bat linux [id/tag] [id/tag]`
- Linux terminal: 
  - `./start-extraction.sh busybox [id/tag] [id/tag]`
  - `./start-extraction.sh linux [id/tag] [id/tag]`

#### Runtime
The entire history of BusyBox can be extracted in about one day.

For Linux, even considering only the commits between two minor revisions, e.g. v4.1 and v4.2, can take several days.

#### Errors in Log
If the entire history of BusyBox or Linux is considered, large numbers of non-extractable commits are to be expected. 
Therefore, errors that appear in the log do not indicate a problem with the setup, but only indicate that a commit could not be processed. 

#### Examples:
```
Extract the ground truth for all commits of BusyBox
start-extraction.bat busybox
./start-extraction.sh busybox

Extract the ground truth between two specific commits of Busybox
start-extraction.bat busybox b35eef5383a4e7a6fb60fcf3833654a0bb2245e0 7de0ab21d939a5a304157f75918d0318a95261a3
./start-extraction.sh busybox b35eef5383a4e7a6fb60fcf3833654a0bb2245e0 7de0ab21d939a5a304157f75918d0318a95261a3

Extract the ground truth for the commit under revision tag v4.1 of Linux
start-extraction.bat linux v4.1
./start-extraction.sh linux v4.1

Extract the ground truth for all commits between two minor revisions of Linux
start-extraction.bat linux v4.3 v4.4
./start-extraction.sh linux v4.3 v4.4
```

### Stopping the Ground Truth Extraction
You can stop the Docker container in which the ground truth extraction is running at any time. In this case, all 
collected data will be copied to _Extraction/extraction-results/_ as if the extraction finished successfully. 

- Windows CMD: 
  - `stop-extraction.bat busybox`
  - `stop-extraction.bat linux`
- Linux terminal: 
  - `./stop-extraction.sh busybox`
  - `./stop-extraction.sh linux`

### Custom Configuration
You can find the properties files used by Docker under Extraction/docker-resources. By changing the properties 
for BusyBox or Linux respectively, you can adjust the ground truth extraction (e.g., change the log level, 
number of threads, etc.). For your convenience, we set all properties to default values. __Note that you have to rebuild the Docker image in order for the changes to take effect__.

This can also be used in case you want to extract the ground truth for any other SPL besides Linux and BusyBox. However,
please note that this requires the correct configuration of KernelHaven and possibly other preprocessing steps, that
are not included in this project. In addition, the extractors that are used internally, i.e., KbuildMiner and KconfigReader
might not be applicable to the chosen SPL. In this case, custom extractors have to be implemented and added to the dependencies 
in the _pom.xml_ file.

### Clean-Up 
You clean up all created images, container, and volumes via `docker system prune -a`. __DISCLAIMER: This will remove ALL docker objects, even the ones not related to ground truth extraction__. If you have other images, containers, or volumes that you do not want to loose, you can run the docker commands that refer to the objects related to the ground truth extraction.
- Image: `docker rmi extraction`
- Container: 
  - `docker container rm extraction-busybox`
  - `docker container rm extraction-linux`
- Volume:
  - `docker volume rm extraction-busybox`
  - `docker volume rm extraction-linux`

## Custom System Setup
If you want to run the ground truth extraction without Docker, you will have to first set up the environment in which
the extraction is executed. 

### Limitations
There are some limitations to the ground truth extraction that should be mentioned.
#### SPL Versions
Due to the plugins that are used by KernelHaven (i.e., KbuildMiner and KconfigReader), it is not possible to extract
the ground truth of SPL revisions without prior setup of the operating system on which the extraction is run.
More specifically, we were not able to get KernelHaven to run for linux versions of v5.0 or above. Older versions of linux (below v4), can also cause problems.
This is due to changes in the build structure that require changes to KbuildMiner and KconfigReader. Similar problems probably also exist for Busybox, Coreboot, etc.
`For this reason we offer scripts that setup a Docker container for extracting the data for BusyBox or Linux.`

#### Operating System
Due to the implementation of the Ground Truth Extraction and KernelHaven, it is only possible to run the ground truth
extraction on Linux (and possibly Mac). However, you can use the provided Docker setup, or your own virtual machine or
Windows Subsystem for Linux, in order to run the extraction on any OS.

### Requirements
- build-essential packages or similar (depending on OS)
- git
- libelf-dev
- libssl-dev
- libselinux (For Busybox only)
- flex
- bison
- maven
- openJDK-8
- gcc v4.7.4 (or older) (Not required for Busybox)

### Setup Guide for Windows Subsystem for Linux (WSL) and Ubuntu
It is possible to use WSL to run the extraction on a Windows machine.

#### Installing WSL2 with Ubuntu 20 LTS
- Follow the guide at https://docs.microsoft.com/en-us/windows/wsl/install-win10
- Using WSL2 is strongly recommended, because the extraction under WSL1 will take a lifetime. You can check which WSL you 
  have installed by following the instructions here https://askubuntu.com/questions/1177729/wsl-am-i-running-version-1-or-version-2
- You can list the installed distributions with `wsl --list --verbose`
- Install Ubuntu 20 LTS via the Microsoft store

#### Problems with WSL
If you should encounter problems with the steps bellow, one of the following hints might help you
- Disable compression to avoid problems with starting WSL and/or network connection
  -- Starting ubuntu -> solved by disabling compression of %USERPROFILE%\AppData\Local\Packages\CanonicalGroupLimited... directory
  --Network connection -> solved by disabling the compression of the AppData/Local/Temp
- (otherwise some suggestions to solve network connectivity errors which seem to occur quite often: https://github.com/microsoft/WSL/issues/5336)
- Colliding paths when cloning linux repo (possibly only a problem when cloning into subdirectories of mnt) 
  -> enable case sensitivity (https://www.howtogeek.com/354220/how-to-enable-case-sensitive-folders-on-windows-10/) (https://devblogs.microsoft.com/commandline/per-directory-case-sensitivity-and-wsl/)

#### Install required packages
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

Install libselinux (For Busybox only)
```sudo apt install libselinux1-dev```

#### Installing old gcc
Older linux sources can only be compiled with old gcc versions, due to changes in gcc over time. In order to install an older gcc version follow these steps.

Remove currently installed gcc:
```sudo apt remove gcc```

Make sure it is gone. The following should **not work**:
```gcc --version```

Copy the sources.list to your working directory:
```cp /etc/apt/sources.list .```

Copy the following into the copy of sources.list. 
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

Move the changed sources.list file back into /etc/apt/:
```sudo mv ./sources.list /etc/apt/```

Update the package list of apt:
```sudo apt update```

Install an old gcc version:
```sudo apt install gcc-4.4```

Because the linux makefiles are using gcc without specifying a version, we have to set a link to gcc-4:
```sudo ln -s /bin/gcc-4.4 /bin/gcc```

Assert the correct gcc is used:
```gcc --version```

### Prepare Working Directory
We recommend creating a working directory in which all data related to the extraction is managed. In the following, we refer to the working directory as *WORKDIR*.

Navigate to your *WORKDIR*:
```cd WORKDIR```

Clone the repository of the SPL that you want to analyze (e.g., Linux):
```
git clone https://github.com/torvalds/linux.git
```

Clone the Extraction repository:
```
git clone git@gitlab.informatik.hu-berlin.de:mse/VariantSync/Extraction.git
```

Navigate into the repo:
```cd Extraction```

Build the jar:
```mvn package```

Copy the required files to the work dir:
```
IF the ExtractionRepo is part of your work dir run:

cp target/Extraction-*-jar-with* src/main/resources/KernelHaven.jar src/main/resources/extraction_linux.properties ..

ELSE

cp target/Extraction-*-jar-with* src/main/resources/KernelHaven.jar src/main/resources/extraction_linux.properties WORKDIR/
```

Navigate back to the WORKDIR:
```cd WORKDIR```

### Configuration
Open the properties file `extraction_linux.properties` in an editor of your choice. 

Adjust the path to the linux sources (absolute path): ```source_tree = WORKDIR/linux```

Example: ```source_tree = /home/alice/linux-analysis/linux```

Save the file.

### Validation
The easiest way to check whether (almost) everything is set up correctly and whether it is possible to extract the ground truth for a specific Linux commit, is to run `make allyesconfig prepare` in the linux sources directory.

Navigate to the linux sources
```cd linux```

Checkout the desired commit or revision, e.g.,
```git checkout v4.2```

Run make:
```make allyesconfig prepare```

If no errors are thrown, the extraction *should* be successful for this commit. If you are able to complete the preparation for at least one commit, your system should be set up correctly.

Navigate back to working directory
```cd ..```

### Ground Truth Extraction
You can run the ground truth extraction for a range of commits by specifying commit ids:
```
java -jar Extraction-1.0.0-jar-with-dependencies.jar extraction_linux.properties *START_COMMIT_ID* *END_COMMIT_ID*   
```
or by specifying revision tags:
```
java -jar Extraction-1.0.0-jar-with-dependencies.jar extraction_linux.properties *START_REVISION_TAG* *END_REVISION_TAG*   
```
For example, to extract the ground truth for all commits between Linux v4.5 and Linux v4.6, the following is possible:
```
java -jar Extraction-1.0.0-jar-with-dependencies.jar extraction_linux.properties b562e44f507e863c6792946e4e1b1449fbbac85d 2dcd0af568b0cf583645c8a317dd12e344b1c72a

java -jar Extraction-1.0.0-jar-with-dependencies.jar extraction_linux.properties v4.5 v5.6
```

By default, the files with the extracted ground truth are stored under `WORKDIR/evolution-analysis/output/`
