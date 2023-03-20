# VEVOS: Ground Truth Extraction
VEVOS is a tool suite for the simulation of the evolution of clone-and-own projects and consists of two main components: The ground truth extraction, called VEVOS/Extraction and the variant simulation called VEVOS/Simulation.

This repository contains VEVOS/Extraction. 
Please refer to our paper _Simulating the Evolution of Clone-and-Own Projects with VEVOS_ published at the International Conference on Evaluation and Assessment in Software Engineering (EASE) 2022 ([doi](https://doi.org/10.1145/3530019.3534084)) for more information. 
VEVOS/Extraction is a Java project for extracting feature mappings, presence conditions, and feature models for each revision (within a specified range of the commit-history) from an input software product line.

## Quick Start using Docker
In the following, we provide instructions on how to quickly extract the ground truth of Linux or Busybox with the provided Docker setup.

### Requirements
The only requirement is Docker. We provide batch and bash scripts that execute the necessary Docker setup and execution.
We tested the Docker setup under Windows and Linux. 
We have not tested the setup on Mac, but you should be able to use the instructions for Linux.

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
git clone https://github.com/VariantSync/VEVOS_Extraction.git
``` 
Then, navigate to the repository's root directory in a terminal of your choice.

### Build the Docker Image
Before the extraction can be executed, we have to build the Docker image. This can be done by executing the corresponding build script in a terminal.

- Linux terminal: `./build-docker-image.sh`
- Windows CMD: `build-docker-image.bat`

This process may roughly take half an hour.

### Start the Ground Truth Extraction in a Docker Container
We provide bash and batch scripts that start the ground truth extraction and copy all data to
_Extraction/extraction-results_ once the extraction is complete, or has been stopped.
Start the extraction by executing the `start-extraction` script (see examples further below).
The basic syntax is `start-extraction.(sh|bat)`:

- Windows CMD:
  - `start-extraction.bat` 
- Linux terminal:
  - `./start-extraction.sh`

#### Runtime
TODO

### Stopping the Ground Truth Extraction
You can stop the Docker container in which the ground truth extraction is running at any time. In this case, all
collected data will be copied to _Extraction/extraction-results/_ as if the extraction finished successfully.

- Windows CMD:
  - `stop-extraction.bat`
- Linux terminal:
  - `./stop-extraction.sh`

### Custom Configuration
You can find the properties files used by Docker under Extraction/docker-resources. By changing the properties, you can adjust the ground truth extraction (e.g., change the log level,
number of threads, etc.). For your convenience, we set all properties to default values. __Note that you have to rebuild the Docker image in order for the changes to take effect__.

### Clean-Up 
You can clean up all created images, container, and volumes via `docker system prune -a`. __DISCLAIMER: This will remove ALL docker objects, even the ones not related to ground truth extraction__. If you have other images, containers, or volumes that you do not want to loose, you can run the docker commands that refer to the objects related to the ground truth extraction.
- Image: `docker rmi extraction`
- Container: 
  - `docker container rm extraction`
  - `docker container rm extraction`
- Volume:
  - `docker volume rm extraction`
  - `docker volume rm extraction`

## Custom System Setup
If you want to run the ground truth extraction without Docker, you will have to first set up the environment in which
the extraction is executed.

### Limitations
There are some limitations to the ground truth extraction that should be mentioned.

#### Operating System
Due to the implementation of the Ground Truth Extraction and KernelHaven, it is only possible to run the ground truth
extraction on Linux (and possibly Mac). However, you can use the provided Docker setup, or your own virtual machine or
Windows Subsystem for Linux, in order to run the extraction on any OS.

### Requirements
TODO

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

### Configuration
TODO

### Validation
TODO

### Ground Truth Extraction
TODO