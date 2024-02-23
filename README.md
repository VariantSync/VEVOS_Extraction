# VEVOS: Ground Truth Extraction v2.1.0

VEVOS is a tool suite for the simulation of the evolution of clone-and-own projects and consists of two main components:
The ground truth extraction, called VEVOS/Extraction and the variant simulation called VEVOS/Simulation.

This repository contains VEVOS/Extraction.
Please refer to our paper _Simulating the Evolution of Clone-and-Own Projects with VEVOS_ published at the International
Conference on Evaluation and Assessment in Software Engineering (EASE)
2022 ([doi](https://doi.org/10.1145/3530019.3534084)) for more information.
VEVOS/Extraction is a Java project for extracting feature mappings, presence conditions, and feature models for each
commit from an input software product line.

## Version 2.x.x Update

### Improvements

Version 2.0.0 of VEVOS Extraction presents a major improvement over version 1.0.0 in terms of extractable product lines,
commit coverage, and extraction speed.
VEVOS is now based on [DiffDetective](https://github.com/VariantSync/DiffDetective) a library for analyses of edits to
preprocessor-based product lines.
Due to this major change, VEVOS can now extract a ground truth for any C preprocessor-based software product line and is
no longer bound to the availability of a special adaptor.
For this reason, VEVOS in its default configuration extracts a ground truth for the 43 product lines listed in
the [without_linux](docker-resources/without_linux.md) dataset file.

### Shortcomings

However, there is also a drawback of the improved extraction. VEVOS 2.0.0 is not capable of extracting a feature model
or the presence conditions of entire source code files that are defined by additional build files. If these are still
required, VEVOS 1.x.x has to be used.

### Extraction Modes

There are two basic extraction modes: `fast` and `full`.

#### Fast Extraction

The fast ground truth extraction only extracts the ground truths of changed files for each commit. This extraction is
very useful for studies that are only interested in the evolution of a software family.

#### Full Extraction

The full ground truth extraction extracts the ground truth for all code files of all commits in a product line. Due to
the effort of extracting and saving a ground truth for all files of each commit, this extraction may require a very long
time and large amounts of free disk space.

Essentially, the full ground truth extraction first performs a fast ground truth extraction and then incrementally
combines the ground truths of all commits.

## Quick Start using Docker

In the following, we provide instructions on how to quickly extract a ground truth for any preprocessor-based product
line using Docker.

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

### (Optional) Configure the Extraction

You can customize the extraction by changing one of the three integrated configurations for Docker.
Each configuration has two files associated with it: a properties file that configures VEVOS itself, and a dataset file
that specifies the product lines for extraction.

#### Config 1: without_linux

[without_linux](docker-resources/without_linux.properties) is the default configuration for extracting a ground truth
for 43 product lines specified in the corresponding [dataset file](docker-resources/without_linux.md).

#### Config 2: verification

[verification](docker-resources/verification.properties) is the default configuration for extracting a ground truth for
BusyBox [dataset file](docker-resources/verification.md). This configuration can be used for verification purposes.

#### Config 3: custom

[custom](docker-resources/custom.properties) is the default configuration for extracting a ground truth for any other
preprocessor-based product line. The desired product line should be specified
in [dataset file](docker-resources/custom.md).

> Note that configurations are part of the Docker image. Any configuration changes only takes effect after rebuilding
> the image as described below.

### Build the Docker Image

Before the extraction can be executed, we have to build the Docker image. This can be done by executing the
corresponding build script in a terminal.

- Linux terminal: `./build-docker-image.sh`
- Windows CMD: `build-docker-image.bat`

This process may take a couple of minutes.

### Start the Ground Truth Extraction in a Docker Container

We provide bash and batch scripts that start the ground truth extraction. The extracted ground truth is written to
the [ground-truth](ground-truth) directory.
Start the extraction by executing the `start-extraction` script (see examples further below).
The basic syntax is `start-extraction.(sh|bat) [(verification|custom)] (fast|full)`:

#### Example 1: Start a fast ground truth extraction for `without_linux`

- Windows CMD:
    - `start-extraction.bat fast`
- Linux terminal:
    - `./start-extraction.sh fast`

#### Example 2: Start a full ground truth extraction for `without_linux`

- Windows CMD:
    - `start-extraction.bat full`
- Linux terminal:
    - `./start-extraction.sh full`

#### Example 3: Start a fast ground truth extraction for `verification`

- Windows CMD:
    - `start-extraction.bat verification fast`
- Linux terminal:
    - `./start-extraction.sh verification fast`

#### Example 4: Start a full ground truth extraction for `custom`

- Windows CMD:
    - `start-extraction.bat custom full`
- Linux terminal:
    - `./start-extraction.sh custom full`

#### Runtime

It is difficult to estimate the runtime as it largely depends on the product line's history size and complexity of
annotations. To provide some examples:

- A fast ground truth extraction of BusyBox requires 2-15 minutes depending on the machine
- A full ground truth extraction of BusyBox requires 1-3 hours depending on the machine
- A fast ground truth extraction of the 43 product lines specified in [without_linux](docker-resources/without_linux.md)
  requires 1-7 days depending on the machine

### Stopping the Ground Truth Extraction

You can stop the Docker container in which the ground truth extraction is running at any time.

- Windows CMD:
    - `stop-extraction.bat`
- Linux terminal:
    - `./stop-extraction.sh`

### Custom Configuration

You can find the properties files used by Docker under Extraction/docker-resources. By changing the properties, you can
adjust the ground truth extraction (e.g., change the log level,
number of threads, etc.). For your convenience, we set all properties to default values. __Note that you have to rebuild
the Docker image in order for the changes to take effect__.

### Clean-Up

You can clean up all created images, container, and volumes via `docker system prune -a`. __DISCLAIMER: This will remove
ALL docker objects, even the ones not related to ground truth extraction__. If you have other images, containers, or
volumes that you do not want to loose, you can run the docker commands that refer to the objects related to the ground
truth extraction.

- Image: `docker rmi extraction`
- Container:
    - `docker container rm extraction`
    - `docker container rm extraction`
- Volume:
    - `docker volume rm extraction`
    - `docker volume rm extraction`

## Execution without Docker

If you want to run the ground truth extraction without Docker, you will have to first set up the environment in which
the extraction is executed.

### Operating System

Due to the implementation of the Ground Truth Extraction, it is only possible to run the ground truth extraction on
Linux (and possibly Mac). However, you can use the provided Docker setup, or your own virtual machine or Windows
Subsystem for Linux, in order to run the extraction on any OS.

### Setup for Linux

No special setup is required.

### Setup Guide for Windows Subsystem for Linux (WSL) and Ubuntu

It is possible to use WSL to run the extraction on a Windows machine.

#### Installing WSL2 with Ubuntu 20 LTS

- Follow the guide at https://docs.microsoft.com/en-us/windows/wsl/install-win10
- Using WSL2 is strongly recommended, because the extraction under WSL1 will take a lifetime. You can check which WSL
  you
  have installed by following the instructions
  here https://askubuntu.com/questions/1177729/wsl-am-i-running-version-1-or-version-2
- You can list the installed distributions with `wsl --list --verbose`
- Install Ubuntu 20 LTS via the Microsoft store

#### Problems with WSL

If you should encounter problems with the steps bellow, one of the following hints might help you

- Disable compression to avoid problems with starting WSL and/or network connection
  -- Starting ubuntu -> solved by disabling compression of %USERPROFILE%\AppData\Local\Packages\CanonicalGroupLimited...
  directory
  --Network connection -> solved by disabling the compression of the AppData/Local/Temp
- (otherwise some suggestions to solve network connectivity errors which seem to occur quite
  often: https://github.com/microsoft/WSL/issues/5336)
- Colliding paths when cloning linux repo (possibly only a problem when cloning into subdirectories of mnt)
  -> enable case
  sensitivity (https://www.howtogeek.com/354220/how-to-enable-case-sensitive-folders-on-windows-10/) (https://devblogs.microsoft.com/commandline/per-directory-case-sensitivity-and-wsl/)

### Build
You can build a jar file for executing the extraction by calling Maven in the project's root directory:
```shell
mvn package
```
Maven will build the jar file and save it to the [target](target) directory. The jar which you should use for execution is `Extraction-jar-with-dependencies.jar`.

### Execution
The execution requires a configuration given by a properties file. An example of such a properties file can be found under [extraction.properties](src/main/resources/extraction.properties). You may customize these properties.  

After building the jar file, you can execute it using the following syntax:
```shell
# Either choose a 'fast' or a 'full' extraction
java -jar Extraction-jar-with-dependencies.jar PATH_TO_YOUR_PROPERTIES (fast|full)
```
