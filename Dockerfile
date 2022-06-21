# syntax=docker/dockerfile:1
FROM alpine:3.14

# Prepare the environment
RUN apk add maven git

WORKDIR /home/user

COPY local-maven-repo ./local-maven-repo
COPY src ./src
COPY pom.xml .
RUN mvn package || exit


FROM ubuntu:20.04

# Create a user
RUN adduser --disabled-password  --home /home/user --gecos '' user

# Prepare the environment
RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata
RUN apt-get install -y --no-install-recommends build-essential libelf-dev libssl-dev flex bison libselinux1-dev git

# Setup working directory
WORKDIR /resources
COPY ./docker-resources/ubuntu-repos.txt .

# Install jdk-8 and gcc-4.4
RUN apt-get remove -y openjdk-*
RUN apt-get remove -y gcc
RUN apt-get install -y --no-install-recommends openjdk-8-jdk
RUN cat ubuntu-repos.txt >> /etc/apt/sources.list
RUN apt-get update \
    && apt-get install -y --no-install-recommends gcc-4.4
RUN ln -s /bin/gcc-4.4 /bin/gcc
RUN rm -rf Extraction
RUN gcc --version
RUN java -version
RUN apt-get install -y --no-install-recommends bc


WORKDIR /home/user
# Copy JAR from previous stage
COPY --from=0 /home/user/target /home/user/target
RUN cp target/Extraction-*-jar-with* .

# Copy required scripts and properties
COPY docker-resources/extract.sh /home/user/
COPY docker-resources/entrypoint.sh /home/user/
COPY docker-resources/fix-perms.sh /home/user/
COPY docker-resources/KernelHaven.jar /home/user/
COPY docker-resources/extraction_busybox.properties /home/user/
COPY docker-resources/extraction_linux.properties /home/user/
COPY docker-resources/extraction_generic.properties /home/user/

RUN mkdir -p /home/user/extraction-results/output
RUN chown user:user /home/user -R
RUN chmod +x entrypoint.sh
RUN chmod +x fix-perms.sh
RUN chmod +x extract.sh

ENTRYPOINT ["./entrypoint.sh", "./extract.sh"]
USER user