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

# Create a user
RUN adduser --disabled-password  --home /home/user --gecos '' user

# Prepare the environment
RUN apk add git

WORKDIR /home/user
# Copy JAR from previous stage
COPY --from=0 /home/user/target /home/user/target
RUN cp target/Extraction-jar-with* .

# Copy required scripts and properties
COPY docker-resources/* /home/user/
RUN mkdir -p /home/user/src/main/resources
RUN mv tinylog.properties src/main/resources/

RUN mkdir -p /home/user/ground-truth/
RUN chown user:user /home/user -R
RUN chmod +x entrypoint.sh
RUN chmod +x fix-perms.sh
RUN chmod +x extract.sh

ENTRYPOINT ["./entrypoint.sh", "./extract.sh"]
USER user