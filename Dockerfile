# syntax=docker/dockerfile:1
FROM openjdk:19-alpine

# Prepare the environment
RUN apk add maven git

WORKDIR /home/user

COPY local-maven-repo ./local-maven-repo
COPY src ./src
COPY pom.xml .
RUN mvn package || exit


FROM openjdk:19-alpine

ARG GROUP_ID
ARG USER_ID

# Create a user
RUN addgroup -g $GROUP_ID user
RUN adduser --disabled-password -G user -u $USER_ID --home /home/user --gecos '' user

# Prepare the environment
RUN apk update
RUN apk add git bash



ENV GOSU_VERSION 1.16
RUN set -eux; \
    \
    apk add --no-cache --virtual .gosu-deps \
    ca-certificates \
    dpkg \
    gnupg \
    ; \
    \
    dpkgArch="$(dpkg --print-architecture | awk -F- '{ print $NF }')"; \
    wget -O /usr/local/bin/gosu "https://github.com/tianon/gosu/releases/download/$GOSU_VERSION/gosu-$dpkgArch"; \
    wget -O /usr/local/bin/gosu.asc "https://github.com/tianon/gosu/releases/download/$GOSU_VERSION/gosu-$dpkgArch.asc"; \
    \
    # verify the signature
    export GNUPGHOME="$(mktemp -d)"; \
    gpg --batch --keyserver hkps://keys.openpgp.org --recv-keys B42F6819007F00F88E364FD4036A9C25BF357DD4; \
    gpg --batch --verify /usr/local/bin/gosu.asc /usr/local/bin/gosu; \
    command -v gpgconf && gpgconf --kill all || :; \
    rm -rf "$GNUPGHOME" /usr/local/bin/gosu.asc; \
    \
    # clean up fetch dependencies
    apk del --no-network .gosu-deps; \
    \
    chmod +x /usr/local/bin/gosu; \
    # verify that the binary works
    gosu --version; \
    gosu nobody true



WORKDIR /home/user
# Copy JAR from previous stage
COPY --from=0 /home/user/target /home/user/target
RUN cp target/*Extraction-jar-with* .

# Copy required scripts and properties
COPY docker-resources/* /home/user/
RUN mkdir -p /home/user/src/main/resources

RUN mkdir -p /home/user/ground-truth/
# permissions for calculon
RUN chown user:user /home/user -R
RUN chmod +x entrypoint.sh
RUN chmod +x fix-perms.sh
RUN chmod +x extract.sh

ENTRYPOINT ["./entrypoint.sh", "./extract.sh"]
