# syntax=docker/dockerfile:1
# Prepare the environment
FROM ubuntu:20.04
RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata
RUN apt-get install -y --no-install-recommends build-essential libelf-dev libssl-dev flex bison maven libselinux1-dev git

# Setup working directory
WORKDIR /variability-extraction
RUN pwd
RUN echo "Creating VariabilityExtraction dir"
RUN mkdir VariabilityExtraction
RUN echo "Changing directory into VariabilityExtraction"
WORKDIR /variability-extraction/VariabilityExtraction
RUN echo "Copying sources"
COPY . .
RUN ls .
RUN echo "Building JAR"
RUN mvn package
RUN echo "Copying resources required to run the extraction."
RUN cp target/VariabilityExtraction-*-jar-with* docker-resources/* ..
WORKDIR /variability-extraction
RUN chmod +x start-extraction.sh
RUN ls .

# Install jdk-8 and gcc-4.4
RUN apt-get remove -y openjdk-*
RUN apt-get remove -y gcc
RUN apt-get install -y --no-install-recommends openjdk-8-jdk
RUN cp VariabilityExtraction/docker-resources/ubuntu-repos.txt .
RUN cat ubuntu-repos.txt >> /etc/apt/sources.list
RUN apt-get update \
    && apt-get install -y --no-install-recommends gcc-4.4
RUN ln -s /bin/gcc-4.4 /bin/gcc
RUN echo "Removing no longer required sources"
RUN rm -rf VariabilityExtraction
RUN ls -al
RUN gcc --version
RUN java -version

ENTRYPOINT ["./start-extraction.sh"]