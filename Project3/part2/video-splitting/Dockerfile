# Use Ubuntu 20.04 as the base image
FROM ubuntu:20.04

# Set environment variables
ENV LANG=C.UTF-8 \
    JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# Install Java, ffmpeg, and other dependencies
RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
    openjdk-17-jdk \
    ffmpeg \
    wget \
    unzip && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Install AWS Lambda Java Runtime Interface Client
RUN mkdir -p /opt
RUN wget -O /opt/aws-lambda-java-runtime-interface-client.jar \
    https://repo1.maven.org/maven2/com/amazonaws/aws-lambda-java-runtime-interface-client/2.6.0/aws-lambda-java-runtime-interface-client-2.6.0.jar

# Set the working directory
WORKDIR /var/task

# Copy the fat JAR into the container
COPY target/video-splitting-1.0-SNAPSHOT.jar /var/task/

# Set the entry point and command for Lambda
ENTRYPOINT [ "java", \
    "-cp", "/var/task/*:/opt/*", \
    "com.amazonaws.services.lambda.runtime.api.client.AWSLambda" ]

CMD [ "com.cse546.project32.Handler::handleRequest" ]