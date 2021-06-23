# syntax=docker/dockerfile:1
FROM ubuntu:20.04

# Create a user
ARG USER_ID
ARG GROUP_ID
RUN addgroup --gid $GROUP_ID user
RUN adduser --disabled-password  --home /home/user --gecos '' --uid $USER_ID --ingroup user user

# Prepare the environment
RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata
RUN apt-get install -y --no-install-recommends build-essential libelf-dev libssl-dev flex bison libselinux1-dev git


# Setup working directory
WORKDIR /resources
COPY ./docker-resources/ubuntu-repos.txt .
# COPY ./docker-resources/maven-setup.sh /etc/profile.d/maven.sh
# RUN chmod +x /etc/profile.d/maven.sh

# Install jdk-8 and gcc-4.4
RUN apt-get remove -y openjdk-*
RUN apt-get remove -y gcc
RUN apt-get install -y --no-install-recommends openjdk-8-jdk
RUN cat ubuntu-repos.txt >> /etc/apt/sources.list
RUN apt-get update \
    && apt-get install -y --no-install-recommends gcc-4.4
RUN ln -s /bin/gcc-4.4 /bin/gcc
RUN rm -rf VariabilityExtraction
RUN ls -al
RUN gcc --version
RUN java -version
RUN apt-get install -y --no-install-recommends maven

COPY ./docker-resources/start-extraction.sh /home/user/
RUN mkdir -p /home/user/extraction-results/output
RUN chown user:user /home/user -R
WORKDIR /home/user
RUN chmod +x start-extraction.sh

ENTRYPOINT ["./start-extraction.sh"]